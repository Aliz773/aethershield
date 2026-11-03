package com.aethershield.core

import android.content.Context
import android.os.ParcelFileDescriptor
import hev.htproxy.TProxyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * TUN -> SOCKS5 forwarder using hev-socks5-tunnel.
 *
 * The descriptor stays owned by the VpnService: hev keeps a foreign fd rather than closing
 * it, so the same TUN can be handed back after a reconnect.
 */
class HevForwarder(private val context: Context) : TunForwarder {

    @Volatile
    private var running = false

    override suspend fun start(tun: ParcelFileDescriptor, endpoint: EngineEndpoint) =
        withContext(Dispatchers.IO) {
            stop() // never leave a previous run holding a socket or a stale endpoint
            val cfg = File(context.filesDir, "hev.yml")
            cfg.writeText(
                """
                tunnel:
                  mtu: 1500
                  ipv4: 10.8.0.2
                socks5:
                  port: ${endpoint.port}
                  address: ${endpoint.host}
                  udp: 'udp'
                """.trimIndent() + "\n"
            )
            try {
                if (!TProxyService.TProxyStartService(cfg.absolutePath, tun.fd)) {
                    throw EngineUnavailableException("hev-socks5-tunnel refused to start")
                }
                running = true
                delay(400) // give its worker thread a moment, then check it is really alive
                if (!TProxyService.TProxyIsRunning()) {
                    throw EngineUnavailableException("hev-socks5-tunnel stopped right after start")
                }
            } catch (e: LinkageError) {
                // library missing / wrong ABI / JNI class name mismatch
                throw EngineUnavailableException(
                    "cannot load libhev-socks5-tunnel.so (${e.javaClass.simpleName}: ${e.message})"
                )
            }
        }

    /**
     * Reads the tunnel's own byte counters (hev-jni.c packs them as
     * `[tx_packets, tx_bytes, rx_packets, rx_bytes]`, and in hev `tx` is what it read from
     * the TUN -- our upload -- while `rx` is what it wrote back -- our download).
     */
    override fun traffic(): TunCounters? {
        if (!running) return null
        return try {
            val stats = TProxyService.TProxyGetStats()
            if (stats.size < 4) null
            else TunCounters(upBytes = stats[1], downBytes = stats[3])
        } catch (_: LinkageError) {
            null
        }
    }

    override fun stop() {
        if (!running) return
        running = false
        try {
            TProxyService.TProxyStopService()
        } catch (_: LinkageError) {
        }
    }
}
