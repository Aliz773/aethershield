package com.aethershield.vpn

import com.aethershield.core.ConnectionState
import com.aethershield.core.EngineEndpoint
import com.aethershield.core.EngineUnavailableException
import com.aethershield.core.ExitFinder
import com.aethershield.core.ExitInfo
import com.aethershield.core.TrafficSnapshot
import com.aethershield.core.TunCounters
import com.aethershield.core.TunnelEngine
import com.aethershield.core.TunnelHost
import com.aethershield.core.TunnelMode
import com.aethershield.core.Transport
import com.aethershield.core.VpnSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Exercises the reconnect state machine and the traffic plumbing on the JVM. Timings are
 * injected, so a scenario that takes a minute on a phone runs in milliseconds here.
 */
class ConnectionManagerTest {

    private class FakeEngine : TunnelEngine {
        var startCount = 0
        var alive = false
        var failure: String? = null

        override suspend fun start(settings: VpnSettings): EngineEndpoint {
            startCount++
            failure?.let { throw EngineUnavailableException(it) }
            alive = true
            return EngineEndpoint("127.0.0.1", 18_000 + startCount)
        }

        override suspend fun stop() {
            alive = false
        }

        override fun stopNow() {
            alive = false
        }

        override val isAlive: Boolean
            get() = alive
    }

    private class FakeHost : TunnelHost {
        var attachCount = 0
        var detachCount = 0
        var lastEndpoint: EngineEndpoint? = null
        var counters: TunCounters? = null
        var trafficReads = 0

        override suspend fun attach(endpoint: EngineEndpoint) {
            attachCount++
            lastEndpoint = endpoint
        }

        override fun detach() {
            detachCount++
        }

        override fun traffic(): TunCounters? {
            trafficReads++
            return counters
        }
    }

    private fun timing(
        tickMs: Long = 10,
        processCheckMs: Long = 10,
        probeIntervalMs: Long = 10,
        probeMisses: Int = 2,
        maxAttempts: Int = 5,
        baseBackoffMs: Long = 10,
        maxBackoffMs: Long = 50,
    ) = ConnectionManager.Timing(
        tickMs = tickMs,
        processCheckMs = processCheckMs,
        probeIntervalMs = probeIntervalMs,
        probeMisses = probeMisses,
        healthySessionMs = 60_000,
        maxAttempts = maxAttempts,
        baseBackoffMs = baseBackoffMs,
        maxBackoffMs = maxBackoffMs,
        exitRefreshMs = 60_000,
        exitRetryMs = 60_000,
    )

    private fun waitUntil(what: String, timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        fail("timed out waiting for: $what")
    }

    private fun ConnectionManager.stopNow(host: TunnelHost) = runBlocking { disconnect(host).join() }

    @Test
    fun connect_publishesConnectedOnlyAfterTheTunIsAttached() {
        val engine = FakeEngine()
        val host = FakeHost()
        val manager = ConnectionManager(engine, timing(), probe = { true })

        val job: Job = manager.connect(VpnSettings(), host)
        waitUntil("Connected") { manager.state.value is ConnectionState.Connected }

        assertEquals(1, engine.startCount)
        assertEquals(1, host.attachCount)
        assertTrue(host.lastEndpoint != null)

        manager.stopNow(host)
        waitUntil("job finished") { job.isCompleted }
        assertTrue(manager.state.value is ConnectionState.Disconnected)
        assertEquals(1, host.detachCount)
    }

    @Test
    fun aFailingCore_isRetriedThenReportedAsAnError() {
        val engine = FakeEngine().apply { failure = "no gateway" }
        val host = FakeHost()
        val manager = ConnectionManager(engine, timing(maxAttempts = 2), probe = { true })

        runBlocking { manager.connect(VpnSettings(), host).join() }

        assertTrue(manager.state.value is ConnectionState.Error)
        assertEquals("one initial attempt plus maxAttempts retries", 3, engine.startCount)
        assertEquals("the TUN must never be built for a core that failed", 0, host.attachCount)
        assertEquals(TrafficSnapshot(), manager.traffic.value)
    }

    @Test
    fun disconnect_duringBackoff_endsDisconnectedAndStopsRetrying() {
        val engine = FakeEngine().apply { failure = "no gateway" }
        val host = FakeHost()
        val manager = ConnectionManager(
            engine,
            timing(maxAttempts = 10, baseBackoffMs = 5_000, maxBackoffMs = 5_000),
            probe = { true },
        )

        val job = manager.connect(VpnSettings(), host)
        waitUntil("Reconnecting") { manager.state.value is ConnectionState.Reconnecting }

        manager.stopNow(host)
        waitUntil("job finished") { job.isCompleted }

        assertTrue(manager.state.value is ConnectionState.Disconnected)
        val attemptsAtCancel = engine.startCount
        Thread.sleep(200)
        assertEquals("no attempt may run after disconnect", attemptsAtCancel, engine.startCount)
    }

    @Test
    fun aDeadCoreProcess_withoutAnException_dropsAndReconnects() {
        val engine = FakeEngine()
        val host = FakeHost()
        val manager = ConnectionManager(engine, timing(), probe = { true })

        manager.connect(VpnSettings(), host)
        waitUntil("first Connected") { manager.state.value is ConnectionState.Connected }
        assertEquals(1, host.attachCount)

        engine.alive = false // the core was killed from outside
        waitUntil("reconnected") {
            host.attachCount == 2 && manager.state.value is ConnectionState.Connected
        }

        manager.stopNow(host)
    }

    @Test
    fun aTunnelThatStopsCarryingTraffic_isTreatedAsDropped() {
        val engine = FakeEngine()
        val host = FakeHost()
        // The proxy answers, but the CONNECTs it grants never carry anything: exactly what
        // a wedged core looks like from the outside.
        var failedProbesLeft = 2
        val manager = ConnectionManager(
            engine,
            timing(),
            probe = {
                if (failedProbesLeft > 0) {
                    failedProbesLeft--
                    false
                } else {
                    true
                }
            },
        )

        manager.connect(VpnSettings(), host)
        waitUntil("reconnected after two dead probes") {
            host.attachCount == 2 && manager.state.value is ConnectionState.Connected
        }

        manager.stopNow(host)
    }

    @Test
    fun anInvalidSettingIsRejectedBeforeTheCoreIsTouched() {
        val engine = FakeEngine()
        val host = FakeHost()
        val manager = ConnectionManager(engine, timing(), probe = { true })

        val broken = VpnSettings(
            mode = TunnelMode.WARP_WIREGUARD,
            transport = Transport.HTTP2_TCP,
        )
        runBlocking { manager.connect(broken, host).join() }

        assertTrue(manager.state.value is ConnectionState.Error)
        assertEquals(0, engine.startCount)
        manager.stopNow(host)
    }

    @Test
    fun theForwarderCountersBecomeSessionTrafficAndAreClearedOnDisconnect() {
        val engine = FakeEngine()
        val host = FakeHost()
        val manager = ConnectionManager(engine, timing(), probe = { true })

        manager.connect(VpnSettings(), host)
        waitUntil("Connected") { manager.state.value is ConnectionState.Connected }
        assertEquals(TrafficSnapshot(), manager.traffic.value)

        host.counters = TunCounters(upBytes = 1_000, downBytes = 4_000)
        waitUntil("baseline read") { host.trafficReads >= 1 }
        assertEquals("the first reading is a baseline, not traffic", TrafficSnapshot(), manager.traffic.value)

        host.counters = TunCounters(upBytes = 3_000, downBytes = 14_000)
        waitUntil("deltas counted") { manager.traffic.value.downBytes >= 10_000 }

        assertEquals(2_000, manager.traffic.value.upBytes)
        assertEquals(10_000, manager.traffic.value.downBytes)

        manager.stopNow(host)
        assertEquals("traffic must not survive the session", TrafficSnapshot(), manager.traffic.value)
    }

    @Test
    fun theExitAddressIsPublishedWhileConnectedAndClearedAfterwards() {
        val engine = FakeEngine()
        val host = FakeHost()
        var lookups = 0
        val finder = ExitFinder { _, _ ->
            lookups++
            ExitInfo(ip = "203.0.113.9", countryCode = "DE", colo = "FRA")
        }
        val manager = ConnectionManager(
            engine,
            timing(),
            probe = { true },
            exitFinder = finder,
        )

        manager.connect(VpnSettings(), host)
        waitUntil("exit learned") { manager.exitInfo.value?.ip == "203.0.113.9" }

        assertEquals("DE", manager.exitInfo.value?.countryCode)
        assertEquals("FRA", manager.exitInfo.value?.colo)
        assertEquals(1, lookups)

        manager.stopNow(host)
        assertNull("a stale exit must not outlive the tunnel", manager.exitInfo.value)
    }
}
