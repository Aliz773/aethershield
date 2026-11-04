package com.aethershield.sharing

import com.aethershield.core.EngineEndpoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * End-to-end check of the share proxy: a guest connects over SOCKS5 and over HTTP CONNECT,
 * and the bytes really reach a target socket through a stand-in for the tunnel.
 *
 * The proxy is plain java.net code, so this runs on the JVM with no phone involved.
 */
class SharingServerTest {

    private lateinit var echo: ServerSocket
    private lateinit var socks: ServerSocket
    private lateinit var sharing: SharingServer

    private val sharePort: Int get() = sharing.snapshot().port

    @Before
    fun setUp() {
        echo = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) { acceptLoop(echo) { echoClient(it) } }

        socks = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) { acceptLoop(socks) { fakeSocksUpstream(it) } }

        sharing = SharingServer { EngineEndpoint("127.0.0.1", socks.localPort) }
        assertTrue("share server must bind", sharing.start(0))
    }

    @After
    fun tearDown() {
        sharing.stop()
        runCatching { echo.close() }
        runCatching { socks.close() }
    }

    private fun acceptLoop(server: ServerSocket, handler: (Socket) -> Unit) {
        while (!server.isClosed) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            thread(isDaemon = true) { runCatching { handler(client) } }
        }
    }

    /** Echo target: whatever a guest sends comes straight back. */
    private fun echoClient(socket: Socket) {
        socket.use {
            val buffer = ByteArray(4096)
            while (true) {
                val read = it.getInputStream().read(buffer)
                if (read <= 0) break
                it.getOutputStream().write(buffer, 0, read)
                it.getOutputStream().flush()
            }
        }
    }

    /** Stands in for the Aether core: accepts one SOCKS5 CONNECT and pipes to the target. */
    private fun fakeSocksUpstream(client: Socket) {
        client.use { guest ->
            val input = guest.getInputStream()
            val output = guest.getOutputStream()

            val greeting = ByteArray(2)
            assertTrue(input.readFully(greeting))
            assertTrue(input.readFully(ByteArray(greeting[1].toInt())))
            output.write(byteArrayOf(5, 0))
            output.flush()

            val head = ByteArray(4)
            assertTrue(input.readFully(head))
            val host = when (head[3].toInt() and 0xff) {
                1 -> {
                    val raw = ByteArray(4)
                    input.readFully(raw)
                    InetAddress.getByAddress(raw).hostAddress.orEmpty()
                }
                3 -> {
                    val length = ByteArray(1)
                    input.readFully(length)
                    val raw = ByteArray(length[0].toInt())
                    input.readFully(raw)
                    String(raw)
                }
                else -> {
                    val raw = ByteArray(16)
                    input.readFully(raw)
                    InetAddress.getByAddress(raw).hostAddress.orEmpty()
                }
            }
            val portBytes = ByteArray(2)
            input.readFully(portBytes)
            val port = ((portBytes[0].toInt() and 0xff) shl 8) or (portBytes[1].toInt() and 0xff)

            val target = Socket()
            target.connect(InetSocketAddress(host, port), 2_000)
            output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            output.flush()

            val back = thread(isDaemon = true) {
                runCatching {
                    target.getInputStream().copyTo(output)
                    output.flush()
                }
                runCatching { target.close() }
            }
            runCatching {
                input.copyTo(target.getOutputStream())
                target.getOutputStream().flush()
            }
            runCatching { target.close() }
            back.join(1_000)
        }
    }

    private fun readHead(input: java.io.InputStream): String {
        val builder = StringBuilder()
        while (!builder.endsWith("\r\n\r\n")) {
            val byte = input.read()
            if (byte < 0) break
            builder.append(byte.toChar())
        }
        return builder.toString()
    }

    @Test
    fun aSocks5GuestReachesTheTargetAndIsListed() {
        Socket("127.0.0.1", sharePort).use { guest ->
            guest.soTimeout = 5_000
            val out = guest.getOutputStream()
            val input = guest.getInputStream()

            out.write(byteArrayOf(5, 1, 0))
            out.flush()
            val greeting = ByteArray(2)
            assertTrue(input.readFully(greeting))
            assertEquals("no auth offered", 0, greeting[1].toInt())

            val address = InetAddress.getByName("127.0.0.1").address
            val port = echo.localPort
            out.write(
                byteArrayOf(5, 1, 0, 1) + address +
                    byteArrayOf((port shr 8).toByte(), port.toByte())
            )
            out.flush()
            val reply = ByteArray(10)
            assertTrue(input.readFully(reply))
            assertEquals("CONNECT granted", 0, reply[1].toInt())

            out.write("hello".toByteArray())
            out.flush()
            val echoed = ByteArray(5)
            assertTrue(input.readFully(echoed))
            assertEquals("hello", String(echoed))

            val clients = sharing.snapshot().clients
            assertEquals(1, clients.size)
            assertEquals("SOCKS5", clients[0].protocol)
            assertEquals(ClientOs.UNKNOWN, clients[0].os)
            assertTrue("bytes are counted", clients[0].upBytes >= 5 && clients[0].downBytes >= 5)
        }
    }

    @Test
    fun anHttpGuestConnectsAndItsOperatingSystemIsGuessed() {
        Socket("127.0.0.1", sharePort).use { guest ->
            guest.soTimeout = 5_000
            val out = guest.getOutputStream()
            val input = guest.getInputStream()

            val target = "127.0.0.1:${echo.localPort}"
            val request = "CONNECT $target HTTP/1.1\r\n" +
                "Host: $target\r\n" +
                "User-Agent: Mozilla/5.0 (Linux; Android 13; Pixel 7) Chrome/119\r\n\r\n"
            out.write(request.toByteArray())
            out.flush()

            val head = readHead(input)
            assertTrue("expected 200, got: $head", head.contains("200 Connection Established"))

            out.write("ping".toByteArray())
            out.flush()
            val echoed = ByteArray(4)
            assertTrue(input.readFully(echoed))
            assertEquals("ping", String(echoed))

            val clients = sharing.snapshot().clients
            assertEquals(1, clients.size)
            assertEquals("HTTP", clients[0].protocol)
            assertEquals(ClientOs.ANDROID, clients[0].os)
        }
    }

    @Test
    fun withoutATunnelTheGuestIsToldTheUpstreamIsGone() {
        val offline = SharingServer { null }
        assertTrue(offline.start(0))
        try {
            Socket("127.0.0.1", offline.snapshot().port).use { guest ->
                guest.soTimeout = 5_000
                guest.getOutputStream().write("CONNECT example.com:443 HTTP/1.1\r\n\r\n".toByteArray())
                guest.getOutputStream().flush()
                val status = guest.getInputStream().bufferedReader().readLine()
                assertTrue("expected 502, got: $status", status.contains("502"))
            }
        } finally {
            offline.stop()
        }
    }

    @Test
    fun stoppingTheShareClosesTheListener() {
        val port = sharePort
        sharing.stop()

        assertTrue("not running after stop", !sharing.snapshot().running)
        val refused = runCatching { Socket("127.0.0.1", port).close() }.isFailure
        assertTrue("the port must stop accepting", refused)
    }
}
