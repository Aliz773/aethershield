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
        if (settings.mode.usesMasque) {
            add(if (settings.transport == Transport.HTTP2_TCP) "--h2" else "--h3")
        }
        // Never rely on the 1819 default: a leftover core from a previous run would own it.
        addAll(listOf("--bind", "$LOOPBACK:$port"))
        add(settings.scan.cliFlag)
        add(settings.ip.flag)
        settings.noize.cliValue?.let { addAll(listOf("--noize", it)) }
        add("--quick-reconnect")
        addAll(listOf("--log-level", "info"))
    }
}
