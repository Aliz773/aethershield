package com.aethershield.core

import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * A minimal SOCKS5 client.
 *
 * A bare TCP connect to the proxy port proves nothing: the kernel accepts the connection
 * as soon as the listener is bound, which for the Aether core happens before it can carry
 * traffic. Every check here speaks the protocol instead.
 */
internal object SocksProbe {

    /**
     * Just the greeting: `05 01 00` -> `05 00`. The cheapest possible "is the proxy really
     * answering" test, used while the core is booting.
     */
    fun greets(host: String, port: Int, timeoutMs: Int = 600): Boolean = try {
        Socket().use { sock ->
            sock.connect(InetSocketAddress(host, port), timeoutMs)
            sock.soTimeout = timeoutMs
            sock.getOutputStream().run {
                write(byteArrayOf(5, 1, 0))
                flush()
            }
            val reply = ByteArray(2)
            sock.getInputStream().readFully(reply, 2) &&
                reply[0] == 5.toByte() && reply[1] == 0.toByte()
        }
    } catch (_: IOException) {
        false
    }

    /**
     * End-to-end: ask the proxy to CONNECT to [targetHost]:[targetPort] and see whether it
     * grants it. This is the only check that can tell a live tunnel from a core that is
     * still running but no longer carrying anything.
     *
     * [targetHost] must be a literal IPv4 address, so no DNS is involved anywhere.
     */
    fun connectsThrough(
        host: String,
        port: Int,
        targetHost: String,
        targetPort: Int,
        timeoutMs: Int = 5_000,
    ): Boolean = try {
        val target = InetAddress.getByName(targetHost) as? Inet4Address ?: return false
        Socket().use { sock ->
            sock.connect(InetSocketAddress(host, port), timeoutMs)
            sock.soTimeout = timeoutMs
            val out = sock.getOutputStream()
            out.run {
                write(byteArrayOf(5, 1, 0))
                flush()
            }
            val greeting = ByteArray(2)
            val input = sock.getInputStream()
            if (!input.readFully(greeting, 2) || greeting[1] != 0.toByte()) return false

            // VER, CMD=CONNECT, RSV, ATYP=IPv4, address, port
            val request = byteArrayOf(5, 1, 0, 1) +
                target.address +
                byteArrayOf((targetPort shr 8).toByte(), targetPort.toByte())
            out.run {
                write(request)
                flush()
            }
            // VER, REP, RSV, ATYP -- REP==0 means the proxy opened the upstream connection.
            val reply = ByteArray(4)
            input.readFully(reply, 4) && reply[0] == 5.toByte() && reply[1] == 0.toByte()
        }
    } catch (_: IOException) {
        false
    }

    /**
     * The same CONNECT as [connectsThrough], reported as milliseconds. This is handshake
     * latency measured through the tunnel -- honest, and the only latency a TCP tunnel can
     * offer, since ICMP never enters it.
     */
    fun connectMillis(
        host: String,
        port: Int,
        targetHost: String,
        targetPort: Int,
        timeoutMs: Int = 5_000,
    ): Int? {
        val started = System.nanoTime()
        if (!connectsThrough(host, port, targetHost, targetPort, timeoutMs)) return null
        return ((System.nanoTime() - started) / 1_000_000).toInt()
    }

    /** [Socket.inputStream].read() is allowed to return short; loop until [size] or EOF. */
    private fun InputStream.readFully(buffer: ByteArray, size: Int): Boolean {
        var read = 0
        while (read < size) {
            val n = read(buffer, read, size - read)
            if (n <= 0) return false
            read += n
        }
        return true
    }
}
