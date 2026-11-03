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

data class VpnSettings(
    val mode: TunnelMode = TunnelMode.MASQUE,
    val transport: Transport = Transport.HTTP3_QUIC,
) {
    /** Returns an error message, or null when the combination is valid. */
    fun validate(): String? =
        if (transport == Transport.HTTP2_TCP && !mode.usesMasque)
            "HTTP/2 transport is only valid for MASQUE-based modes"
        else null
}
