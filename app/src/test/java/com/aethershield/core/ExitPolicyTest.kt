package com.aethershield.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExitPolicyTest {

    @Test
    fun aBlockListIsNegated() {
        assertEquals("!IR", ExitPolicy.Block("IR").cliSpec)
        assertEquals("!RU", ExitPolicy.Block("ru").cliSpec)
    }

    @Test
    fun anAllowListIsTheBareCode() {
        assertEquals("DE", ExitPolicy.Only("DE").cliSpec)
        assertEquals("SE", ExitPolicy.Only("se").cliSpec)
    }

    @Test
    fun noPolicyPassesNoFlagAtAll() {
        assertNull(ExitPolicy.Any.cliSpec)
    }

    @Test
    fun storageRoundTrips() {
        listOf(
            ExitPolicy.Any,
            ExitPolicy.Block("IR"),
            ExitPolicy.Only("DE"),
        ).forEach { policy ->
            val stored = policy.encodeForStorage()
            assertEquals("$policy stored as $stored", policy, decodeExitPolicy(stored))
        }
    }

    @Test
    fun corruptStoredValuesFallBackToNoPolicy() {
        assertNull(decodeExitPolicy("garbage").cliSpec)
        assertEquals(ExitPolicy.Any, decodeExitPolicy("only:"))
        assertEquals(ExitPolicy.Any, decodeExitPolicy("block:IRAN"))
        assertEquals(ExitPolicy.Any, decodeExitPolicy(null))
        assertEquals(ExitPolicy.Any, decodeExitPolicy(""))
    }
}
