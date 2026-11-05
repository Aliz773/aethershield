package com.aethershield.core

import android.os.ParcelFileDescriptor

/** Where the Aether core listens once it is really up (SOCKS5). */
data class EngineEndpoint(val host: String, val port: Int)

class EngineUnavailableException(message: String) : Exception(message)

/** One line the core printed, kept with the moment it arrived. */
data class LogEntry(val timeMillis: Long, val text: String)

/** Snapshot of the core process, for the diagnostics screen. */
data class EngineStatus(
    val running: Boolean = false,
    val pid: Long? = null,
    val endpoint: EngineEndpoint? = null,
    val exitCode: Int? = null,
    val lastLog: List<LogEntry> = emptyList(),
)

/**
 * SEAM 1: the Aether Rust core.
 * start() MUST return only after the core's local SOCKS5 is verified ready, or throw.
 */
interface TunnelEngine {
    suspend fun start(settings: VpnSettings): EngineEndpoint
    suspend fun stop()

    /**
     * Non-suspending, best-effort teardown. Exists for Service.onDestroy(), where the
     * coroutine scope is already cancelled and [stop] could not run.
     */
    fun stopNow()

    /** True while the core is still running. Cheap enough to poll every couple of seconds. */
    val isAlive: Boolean

    fun status(): EngineStatus = EngineStatus(running = isAlive)

    /** Drops the captured output; the Logs screen offers this as "clear". */
    fun clearLog() = Unit
}

/**
 * SEAM 2: the TUN side, owned by the VpnService.
 *
 * The descriptor survives a reconnect. hev-socks5-tunnel keeps a foreign fd rather than
 * closing it (hev_socks5_tunnel_fini only releases what it holds for it -- verified in
 * hev-socks5-tunnel.c), so a reconnect only points the forwarder at the new endpoint
 * instead of rebuilding the TUN and renegotiating routes with the system.
 */
interface TunnelHost {
    /** Open the TUN if it is not open yet, then forward it towards [endpoint]. */
    suspend fun attach(endpoint: EngineEndpoint)

    /** Stop the forwarder and close the TUN. Idempotent, never throws. */
    fun detach()

    /** Cumulative forwarder counters, or null when there is nothing to report yet. */
    fun traffic(): TunCounters? = null
}

/** SEAM 2 (the mechanics): TUN -> SOCKS5 forwarder. */
interface TunForwarder {
    suspend fun start(tun: ParcelFileDescriptor, endpoint: EngineEndpoint)
    fun stop()

    /** Cumulative counters since the forwarder last started; null when unavailable. */
    fun traffic(): TunCounters? = null
}

/** Honest placeholder: never pretends to connect. */
class UnavailableEngine : TunnelEngine {
    override suspend fun start(settings: VpnSettings): EngineEndpoint =
        throw EngineUnavailableException("Aether core is not integrated yet")

    override suspend fun stop() = Unit
    override fun stopNow() = Unit
    override val isAlive: Boolean = false
}

class UnavailableForwarder : TunForwarder {
    override suspend fun start(tun: ParcelFileDescriptor, endpoint: EngineEndpoint) =
        throw EngineUnavailableException("TUN forwarder (hev-socks5-tunnel) is not wired yet")

    override fun stop() = Unit
}
