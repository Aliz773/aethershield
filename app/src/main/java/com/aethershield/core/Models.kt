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
 * An extra hop carried by the WARP tunnel (`--tor` / `--psiphon`).
 *
 * The core does not move the exit when one of these is on: it keeps the WARP exit on `--bind`
 * and opens a *second* SOCKS listener on [bindFlag] that comes out of tor/psiphon. Routing the
 * device through that layer therefore means pointing the TUN at that second port, which is what
 * the engine does.
 */
enum class Layer(val flag: String?, val bindFlag: String?) {
    NONE(null, null),
    TOR("--tor", "--tor-bind"),
    PSIPHON("--psiphon", "--psiphon-bind"),
}

/**
 * Every mode the shipped v2.4.0 binary can actually run, named the way the other Aether GUIs
 * name them: a protocol (MASQUE / WireGuard / gool / MIM) optionally carrying tor or psiphon.
 *
 * [SMART] is not a flag set: it walks [SmartPlan.order] until something connects.
 * All the other flags are taken from the binary's own `--help`.
 */
enum class TunnelMode(
    val cliFlags: List<String>,
    val usesMasque: Boolean,
    val layer: Layer = Layer.NONE,
    /** False for smart and tor-only: there is no WARP tunnel to scan for. */
    val isTunnel: Boolean = true,
) {
    SMART(emptyList(), usesMasque = false, isTunnel = false),
    MASQUE(listOf("--masque"), usesMasque = true),
    WIREGUARD(listOf("--wg"), usesMasque = false),
    GOOL(listOf("--gool"), usesMasque = true),
    MIM(listOf("--mim"), usesMasque = true),
    WIREGUARD_PSIPHON(listOf("--wg", "--psiphon"), usesMasque = false, layer = Layer.PSIPHON),
    GOOL_PSIPHON(listOf("--gool", "--psiphon"), usesMasque = true, layer = Layer.PSIPHON),
    MASQUE_PSIPHON(listOf("--masque", "--psiphon"), usesMasque = true, layer = Layer.PSIPHON),
    WIREGUARD_TOR(listOf("--wg", "--tor"), usesMasque = false, layer = Layer.TOR),
    GOOL_TOR(listOf("--gool", "--tor"), usesMasque = true, layer = Layer.TOR),
    MASQUE_TOR(listOf("--masque", "--tor"), usesMasque = true, layer = Layer.TOR),
    GOOL_CLASSIC(listOf("--gool-classic"), usesMasque = false),
    TOR_ONLY(listOf("--tor-only"), usesMasque = false, isTunnel = false),
}

/** Tor needs minutes to bootstrap, whether it is the whole tunnel or a layer inside one. */
val TunnelMode.slowStart: Boolean
    get() = this == TunnelMode.TOR_ONLY || layer == Layer.TOR

/**
 * Whether the HTTP/2-vs-HTTP/3 choice is meaningful. Smart is included because it may land on
 * MASQUE, and disabling the chips for it would take the choice away for no reason.
 */
val TunnelMode.mayUseMasque: Boolean
    get() = usesMasque || this == TunnelMode.SMART

/** Psiphon shells out to a binary this app does not ship; the engine fails fast and says so. */
val TunnelMode.needsPsiphonBinary: Boolean
    get() = layer == Layer.PSIPHON

/**
 * Only a Psiphon exit has selectable countries: `--psiphon-region` asks Psiphon for an egress
 * region, while a plain WARP exit is whatever edge the scan lands on -- there is nothing to ask.
 */
val TunnelMode.hasSelectableCountry: Boolean
    get() = layer == Layer.PSIPHON

/**
 * What "Smart" tries, in order. It walks down the list until one protocol connects and then
 * sticks with that one for the rest of the session, so a reconnect does not start over.
 */
object SmartPlan {
    val order: List<TunnelMode> = listOf(
        TunnelMode.MASQUE,
        TunnelMode.WIREGUARD,
        TunnelMode.GOOL,
        TunnelMode.MIM,
    )

    fun modeFor(attempt: Int): TunnelMode = order[attempt % order.size]
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
    /** ISO-3166 alpha-2 egress region for `--psiphon-region`, or null for "let Psiphon pick". */
    val psiphonRegion: String? = null,
) {
    /** Returns an error message, or null when the combination is valid. */
    fun validate(): String? = when {
        transport == Transport.HTTP2_TCP && !mode.usesMasque ->
            "HTTP/2 transport is only valid for MASQUE-based modes"
        // Psiphon only serves a fixed set of exits; asking for another one loops forever.
        mode.hasSelectableCountry && psiphonRegion != null && !PsiphonCountries.supports(psiphonRegion) ->
            "Psiphon has no exit in ${psiphonRegion.uppercase()}"
        else -> null
    }
}

/**
 * ISO-3166 alpha-2 → regional indicator letters, which render as the country's flag.
 * Anything that is not two ASCII letters comes back untouched, so a bad value can never
 * print control characters.
 */
fun flagEmoji(countryCode: String): String {
    val code = countryCode.uppercase()
    if (code.length != 2 || code.any { it !in 'A'..'Z' }) return ""
    return code.map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
}
