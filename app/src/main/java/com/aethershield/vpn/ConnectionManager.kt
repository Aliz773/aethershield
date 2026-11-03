package com.aethershield.vpn

import com.aethershield.core.ConnectionState
import com.aethershield.core.EngineEndpoint
import com.aethershield.core.EngineStatus
import com.aethershield.core.SocksProbe
import com.aethershield.core.TrafficMeter
import com.aethershield.core.TrafficSnapshot
import com.aethershield.core.TunnelEngine
import com.aethershield.core.TunnelHost
import com.aethershield.core.VpnSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The ONE authoritative connection lifecycle. Service, notification and UI all use this.
 *
 * "Connected" is published only after engine.start() AND the TUN are both up, and it is
 * withdrawn as soon as the core dies or stops answering. A dropped tunnel is retried with
 * backoff and reported as Reconnecting instead of leaving a stale "Connected" on screen.
 *
 * The same monitor loop that watches the tunnel also samples the forwarder's byte counters,
 * so [traffic] is live exactly while the tunnel is, and zero the rest of the time.
 *
 * Timings and the liveness probe are injectable so the state machine can be unit tested
 * without waiting for minutes of real time.
 */
class ConnectionManager(
    private val engine: TunnelEngine,
    private val timing: Timing = Timing(),
    private val probe: (EngineEndpoint) -> Boolean = DEFAULT_PROBE,
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
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val meter = TrafficMeter()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _traffic = MutableStateFlow(TrafficSnapshot())
    val traffic: StateFlow<TrafficSnapshot> = _traffic.asStateFlow()

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
        resetTraffic()
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
        resetTraffic()
        _state.value = ConnectionState.Disconnected
    }

    private suspend fun runSession(settings: VpnSettings, host: TunnelHost) {
        val problem = settings.validate()
        if (problem != null) {
            fail(host, problem)
            return
        }

        var failures = 0
        var lastProblem = "the tunnel stopped answering"

        while (currentCoroutineContext().isActive) {
            val startedAt = System.currentTimeMillis()
            try {
                resetTraffic()
                _state.value =
                    if (failures == 0) ConnectionState.Connecting else ConnectionState.Reconnecting
                val endpoint = engine.start(settings)
                host.attach(endpoint)
                failures = 0
                _state.value = ConnectionState.Connected(System.currentTimeMillis())
                monitor(endpoint, host)
                lastProblem = "the tunnel stopped answering"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastProblem = e.message ?: e.javaClass.simpleName
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
            resetTraffic()
            _state.value = ConnectionState.Reconnecting
            delay(backoffMs(failures))
        }
    }

    /**
     * Runs while the tunnel is up. Publishes traffic every tick, and returns as soon as the
     * tunnel is no longer usable: the process is gone, or a real CONNECT through the SOCKS5
     * proxy failed [Timing.probeMisses] times in a row. Throws CancellationException when the
     * session is cancelled.
     */
    private suspend fun monitor(endpoint: EngineEndpoint, host: TunnelHost) {
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

    private fun fail(host: TunnelHost, message: String) {
        engine.stopNow()
        host.detach()
        resetTraffic()
        _state.value = ConnectionState.Error(message)
    }

    private fun resetTraffic() {
        meter.reset()
        _traffic.value = TrafficSnapshot()
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
