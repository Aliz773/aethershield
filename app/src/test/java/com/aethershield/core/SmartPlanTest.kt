package com.aethershield.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SmartPlanTest {

    @Test
    fun itOnlyWalksPlainProtocols() {
        SmartPlan.order.forEach { mode ->
            assertNotEquals("smart cannot be part of its own plan", TunnelMode.SMART, mode)
            assertEquals("psiphon/tor layers would make the first attempt far too slow",
                Layer.NONE, mode.layer)
            assertEquals("$mode must be a real tunnel", true, mode.isTunnel)
        }
    }

    @Test
    fun theOrderStartsWithTheModernProtocolAndCoversTheFour() {
        assertEquals(TunnelMode.MASQUE, SmartPlan.order.first())
        assertEquals(
            setOf(TunnelMode.MASQUE, TunnelMode.WIREGUARD, TunnelMode.GOOL, TunnelMode.MIM),
            SmartPlan.order.toSet(),
        )
    }

    @Test
    fun itWrapsAroundSoALongSessionKeepsTrying() {
        val size = SmartPlan.order.size

        assertEquals(SmartPlan.order[0], SmartPlan.modeFor(0))
        assertEquals(SmartPlan.order[1], SmartPlan.modeFor(1))
        assertEquals(SmartPlan.order[0], SmartPlan.modeFor(size))
        assertEquals(SmartPlan.order[1], SmartPlan.modeFor(size + 1))
    }
}
