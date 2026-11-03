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
 * Every protocol the shipped v2.4.0 binary offers.
 *
 * The interactive menu of the core lists four (MASQUE, WireGuard, gool, MASQUE-in-MASQUE);
 * `--gool-classic` is its older flavour of gool, and `--tor-only` is the one entry that is
 * not a WARP tunnel at all. All flags verified against the binary's own `--help`.
 *
 * Psiphon is deliberately absent: it shells out to a separate `psiphon-tunnel-core` binary
 * ("when it is not next to aether, in ./pt, or on PATH") which is not part of this APK, so a
 * mode for it would only ever fail. Tor, by contrast, is linked into the binary.
 */
enum class TunnelMode(
    val cliFlag: String,
    val usesMasque: Boolean,
    /** False for tor-only: there is no WARP tunnel to scan for, only a local tor proxy. */
    val isTunnel: Boolean = true,
) {
    MASQUE("--masque", true),
    WARP_WIREGUARD("--wg", false),
    GOOL("--gool", true),
    GOOL_CLASSIC("--gool-classic", false),
    MIM("--mim", true),
    TOR_ONLY("--tor-only", false, isTunnel = false),
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

/**
 * `--exit-loc`: the core refuses a tunnel whose exit country is not acceptable, checked
 * through the finished tunnel before it exposes SOCKS5 and again every minute after.
 *
 * This is a filter, not a picker -- the core does not ask for a country, it rejects what it
 * got. Turning it into "connect to Germany" is the app's job: [com.aethershield.vpn.ConnectionManager]
 * keeps retrying until an acceptable exit comes up.
 */
sealed interface ExitPolicy {
    data object Any : ExitPolicy

    /** `!IR` -- any exit except these. */
    data class Block(val countryCode: String) : ExitPolicy

    /** `DE` -- only these. */
    data class Only(val countryCode: String) : ExitPolicy

    /** The `--exit-loc` argument, or null when the flag should not be passed at all. */
    val cliSpec: String?
        get() = when (this) {
            Any -> null
            is Block -> "!${countryCode.uppercase()}"
            is Only -> countryCode.uppercase()
        }
}

internal fun ExitPolicy.encodeForStorage(): String = when (this) {
    ExitPolicy.Any -> "any"
    is ExitPolicy.Block -> "block:${countryCode.uppercase()}"
    is ExitPolicy.Only -> "only:${countryCode.uppercase()}"
}

internal fun decodeExitPolicy(raw: String?): ExitPolicy {
    val parts = raw?.split(':', limit = 2) ?: return ExitPolicy.Any
    val code = parts.getOrNull(1)?.takeIf { it.length == 2 } ?: return ExitPolicy.Any
    return when (parts[0]) {
        "block" -> ExitPolicy.Block(code.uppercase())
        "only" -> ExitPolicy.Only(code.uppercase())
        else -> ExitPolicy.Any
    }
}

data class VpnSettings(
    val mode: TunnelMode = TunnelMode.MASQUE,
    val transport: Transport = Transport.HTTP3_QUIC,
    val scan: ScanMode = ScanMode.TURBO,
    val noize: NoizeProfile = NoizeProfile.AUTO,
    val ip: IpVersion = IpVersion.IPV4,
    val exit: ExitPolicy = ExitPolicy.Any,
) {
    /** Returns an error message, or null when the combination is valid. */
    fun validate(): String? =
        if (transport == Transport.HTTP2_TCP && !mode.usesMasque)
            "HTTP/2 transport is only valid for MASQUE-based modes"
        else null
}
