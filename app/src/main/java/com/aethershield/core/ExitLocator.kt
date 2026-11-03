package com.aethershield.core

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Asks the exit itself what it looks like, with a real HTTPS request made through the
 * tunnel's own SOCKS5 listener.
 *
 * This app excludes its own sockets from the TUN (`addDisallowedApplication`), so a plain
 * request would leave the device directly and report the user's home address. Dialling
 * 127.0.0.1:<socks port> is the only way to see the address the world actually sees -- and
 * it keeps the lookup from leaking outside the tunnel.
 */
object SocksExitFinder : ExitFinder {

    private const val TAG = "ExitFinder"
    private const val PATH = "/cdn-cgi/trace"
    private const val HTTPS_PORT = 443
    private const val TIMEOUT_MS = 8_000

    /**
     * A literal address first: it needs no DNS and the proxy is guaranteed to accept an
     * IPv4 CONNECT. The host name is the fallback if that one ever stops answering.
     */
    private val HOSTS = listOf("1.1.1.1", "www.cloudflare.com")

    override fun find(proxyHost: String, proxyPort: Int): ExitInfo? {
        for (host in HOSTS) {
            val body = try {
                fetch(proxyHost, proxyPort, host)
            } catch (e: IOException) {
                Log.d(TAG, "exit lookup via $host failed: ${e.javaClass.simpleName}")
                null
            } ?: continue
            ExitTrace.parse(body)?.let { return it }
        }
        return null
    }

    private fun fetch(proxyHost: String, proxyPort: Int, host: String): String? {
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(proxyHost, proxyPort), TIMEOUT_MS)
            raw.soTimeout = TIMEOUT_MS
            if (!connectThroughSocks(raw, host, HTTPS_PORT)) return null

            val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, host, HTTPS_PORT, true) as SSLSocket
            ssl.soTimeout = TIMEOUT_MS
            ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            ssl.startHandshake()

            val request = "GET $PATH HTTP/1.1\r\n" +
                "Host: $host\r\n" +
                "User-Agent: AetherShield\r\n" +
                "Accept: */*\r\n" +
                "Connection: close\r\n\r\n"
            ssl.outputStream.run {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }
            return readBody(ssl.inputStream)
        } finally {
            runCatching { raw.close() }
        }
    }

    /** SOCKS5 CONNECT to [host]:[port]; the proxy resolves names, so no DNS happens here. */
    private fun connectThroughSocks(sock: Socket, host: String, port: Int): Boolean {
        val out = sock.getOutputStream()
        val input = sock.getInputStream()

        out.write(byteArrayOf(5, 1, 0))
        out.flush()
        val greeting = ByteArray(2)
        if (!input.readN(greeting, 2)) return false
        if (greeting[0] != 5.toByte() || greeting[1] != 0.toByte()) return false

        val ipv4 = parseIpv4(host)
        val request = if (ipv4 != null) {
            byteArrayOf(5, 1, 0, 1) + ipv4 + portBytes(port)
        } else {
            val name = host.toByteArray(Charsets.US_ASCII)
            byteArrayOf(5, 1, 0, 3, name.size.toByte()) + name + portBytes(port)
        }
        out.write(request)
        out.flush()

        val reply = ByteArray(4)
        if (!input.readN(reply, 4)) return false
        if (reply[0] != 5.toByte() || reply[1] != 0.toByte()) return false

        // Consume BND.ADDR + BND.PORT so the stream is positioned at the payload.
        val addrLength = when (reply[3].toInt()) {
            1 -> 4
            4 -> 16
            3 -> {
                val length = ByteArray(1)
                if (!input.readN(length, 1)) return false
                length[0].toInt() and 0xff
            }
            else -> return false
        }
        return input.readN(ByteArray(addrLength + 2), addrLength + 2)
    }

    private fun readBody(input: InputStream): String {
        val raw = input.readBytes().toString(Charsets.ISO_8859_1)
        val split = raw.indexOf("\r\n\r\n")
        if (split < 0) return raw
        val head = raw.substring(0, split).lowercase()
        val body = raw.substring(split + 4)
        return if (head.contains("transfer-encoding: chunked")) dechunk(body) else body
    }

    /** Minimal de-chunking: enough for a `key=value` page, no gzip. */
    private fun dechunk(body: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < body.length) {
            val lineEnd = body.indexOf("\r\n", i)
            if (lineEnd < 0) break
            val size = body.substring(i, lineEnd).trim().toIntOrNull(16) ?: break
            if (size == 0) break
            val start = lineEnd + 2
            val end = minOf(start + size, body.length)
            out.append(body, start, end)
            i = end + 2
        }
        return out.toString()
    }

    private fun parseIpv4(host: String): ByteArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for (i in 0..3) {
            val value = parts[i].toIntOrNull() ?: return null
            if (value !in 0..255) return null
            bytes[i] = value.toByte()
        }
        return bytes
    }

    private fun portBytes(port: Int): ByteArray =
        byteArrayOf((port shr 8).toByte(), port.toByte())

    private fun InputStream.readN(buffer: ByteArray, size: Int): Boolean {
        var read = 0
        while (read < size) {
            val n = read(buffer, read, size - read)
            if (n <= 0) return false
            read += n
        }
        return true
    }
}
