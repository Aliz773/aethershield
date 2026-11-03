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

    fun build(settings: VpnSettings, port: Int): List<String> = buildList {
        add(settings.mode.cliFlag)
        // Never rely on the 1819 default: a leftover core from a previous run would own it.
        addAll(listOf("--bind", "$LOOPBACK:$port"))

        // tor-only runs no WARP tunnel, so every gateway flag below is meaningless there --
        // and naming one it does not understand is a risk worth not taking.
        if (settings.mode.isTunnel) {
            if (settings.mode.usesMasque) {
                add(if (settings.transport == Transport.HTTP2_TCP) "--h2" else "--h3")
            }
            add(settings.scan.cliFlag)
            add(settings.ip.flag)
            settings.noize.cliValue?.let { addAll(listOf("--noize", it)) }
            add("--quick-reconnect")
        }

        settings.exit.cliSpec?.let { addAll(listOf("--exit-loc", it)) }
        addAll(listOf("--log-level", "info"))
    }
}
