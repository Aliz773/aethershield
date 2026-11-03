package com.aethershield.core

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Preparing : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val sinceMillis: Long) : ConnectionState
    data object Reconnecting : ConnectionState
    data object Disconnecting : ConnectionState
    data class Error(val message: String) : ConnectionState
}

val ConnectionState.isIdle: Boolean
    get() = this is ConnectionState.Disconnected || this is ConnectionState.Error

/**
 * cliFlag values are taken from the embedded `--help` of the shipped v2.4.0 binary
 * (see AetherProcessEngine.CORE_VERSION), so they are verified, not guessed.
 */
enum class TunnelMode(val cliFlag: String, val usesMasque: Boolean) {
    MASQUE("--masque", true),
    WARP_WIREGUARD("--wg", false),
    GOOL("--gool", true),
    GOOL_CLASSIC("--gool-classic", false),
    MIM("--mim", true),
}

enum class Transport { HTTP3_QUIC, HTTP2_TCP }

/**
 * How hard the core looks for a working gateway before it gives up.
 * `--turbo` is fine on a permissive network and hopeless on a filtered one.
 */
enum class ScanMode(val cliFlag: String) {
    TURBO("--turbo"),
    BALANCED("--balanced"),
    THOROUGH("--thorough"),
    VERIFIED("--verified"),
    IRONCLAD("--ironclad"),
}

/**
 * How much the tunnel disguises itself (`--noize <profile>`).
 * [AUTO] passes no flag at all, which lets the core keep its per-mode default
 * (firewall for MASQUE, balanced for WireGuard and gool).
 */
enum class NoizeProfile(val cliValue: String?) {
    AUTO(null),
    OFF("off"),
    LIGHT("light"),
    FIREWALL("firewall"),
    BALANCED("balanced"),
    GFW("gfw"),
    AGGRESSIVE("aggressive"),
}

/** Which address family the core uses to reach the gateway (`-4`, `-6`, `--dual`). */
enum class IpVersion(val flag: String) {
    IPV4("-4"),
    IPV6("-6"),
    DUAL("--dual"),
}

data class VpnSettings(
    val mode: TunnelMode = TunnelMode.MASQUE,
    val transport: Transport = Transport.HTTP3_QUIC,
    val scan: ScanMode = ScanMode.TURBO,
    val noize: NoizeProfile = NoizeProfile.AUTO,
    val ip: IpVersion = IpVersion.IPV4,
) {
    /** Returns an error message, or null when the combination is valid. */
    fun validate(): String? =
        if (transport == Transport.HTTP2_TCP && !mode.usesMasque)
            "HTTP/2 transport is only valid for MASQUE-based modes"
        else null
}
