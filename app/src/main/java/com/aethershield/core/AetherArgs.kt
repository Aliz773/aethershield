package com.aethershield.core

/**
 * The exact argv handed to the Aether CLI.
 *
 * Kept pure and out of the engine so the flags can be asserted in a JVM test instead of
 * being discovered on a phone. Every flag here comes from the embedded `--help` of the
 * shipped v2.4.0 binary; none of them makes the core ask a question on stdin.
 */
object AetherArgs {

    const val LOOPBACK = "127.0.0.1"

    /**
     * @param port the main SOCKS5 listener, always the WARP exit.
     * @param layerPort the second listener that `--tor` / `--psiphon` opens; unused when the
     *   mode carries no layer.
     * @param psiphonBinary where `psiphon-tunnel-core` was found, when the mode needs it.
     */
    fun build(
        settings: VpnSettings,
        port: Int,
        layerPort: Int = 0,
        psiphonBinary: String? = null,
    ): List<String> = buildList {
        val mode = settings.mode
        require(mode != TunnelMode.SMART) { "Smart must be resolved to a concrete mode first" }

        addAll(mode.cliFlags)
        // Never rely on the 1819 default: a leftover core from a previous run would own it.
        addAll(listOf("--bind", "$LOOPBACK:$port"))
        // The layered exit lives on its own port, so it is named rather than guessed (1820/1821).
        mode.layer.bindFlag?.let { addAll(listOf(it, "$LOOPBACK:$layerPort")) }

        // tor-only runs no WARP tunnel, so every gateway flag below is meaningless there --
        // and naming one it does not understand is a risk worth not taking.
        if (mode.isTunnel) {
            if (mode.usesMasque) {
                add(if (settings.transport == Transport.HTTP2_TCP) "--h2" else "--h3")
            }
            add(settings.scan.cliFlag)
            add(settings.ip.flag)
            settings.noize.cliValue?.let { addAll(listOf("--noize", it)) }
            add("--quick-reconnect")
        }

        if (mode.needsPsiphonBinary && psiphonBinary != null) {
            addAll(listOf("--psiphon-bin", psiphonBinary))
        }
        // Only Psiphon has selectable egress regions; a WARP exit is whatever edge the scan
        // landed on, and `--exit-loc` (a reject filter, not a picker) proved useless in practice.
        if (mode.hasSelectableCountry) {
            settings.psiphonRegion?.takeIf { it.length == 2 }?.let {
                addAll(listOf("--psiphon-region", it.uppercase()))
            }
        }
        addAll(listOf("--log-level", "info"))
    }
}
