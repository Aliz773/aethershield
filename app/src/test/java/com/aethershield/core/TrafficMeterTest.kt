package com.aethershield.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficMeterTest {

    @Test
    fun theFirstSampleIsABaselineAndCountsNothing() {
        val meter = TrafficMeter()
        val snapshot = meter.sample(TunCounters(upBytes = 9_000, downBytes = 9_000), atMillis = 1_000)

        assertEquals(0, snapshot.upBytes)
        assertEquals(0, snapshot.downBytes)
        assertEquals(0, snapshot.upBytesPerSec)
        assertEquals(0, snapshot.downBytesPerSec)
    }

    @Test
    fun consecutiveSamplesBecomeSessionTotals() {
        val meter = TrafficMeter()
        meter.sample(TunCounters(0, 0), atMillis = 0)
        meter.sample(TunCounters(1_500, 6_000), atMillis = 1_000)
        val snapshot = meter.sample(TunCounters(2_000, 8_000), atMillis = 2_000)

        assertEquals(2_000, snapshot.upBytes)
        assertEquals(8_000, snapshot.downBytes)
        assertTrue("1000 bytes in 1s should produce a positive rate", snapshot.upBytesPerSec > 0)
    }

    @Test
    fun aCounterResetIsNotTreatedAsNegativeTraffic() {
        val meter = TrafficMeter()
        meter.sample(TunCounters(0, 0), atMillis = 0)
        meter.sample(TunCounters(5_000, 50_000), atMillis = 1_000)

        // The forwarder was restarted: hev resets its counters in fini().
        val afterReset = meter.sample(TunCounters(100, 200), atMillis = 2_000)

        assertEquals("bytes already counted are kept", 5_000, afterReset.upBytes)
        assertEquals(50_000, afterReset.downBytes)
        assertTrue("no negative totals", afterReset.upBytes >= 0 && afterReset.downBytes >= 0)

        val next = meter.sample(TunCounters(600, 1_200), atMillis = 3_000)
        assertEquals("counting resumes from the new base", 5_500, next.upBytes)
    }

    @Test
    fun aZeroLengthIntervalDoesNotDivideByZero() {
        val meter = TrafficMeter()
        meter.sample(TunCounters(0, 0), atMillis = 500)
        val snapshot = meter.sample(TunCounters(4_000, 4_000), atMillis = 500)

        assertEquals(4_000, snapshot.upBytes)
        assertEquals("no elapsed time means no rate", 0, snapshot.upBytesPerSec)
    }

    @Test
    fun aMissingSourceKeepsTheLastNumbers() {
        val meter = TrafficMeter()
        meter.sample(TunCounters(0, 0), atMillis = 0)
        meter.sample(TunCounters(1_000, 2_000), atMillis = 1_000)

        val snapshot = meter.sample(null, atMillis = 2_000)

        assertEquals(1_000, snapshot.upBytes)
        assertEquals(2_000, snapshot.downBytes)
    }

    @Test
    fun resetClearsEverything() {
        val meter = TrafficMeter()
        meter.sample(TunCounters(0, 0), atMillis = 0)
        meter.sample(TunCounters(1_000, 2_000), atMillis = 1_000)
        meter.reset()

        assertEquals(TrafficSnapshot(), meter.snapshot())

        val afterReset = meter.sample(TunCounters(50_000, 60_000), atMillis = 2_000)
        assertEquals("a fresh baseline after reset", 0, afterReset.upBytes)
    }
}
