package com.aethershield.vpn

import com.aethershield.core.ConnectionState
import com.aethershield.core.EngineEndpoint
import com.aethershield.core.EngineStatus
import com.aethershield.core.ExitFinder
import com.aethershield.core.ExitInfo
import com.aethershield.core.SocksExitFinder
import com.aethershield.core.SocksProbe
import com.aethershield.core.SmartPlan
import com.aethershield.core.TrafficMeter
import com.aethershield.core.TrafficSnapshot
import com.aethershield.core.TunnelEngine
import com.aethershield.core.TunnelHost
import com.aethershield.core.TunnelMode
import com.aethershield.core.VpnSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The ONE authoritative connection lifecycle. Service, notification and UI all use this.
 *
 * "Connected" is published only after engine.start() AND the TUN are both up, and it is
 * withdrawn as soon as the core dies or stops answering. A dropped tunnel is retried with
 * backoff and reported as Reconnecting instead of leaving a stale "Connected" on screen.
 *
 * The same monitor loop that watches the tunnel also samples the forwarder's byte counters
 * ([traffic]) and, in a side coroutine, asks the exit what address it presents ([exitInfo]).
 *
 * Timings, the liveness probe and the exit lookup are injectable so the state machine can be
 * unit tested without a network or minutes of real time.
 */
class ConnectionManager(
    private val engine: TunnelEngine,
    private val timing: Timing = Timing(),
    private val probe: (EngineEndpoint) -> Boolean = DEFAULT_PROBE,
    private val exitFinder: ExitFinder = SocksExitFinder,
) {
    data class Timing(
        /** How often the monitor wakes up; also the traffic sampling period. */
        val tickMs: Long = 500,
        /** How often the free "is the process still there" check runs. */
        val processCheckMs: Long = 2_000,
        /** How often a real CONNECT is pushed through the tunnel. Kept infrequent on purpose. */
        val probeIntervalMs: Long = 30_000,
        /** Consecutive failed probes before the tunnel counts as dead (~1 minute). */
        val probeMisses: Int = 2,
        /** Session age that resets the retry budget. */
        val healthySessionMs: Long = 60_000,
        val maxAttempts: Int = 5,
        val baseBackoffMs: Long = 1_000,
        val maxBackoffMs: Long = 15_000,
        /** How often a known exit is re-checked, so a changed exit shows up on its own. */
        val exitRefreshMs: Long = 60_000,
        /** How often to retry when the exit could not be learned yet. */
        val exitRetryMs: Long = 15_000,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val meter = TrafficMeter()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _traffic = MutableStateFlow(TrafficSnapshot())
    val traffic: StateFlow<TrafficSnapshot> = _traffic.asStateFlow()

    private val _exit = MutableStateFlow<ExitInfo?>(null)
    val exitInfo: StateFlow<ExitInfo?> = _exit.asStateFlow()

    /** The SOCKS5 port of the running tunnel; null when there is nothing to share. */
    private val _endpoint = MutableStateFlow<EngineEndpoint?>(null)
    val activeEndpoint: StateFlow<EngineEndpoint?> = _endpoint.asStateFlow()

    /** The protocol actually in use, which only differs from the setting under "Smart". */
    private val _activeMode = MutableStateFlow<TunnelMode?>(null)
    val activeMode: StateFlow<TunnelMode?> = _activeMode.asStateFlow()

    @Volatile
    private var session: Job? = null

    fun engineStatus(): EngineStatus = engine.status()

    /** The returned Job completes when the session has ended (Connected kept, or Error). */
    fun connect(settings: VpnSettings, host: TunnelHost): Job = scope.launch {
        val accepted = lock.withLock {
            val current = _state.value
            if (current is ConnectionState.Disconnected || current is ConnectionState.Error) {
                // Claimed under the same lock disconnect() takes, so it can never miss this job.
                session = currentCoroutineContext()[Job]
                _state.value = ConnectionState.Preparing
                true
            } else {
                false // rapid repeated presses end up here
            }
        }
        if (!accepted) return@launch
        runSession(settings, host)
    }

    fun disconnect(host: TunnelHost): Job = scope.launch {
        val accepted = lock.withLock {
            val current = _state.value
            if (current is ConnectionState.Disconnected || current is ConnectionState.Disconnecting) {
                false
            } else {
                _state.value = ConnectionState.Disconnecting
                true
            }
        }
        val running = session
        session = null
        if (accepted) running?.cancelAndJoin()
        host.detach()
        safeStop()
        resetLiveState()
        _state.value = ConnectionState.Disconnected
    }

    /**
     * Synchronous last resort for Service.onDestroy(), where the scope is already cancelled.
     * Leaves the VPN in a clean Disconnected state instead of a stale "Connected".
     */
    fun killNow() {
        session?.cancel()
        session = null
        engine.stopNow()
        resetLiveState()
        _state.value = ConnectionState.Disconnected
    }

    private suspend fun runSession(settings: VpnSettings, host: TunnelHost) {
        // "Smart" has no flags of its own; validate the mode it would actually start with.
        val firstMode =
            if (settings.mode == TunnelMode.SMART) SmartPlan.modeFor(0) else settings.mode
        settings.copy(mode = firstMode).validate()?.let { problem ->
            fail(host, problem)
            return
        }

        var failures = 0
        var lastProblem = "the tunnel stopped answering"
        // Advanced only when a protocol fails to come up, so a reconnect after a drop keeps
        // the protocol that was working instead of walking the whole list again.
        var smartIndex = 0

        while (currentCoroutineContext().isActive) {
            val startedAt = System.currentTimeMillis()
            val mode =
                if (settings.mode == TunnelMode.SMART) SmartPlan.modeFor(smartIndex) else settings.mode
            val attempt = settings.copy(mode = mode)
            try {
                resetLiveState()
                _state.value =
                    if (failures == 0) ConnectionState.Connecting else ConnectionState.Reconnecting
                val endpoint = engine.start(attempt)
                host.attach(endpoint)
                failures = 0
                _activeMode.value = mode
                _endpoint.value = endpoint
                _state.value = ConnectionState.Connected(System.currentTimeMillis())
                monitor(endpoint, host)
                lastProblem = "the tunnel stopped answering"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastProblem = e.message ?: e.javaClass.simpleName
                if (settings.mode == TunnelMode.SMART) smartIndex++
                safeStop()
                host.detach()
            }

            // A session that only just came up and died again is a failure; one that ran
            // for a while earns a fresh budget of attempts.
            if (System.currentTimeMillis() - startedAt >= timing.healthySessionMs) failures = 0
            failures++
            if (failures > timing.maxAttempts) {
                fail(host, lastProblem)
                return
            }
            resetLiveState()
            _state.value = ConnectionState.Reconnecting
            delay(backoffMs(failures))
        }
    }

    /**
     * Runs while the tunnel is up. The exit lookup is a side coroutine so a slow or blocked
     * lookup can never delay the liveness checks.
     */
    private suspend fun monitor(endpoint: EngineEndpoint, host: TunnelHost) = coroutineScope {
        // The lookup is cosmetic: whatever goes wrong in it, it may never take the tunnel
        // down with it, so its failures are contained here instead of reaching the session.
        val lookup = launch {
            try {
                pollExit(endpoint)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
        try {
            watchLoop(endpoint, host)
        } finally {
            lookup.cancel()
        }
    }

    /**
     * Publishes traffic every tick and returns as soon as the tunnel is no longer usable:
     * the process is gone, or a real CONNECT through the SOCKS5 proxy failed
     * [Timing.probeMisses] times in a row. Throws CancellationException when cancelled.
     */
    private suspend fun watchLoop(endpoint: EngineEndpoint, host: TunnelHost) {
        var misses = 0
        val startedAt = System.currentTimeMillis()
        var lastProbe = startedAt
        var lastProcessCheck = startedAt

        while (currentCoroutineContext().isActive) {
            delay(timing.tickMs)
            val now = System.currentTimeMillis()
            _traffic.value = meter.sample(host.traffic(), now)

            if (now - lastProcessCheck >= timing.processCheckMs) {
                lastProcessCheck = now
                if (!engine.isAlive) return
            }
            if (now - lastProbe >= timing.probeIntervalMs) {
                lastProbe = now
                if (probe(endpoint)) {
                    misses = 0
                } else if (++misses >= timing.probeMisses) {
                    return
                }
            }
        }
    }

    /** Keeps asking the exit what it is, until the tunnel goes away. */
    private suspend fun pollExit(endpoint: EngineEndpoint) {
        while (currentCoroutineContext().isActive) {
            val info = try {
                withContext(Dispatchers.IO) { exitFinder.find(endpoint.host, endpoint.port) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (info != null) _exit.value = info
            delay(if (info != null) timing.exitRefreshMs else timing.exitRetryMs)
        }
    }

    private fun fail(host: TunnelHost, message: String) {
        engine.stopNow()
        host.detach()
        resetLiveState()
        _state.value = ConnectionState.Error(message)
    }

    private fun resetLiveState() {
        meter.reset()
        _traffic.value = TrafficSnapshot()
        _exit.value = null
        _activeMode.value = null
        _endpoint.value = null
    }

    private suspend fun safeStop() {
        try {
            engine.stop()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private fun backoffMs(failures: Int): Long {
        val shift = (failures - 1).coerceIn(0, 4)
        return (timing.baseBackoffMs shl shift).coerceAtMost(timing.maxBackoffMs)
    }

    companion object {
        /** Cloudflare answers HTTPS here, so a granted CONNECT means traffic really flowed. */
        private const val PROBE_TARGET_HOST = "1.1.1.1"
        private const val PROBE_TARGET_PORT = 443

        private val DEFAULT_PROBE: (EngineEndpoint) -> Boolean = { endpoint ->
            SocksProbe.connectsThrough(
                host = endpoint.host,
                port = endpoint.port,
                targetHost = PROBE_TARGET_HOST,
                targetPort = PROBE_TARGET_PORT,
            )
        }
    }
}
