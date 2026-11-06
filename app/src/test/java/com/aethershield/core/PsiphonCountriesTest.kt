package com.aethershield.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PsiphonCountriesTest {

    @Test
    fun exactlyTheTwentySixSupportedRegionsAreListed() {
        assertEquals(26, PsiphonCountries.codes.size)
        assertEquals(26, PsiphonCountries.codes.distinct().size)
        assertEquals(
            setOf(
                "AT", "AU", "BE", "CA", "CH", "CZ", "DE", "DK", "ES", "FI", "FR", "GB",
                "ID", "IE", "IN", "IT", "JP", "LT", "NL", "NO", "PL", "RO", "RS", "SE",
                "SG", "US",
            ),
            PsiphonCountries.codes.toSet(),
        )
    }

    @Test
    fun everyCodeIsTwoUpperCaseLetters() {
        PsiphonCountries.codes.forEach { code ->
            assertEquals("$code must be two letters", 2, code.length)
            assertTrue("$code must be uppercase", code.all { it in 'A'..'Z' })
        }
    }

    @Test
    fun supportedCodesAreAcceptedInAnyCase() {
        assertTrue(PsiphonCountries.supports("DE"))
        assertTrue(PsiphonCountries.supports("de"))
        assertTrue(PsiphonCountries.supports("US"))
    }

    @Test
    fun unsupportedOrMalformedCodesAreRejected() {
        assertFalse("Iran is not a Psiphon exit", PsiphonCountries.supports("IR"))
        assertFalse(PsiphonCountries.supports("ZZ"))
        assertFalse(PsiphonCountries.supports(null))
        assertFalse(PsiphonCountries.supports(""))
        assertFalse(PsiphonCountries.supports("Germany"))
        assertFalse(PsiphonCountries.supports("D"))
    }

    @Test
    fun aBadRegionIsRejectedBeforeTheCoreIsTouched() {
        val settings = VpnSettings(
            mode = TunnelMode.MASQUE_PSIPHON,
            psiphonRegion = "IR",
        )

        assertEquals("Psiphon has no exit in IR", settings.validate())
        assertEquals(null, settings.copy(psiphonRegion = "DE").validate())
        assertEquals(null, settings.copy(psiphonRegion = null).validate())
    }

    @Test
    fun anUnsupportedRegionNeverReachesTheCommandLine() {
        val unsupported = AetherArgs.build(
            VpnSettings(mode = TunnelMode.GOOL_PSIPHON, psiphonRegion = "IR"), 19_000, 19_001,
        )
        assertFalse(unsupported.contains("--psiphon-region"))

        val supported = AetherArgs.build(
            VpnSettings(mode = TunnelMode.GOOL_PSIPHON, psiphonRegion = "se"), 19_000, 19_001,
        )
        assertTrue(supported.contains("--psiphon-region"))
        assertEquals("SE", supported[supported.indexOf("--psiphon-region") + 1])
    }

    @Test
    fun aRegionIsStillIgnoredOutsidePsiphonModes() {
        val args = AetherArgs.build(
            VpnSettings(mode = TunnelMode.MASQUE, psiphonRegion = "DE"), 19_000,
        )

        assertFalse(args.contains("--psiphon-region"))
    }
}
