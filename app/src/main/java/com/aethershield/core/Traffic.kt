package com.aethershield.core

/**
 * The cumulative byte counters the TUN forwarder reports, seen from the tunnel:
 * [upBytes] is what the apps pushed into it, [downBytes] is what came back to them.
 */
data class TunCounters(val upBytes: Long, val downBytes: Long)

/** What the home screen shows: session totals plus the current throughput. */
data class TrafficSnapshot(
    val upBytes: Long = 0,
    val downBytes: Long = 0,
    val upBytesPerSec: Long = 0,
    val downBytesPerSec: Long = 0,
)

/**
 * Turns cumulative counters into session totals and a rate.
 *
 * Two things it has to survive: the counters restart at zero whenever the forwarder is
 * restarted (hev resets them in its fini), and the very first reading has nothing to be
 * compared against. Neither may ever produce a negative or absurd number on screen.
 */
class TrafficMeter(private val smoothing: Double = 0.5) {

    private var previous: TunCounters? = null
    private var previousAt = 0L

    private var totalUp = 0L
    private var totalDown = 0L
    private var upRate = 0.0
    private var downRate = 0.0

    fun reset() {
        previous = null
        previousAt = 0L
        totalUp = 0
        totalDown = 0
        upRate = 0.0
        downRate = 0.0
    }

    fun snapshot(): TrafficSnapshot = TrafficSnapshot(
        upBytes = totalUp,
        downBytes = totalDown,
        upBytesPerSec = upRate.toLong(),
        downBytesPerSec = downRate.toLong(),
    )

    fun sample(counters: TunCounters?, atMillis: Long): TrafficSnapshot {
        val now = counters ?: return snapshot() // no source right now: keep the last numbers
        val before = previous
        previous = now
        if (before == null) {
            // A baseline, not a delta: nothing has been measured yet.
            previousAt = atMillis
            return snapshot()
        }
        val elapsed = atMillis - previousAt
        previousAt = atMillis

        val upDelta = delta(now.upBytes, before.upBytes)
        val downDelta = delta(now.downBytes, before.downBytes)
        totalUp += upDelta
        totalDown += downDelta

        if (elapsed > 0) {
            upRate = smooth(upRate, upDelta * 1000.0 / elapsed)
            downRate = smooth(downRate, downDelta * 1000.0 / elapsed)
        }
        return snapshot()
    }

    private fun smooth(current: Double, instant: Double): Double =
        current * smoothing + instant * (1.0 - smoothing)

    /** A decrease means the counters were reset, not that traffic went backwards. */
    private fun delta(now: Long, before: Long): Long = if (now >= before) now - before else 0L
}
