package com.aethershield.sharing

import com.aethershield.core.EngineEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Shares the running tunnel with the local network: guests point their proxy at
 * `http://<phone>:10810`, and every connection is relayed through the tunnel's SOCKS5 port.
 *
 * One port serves both protocols because the first byte tells them apart: SOCKS5 starts with
 * 0x05, an HTTP request with a method letter. That keeps the thing a guest has to type short,
 * and it means an Android or iOS device can use it with the plain "HTTP proxy" field the OS
 * already has -- no per-app setup.
 *
 * [upstream] is read on every connection, so a reconnect to a new local port just works.
 */
class SharingServer(private val upstream: () -> EngineEndpoint?) {

    private class Session(
        val id: Long,
        val socket: Socket,
        val address: String,
        val port: Int,
        val protocol: String,
        val userAgent: String?,
        val os: ClientOs,
        val since: Long,
    ) {
        val up = AtomicLong()
        val down = AtomicLong()

        fun toClient() = SharedClient(
            id = id,
            address = address,
            port = port,
            protocol = protocol,
            os = os,
            userAgent = userAgent,
            sinceMillis = since,
            upBytes = up.get(),
            downBytes = down.get(),
        )
    }

    private val sessions = ConcurrentHashMap<Long, Session>()
    private val nextId = AtomicLong(1)
    private val _state = MutableStateFlow(SharingState())
    val state: StateFlow<SharingState> = _state.asStateFlow()

    @Volatile
    private var server: ServerSocket? = null

    @Volatile
    private var boundPort = SharingState.DEFAULT_PORT

    /** Live view for the UI; byte counters are read from the sessions as they stand. */
    fun snapshot(): SharingState = _state.value.copy(
        clients = sessions.values.map { it.toClient() }.sortedBy { it.sinceMillis },
    )

    @Synchronized
    fun start(port: Int = SharingState.DEFAULT_PORT): Boolean {
        if (server != null) return true
        return try {
            val socket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("0.0.0.0", port))
            }
            server = socket
            boundPort = socket.localPort
            acceptLoop(socket)
            _state.value = SharingState(running = true, port = boundPort)
            true
        } catch (e: IOException) {
            _state.value = SharingState(running = false, port = port, error = e.message)
            false
        }
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        sessions.values.forEach { runCatching { it.socket.close() } }
        sessions.clear()
        _state.value = SharingState(running = false, port = boundPort)
    }

    private fun acceptLoop(socket: ServerSocket) {
        Thread({
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: IOException) {
                    break
                }
                Thread({ serve(client) }, "share-client").apply { isDaemon = true }.start()
            }
        }, "share-accept").apply { isDaemon = true }.start()
    }

    private fun serve(client: Socket) {
        var session: Session? = null
        try {
            client.soTimeout = HANDSHAKE_TIMEOUT_MS
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())

            input.mark(1)
            val first = input.read()
            input.reset()
            if (first < 0) return

            if (first == 0x05) {
                session = register(client, "SOCKS5", null)
                serveSocks(client, input, output, session)
            } else {
                val head = readHead(input) ?: return
                val parsed = HttpProxyRequest.parse(head)
                if (parsed == null) {
                    writeHttpError(output, 400, "Bad Request")
                    return
                }
                session = register(client, "HTTP", parsed.userAgent)
                serveHttp(client, input, output, head, parsed, session)
            }
        } catch (_: IOException) {
            // a guest that walked away is not an error worth reporting
        } finally {
            session?.let {
                sessions.remove(it.id)
                _state.value = _state.value.copy()
            }
            runCatching { client.close() }
        }
    }

    private fun register(client: Socket, protocol: String, userAgent: String?): Session {
        val remote = client.inetAddress
        val session = Session(
            id = nextId.getAndIncrement(),
            socket = client,
            address = remote?.hostAddress ?: "unknown",
            port = client.port,
            protocol = protocol,
            userAgent = userAgent,
            os = ClientOs.fromUserAgent(userAgent),
            since = System.currentTimeMillis(),
        )
        sessions[session.id] = session
        return session
    }

    // ---------------------------------------------------------------- SOCKS5

    private fun serveSocks(
        client: Socket,
        input: InputStream,
        output: OutputStream,
        session: Session,
    ) {
        val greeting = ByteArray(2)
        if (!input.readFully(greeting)) return
        if (greeting[0] != 5.toByte()) return
        val methods = ByteArray(greeting[1].toInt() and 0xff)
        if (!input.readFully(methods)) return
        // No authentication: the share is for a trusted home network, and saying so is honest.
        output.write(byteArrayOf(5, 0))
        output.flush()

        val head = ByteArray(4)
        if (!input.readFully(head)) return
        if (head[0] != 5.toByte()) return
        val addressLength = when (head[3].toInt() and 0xff) {
            1 -> 4
            4 -> 16
            3 -> {
                val length = ByteArray(1)
                if (!input.readFully(length)) return
                length[0].toInt() and 0xff
            }
            else -> return
        }
        val request = head + ByteArray(addressLength + 2).also { if (!input.readFully(it)) return }

        if (head[1] != 1.toByte()) {
            output.write(byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0)) // command not supported
            output.flush()
            return
        }

        val upstreamSocket = openUpstream()
        if (upstreamSocket == null) {
            output.write(byteArrayOf(5, 1, 0, 1, 0, 0, 0, 0, 0, 0)) // general failure
            output.flush()
            return
        }
        try {
            // Hand the guest's own CONNECT straight to the tunnel, then relay byte for byte.
            upstreamSocket.getOutputStream().run {
                write(byteArrayOf(5, 1, 0))
                flush()
            }
            val upstreamGreeting = ByteArray(2)
            if (!upstreamSocket.getInputStream().readFully(upstreamGreeting)) return
            upstreamSocket.getOutputStream().run {
                write(request)
                flush()
            }
            val reply = ByteArray(4)
            if (!upstreamSocket.getInputStream().readFully(reply)) return
            val replyLength = when (reply[3].toInt() and 0xff) {
                1 -> 4
                4 -> 16
                3 -> {
                    val length = ByteArray(1)
                    if (!upstreamSocket.getInputStream().readFully(length)) return
                    length[0].toInt() and 0xff
                }
                else -> return
            }
            val replyTail = ByteArray(replyLength + 2)
            if (!upstreamSocket.getInputStream().readFully(replyTail)) return

            output.write(reply + replyTail)
            output.flush()
            relay(client, upstreamSocket, session)
        } finally {
            runCatching { upstreamSocket.close() }
        }
    }

    // ------------------------------------------------------------------ HTTP

    private fun serveHttp(
        client: Socket,
        input: InputStream,
        output: OutputStream,
        head: String,
        parsed: HttpProxyRequest.Parsed,
        session: Session,
    ) {
        val upstreamSocket = openUpstream()
        if (upstreamSocket == null) {
            writeHttpError(output, 502, "Tunnel is not connected")
            return
        }
        try {
            if (!socksConnect(upstreamSocket, parsed.host, parsed.port)) {
                writeHttpError(output, 502, "Upstream refused")
                return
            }

            if (parsed.originPath == null) {
                output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                output.flush()
                relay(client, upstreamSocket, session)
                return
            }

            // Plain http: rewrite the absolute URL to origin form and forward once. The
            // connection is closed afterwards so a reused socket can never reach a second host.
            val lines = head.trimEnd().split("\r\n")
                .filterNot { it.startsWith("Proxy-", ignoreCase = true) }
                .filterNot { it.startsWith("Connection:", ignoreCase = true) }
                .toMutableList()
            lines[0] = "${parsed.method} ${parsed.originPath} HTTP/1.1"
            val rewritten = lines.joinToString("\r\n") + "\r\nConnection: close\r\n\r\n"
            upstreamSocket.getOutputStream().run {
                write(rewritten.toByteArray())
                flush()
            }
            relay(client, upstreamSocket, session)
        } finally {
            runCatching { upstreamSocket.close() }
        }
    }

    private fun writeHttpError(output: OutputStream, code: Int, message: String) {
        runCatching {
            output.write(
                ("HTTP/1.1 $code $message\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                    .toByteArray()
            )
            output.flush()
        }
    }

    // --------------------------------------------------------------- plumbing

    private fun openUpstream(): Socket? {
        val endpoint = upstream() ?: return null
        return try {
            Socket().apply {
                connect(InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS)
                soTimeout = 0
            }
        } catch (_: IOException) {
            null
        }
    }

    /** SOCKS5 CONNECT to the tunnel, addressing by name so the core resolves inside it. */
    private fun socksConnect(socket: Socket, host: String, port: Int): Boolean {
        val out = socket.getOutputStream()
        val input = socket.getInputStream()
        out.write(byteArrayOf(5, 1, 0))
        out.flush()
        val greeting = ByteArray(2)
        if (!input.readFully(greeting) || greeting[1] != 0.toByte()) return false

        val name = host.toByteArray(Charsets.US_ASCII)
        val request = byteArrayOf(5, 1, 0, 3, name.size.toByte()) + name +
            byteArrayOf((port shr 8).toByte(), port.toByte())
        out.write(request)
        out.flush()

        val reply = ByteArray(4)
        if (!input.readFully(reply) || reply[1] != 0.toByte()) return false
        val length = when (reply[3].toInt() and 0xff) {
            1 -> 4
            4 -> 16
            3 -> {
                val l = ByteArray(1)
                if (!input.readFully(l)) return false
                l[0].toInt() and 0xff
            }
            else -> return false
        }
        return input.readFully(ByteArray(length + 2))
    }

    private fun relay(guest: Socket, upstreamSocket: Socket, session: Session) {
        runCatching { guest.soTimeout = 0 }
        runCatching { upstreamSocket.soTimeout = 0 }

        val uplink = Thread(
            { pump(guest.getInputStream(), upstreamSocket.getOutputStream(), session.up) },
            "share-up",
        ).apply { isDaemon = true }
        uplink.start()

        pump(upstreamSocket.getInputStream(), guest.getOutputStream(), session.down)

        runCatching { guest.close() }
        runCatching { upstreamSocket.close() }
        runCatching { uplink.join(1_000) }
    }

    private fun pump(input: InputStream, output: OutputStream, counter: AtomicLong) {
        val buffer = ByteArray(32 * 1024)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                output.flush()
                counter.addAndGet(read.toLong())
            }
        } catch (_: IOException) {
            // either side closing is the normal end of a proxied connection
        }
    }

    private fun readHead(input: InputStream): String? {
        val builder = StringBuilder()
        var lastFour = 0
        while (builder.length < MAX_HEAD_BYTES) {
            val byte = input.read()
            if (byte < 0) return null
            builder.append(byte.toChar())
            lastFour = (lastFour shl 8) or byte
            if (lastFour == 0x0d0a0d0a) return builder.toString()
        }
        return null
    }

    private companion object {
        const val HANDSHAKE_TIMEOUT_MS = 15_000
        const val CONNECT_TIMEOUT_MS = 8_000
        const val MAX_HEAD_BYTES = 16 * 1024
    }
}
