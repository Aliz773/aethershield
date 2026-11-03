package com.aethershield.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExitTraceTest {

    @Test
    fun readsTheAddressAndTheCountryTheExitReports() {
        val body = "fl=1031f7\r\n" +
            "h=1.1.1.1\r\n" +
            "ip=203.0.113.9\r\n" +
            "ts=1793742272.000\r\n" +
            "colo=FRA\r\n" +
            "loc=DE\r\n" +
            "warp=off\r\n"

        val info = ExitTrace.parse(body)!!

        assertEquals("203.0.113.9", info.ip)
        assertEquals("DE", info.countryCode)
        assertEquals("FRA", info.colo)
    }

    @Test
    fun countryCodesAreNormalisedToUpperCase() {
        val info = ExitTrace.parse("ip=198.51.100.7\nloc=se\n")!!

        assertEquals("SE", info.countryCode)
    }

    @Test
    fun aPageWithoutAnAddressIsRejected() {
        assertNull(ExitTrace.parse("fl=abc\nloc=DE\n"))
    }

    @Test
    fun aPageWithoutACountryIsRejected() {
        assertNull(ExitTrace.parse("ip=198.51.100.7\ncolo=FRA\n"))
    }

    @Test
    fun emptyValuesAreRejected() {
        assertNull(ExitTrace.parse("ip=\nloc=DE\n"))
        assertNull(ExitTrace.parse("ip=198.51.100.7\nloc=\n"))
    }

    @Test
    fun unprefixedNoiseAndMissingColoAreBothFine() {
        // Chunk-size lines and other keys must not disturb the two fields we need.
        val info = ExitTrace.parse("1a\r\nip=198.51.100.7\r\nloc=US\r\n0\r\n")!!

        assertEquals("198.51.100.7", info.ip)
        assertEquals("US", info.countryCode)
        assertNull(info.colo)
    }
}
