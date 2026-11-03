package com.aethershield.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The flags are the whole contract with the core: a wrong or missing one either makes it
 * prompt on a stdin that does not exist, or silently ignores the user's choice.
 */
class AetherArgsTest {

    private fun args(settings: VpnSettings, port: Int = 19_000): List<String> =
        AetherArgs.build(settings, port)

    private fun List<String>.valueOf(flag: String): String {
        val i = indexOf(flag)
        assertTrue("expected $flag in $this", i >= 0)
        return this[i + 1]
    }

    @Test
    fun defaultsAreFullySpecifiedSoTheCoreNeverAsksAQuestion() {
        val a = args(VpnSettings())

        assertTrue(a.contains("--masque"))
        assertTrue(a.contains("--h3"))
        assertTrue(a.contains("--turbo"))
        assertTrue(a.contains("-4"))
        assertTrue(a.contains("--quick-reconnect"))
        assertEquals("127.0.0.1:19000", a.valueOf("--bind"))
        assertFalse("obfuscation is left to the core unless asked", a.contains("--noize"))
    }

    @Test
    fun everySettingReachesTheCommandLine() {
        val a = args(
            VpnSettings(
                mode = TunnelMode.GOOL,
                transport = Transport.HTTP2_TCP,
                scan = ScanMode.THOROUGH,
                noize = NoizeProfile.GFW,
                ip = IpVersion.DUAL,
            )
        )

        assertTrue(a.contains("--gool"))
        assertTrue(a.contains("--h2"))
        assertFalse(a.contains("--h3"))
        assertTrue(a.contains("--thorough"))
        assertFalse(a.contains("--turbo"))
        assertTrue(a.contains("--dual"))
        assertFalse(a.contains("-4"))
        assertEquals("gfw", a.valueOf("--noize"))
    }

    @Test
    fun wireGuardModesDoNotGetAMasqueTransportFlag() {
        val a = args(VpnSettings(mode = TunnelMode.WARP_WIREGUARD))

        assertTrue(a.contains("--wg"))
        assertFalse(a.contains("--h2"))
        assertFalse(a.contains("--h3"))
    }

    @Test
    fun turningObfuscationOffIsPassedExplicitlyRatherThanOmitted() {
        val a = args(VpnSettings(noize = NoizeProfile.OFF))

        assertEquals("off", a.valueOf("--noize"))
    }

    @Test
    fun theBindPortIsAlwaysTheOneThatWasChosen() {
        val a = args(VpnSettings(), port = 41_234)

        assertEquals("127.0.0.1:41234", a.valueOf("--bind"))
    }

    @Test
    fun everyScanModeHasItsOwnVerifiedFlag() {
        val expected = mapOf(
            ScanMode.TURBO to "--turbo",
            ScanMode.BALANCED to "--balanced",
            ScanMode.THOROUGH to "--thorough",
            ScanMode.VERIFIED to "--verified",
            ScanMode.IRONCLAD to "--ironclad",
        )
        expected.forEach { (mode, flag) ->
            val a = args(VpnSettings(scan = mode))
            assertTrue("$mode should pass $flag", a.contains(flag))
        }
    }

    @Test
    fun torOnlyRunsNoTunnelSoItGetsNoGatewayFlags() {
        val a = args(VpnSettings(mode = TunnelMode.TOR_ONLY))

        assertTrue(a.contains("--tor-only"))
        assertEquals("the proxy still needs its port", "127.0.0.1:19000", a.valueOf("--bind"))
        assertFalse("no masque carrier is involved", a.contains("--h3"))
        assertFalse(a.contains("--h2"))
        assertFalse("nothing to scan for", a.contains("--turbo"))
        assertFalse(a.contains("-4"))
        assertFalse(a.contains("--quick-reconnect"))
    }

    @Test
    fun aCountryPolicyBecomesAnExitLocSpec() {
        val blocking = args(VpnSettings(exit = ExitPolicy.Block("IR")))
        assertEquals("!IR", blocking.valueOf("--exit-loc"))

        val only = args(VpnSettings(exit = ExitPolicy.Only("de")))
        assertEquals("codes are normalised", "DE", only.valueOf("--exit-loc"))
    }

    @Test
    fun withoutAPolicyNothingIsPassed() {
        assertFalse(args(VpnSettings()).contains("--exit-loc"))
    }

    @Test
    fun aCountryPolicyAppliesToTorOnlyToo() {
        val a = args(VpnSettings(mode = TunnelMode.TOR_ONLY, exit = ExitPolicy.Block("IR")))

        assertEquals("!IR", a.valueOf("--exit-loc"))
    }
}
