package com.aethershield.core

import android.os.SystemClock
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * What the Home screen can honestly say about latency.
 *
 * [Unknown] means nothing has been measured yet (no connection, or the probe has not run).
 * [Timeout] means a measurement was attempted and did not come back in time -- that is
 * information, and different from "not measured".
 */
sealed interface LatencyState {
    data object Unknown : LatencyState
    data class Measured(val millis: Int) : LatencyState
    data object Timeout : LatencyState
}

/**
 * End-to-end latency through the tunnel: SOCKS5 CONNECT, then TLS, then a real HTTPS request
 * for Cloudflare's `/cdn-cgi/trace`, timed with a monotonic clock.
 *
 * This is *proxy* latency, not ICMP -- there is no ICMP inside a TCP tunnel, and calling it
 * ping would be a lie. The request is made through the tunnel's own SOCKS listener, so it
 * measures what a page load would actually experience; a direct request would only measure
 * the phone's own network and would be meaningless here.
 */
object TunnelLatency {

    private const val PROBE_HOST = "1.1.1.1"
    private const val PROBE_PORT = 443
    private const val PROBE_PATH = "/cdn-cgi/trace"
    private const val DEFAULT_TIMEOUT_MS = 6_000

    fun measure(
        proxyHost: String,
        proxyPort: Int,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): LatencyState {
        val started = SystemClock.elapsedRealtime()
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(proxyHost, proxyPort), timeoutMs)
            raw.soTimeout = timeoutMs
            if (!socksConnect(raw)) return LatencyState.Timeout

            val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, PROBE_HOST, PROBE_PORT, true) as SSLSocket
            ssl.soTimeout = timeoutMs
            ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            ssl.startHandshake()

            val request = "GET $PROBE_PATH HTTP/1.1\r\n" +
                "Host: $PROBE_HOST\r\n" +
                "User-Agent: AetherShield-latency\r\n" +
                "Accept: */*\r\n" +
                "Connection: close\r\n\r\n"
            ssl.outputStream.run {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }
            // One byte of a real answer is enough proof that the tunnel carried the request.
            if (ssl.inputStream.read() < 0) return LatencyState.Timeout

            return LatencyState.Measured(
                ((SystemClock.elapsedRealtime() - started).toInt()).coerceAtLeast(1)
            )
        } catch (_: IOException) {
            return LatencyState.Timeout
        } finally {
            runCatching { raw.close() }
        }
    }

    /** SOCKS5 CONNECT with the address as a literal, so no DNS happens outside the tunnel. */
    private fun socksConnect(socket: Socket): Boolean {
        val out = socket.getOutputStream()
        val input = socket.getInputStream()

        out.write(byteArrayOf(5, 1, 0))
        out.flush()
        val greeting = ByteArray(2)
        if (!input.readN(greeting)) return false
        if (greeting[1] != 0.toByte()) return false

        val address = PROBE_HOST.split('.').mapNotNull { it.toIntOrNull()?.toByte() }.toByteArray()
        if (address.size != 4) return false
        val request = byteArrayOf(5, 1, 0, 1) + address +
            byteArrayOf((PROBE_PORT shr 8).toByte(), PROBE_PORT.toByte())
        out.write(request)
        out.flush()

        val reply = ByteArray(4)
        if (!input.readN(reply) || reply[1] != 0.toByte()) return false
        val length = when (reply[3].toInt() and 0xff) {
            1 -> 4
            4 -> 16
            else -> return false
        }
        return input.readN(ByteArray(length + 2))
    }

    private fun InputStream.readN(buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = read(buffer, read, buffer.size - read)
            if (n <= 0) return false
            read += n
        }
        return true
    }
}
