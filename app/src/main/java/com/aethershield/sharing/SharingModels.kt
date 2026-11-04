package com.aethershield.sharing

/** Which kind of device is behind a shared connection, guessed from its User-Agent. */
enum class ClientOs {
    ANDROID, IOS, WINDOWS, MACOS, LINUX, UNKNOWN;

    companion object {
        /**
         * Order matters: "Android" appears inside Chrome-on-Android strings that also say
         * "Linux", and iOS user agents also say "Mac OS X".
         */
        fun fromUserAgent(userAgent: String?): ClientOs {
            val ua = userAgent?.lowercase() ?: return UNKNOWN
            return when {
                "android" in ua -> ANDROID
                "iphone" in ua || "ipad" in ua || "ipod" in ua -> IOS
                "windows" in ua -> WINDOWS
                "macintosh" in ua || "mac os x" in ua -> MACOS
                "linux" in ua || "x11" in ua -> LINUX
                else -> UNKNOWN
            }
        }
    }
}

/** One device using the shared proxy right now. */
data class SharedClient(
    val id: Long,
    val address: String,
    val port: Int,
    val protocol: String,
    val os: ClientOs,
    val userAgent: String?,
    val sinceMillis: Long,
    val upBytes: Long,
    val downBytes: Long,
)

/** What the sharing screen shows. */
data class SharingState(
    val running: Boolean = false,
    val port: Int = DEFAULT_PORT,
    val error: String? = null,
    val clients: List<SharedClient> = emptyList(),
) {
    companion object {
        /** One port for both protocols: the first byte tells SOCKS5 (0x05) from HTTP. */
        const val DEFAULT_PORT = 10810
    }
}

/**
 * An HTTP proxy request line plus the headers we care about.
 *
 * Two forms reach us: `CONNECT host:443` (HTTPS, tunnelled raw) and an absolute URL
 * (`GET http://host/path`), which has to be rewritten to origin form before it is forwarded.
 */
internal object HttpProxyRequest {

    data class Parsed(
        val method: String,
        val host: String,
        val port: Int,
        /** Path plus query for the origin-form rewrite; null for CONNECT. */
        val originPath: String?,
        val userAgent: String?,
    )

    fun parse(head: String): Parsed? {
        val lines = head.split("\r\n")
        val requestLine = lines.firstOrNull()?.trim().orEmpty()
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        val target = parts[1]

        val userAgent = lines.drop(1)
            .firstOrNull { it.startsWith("user-agent:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()

        return if (method == "CONNECT") {
            val (host, port) = splitHostPort(target, defaultPort = 443) ?: return null
            Parsed(method, host, port, originPath = null, userAgent = userAgent)
        } else {
            val url = target.removePrefix("http://")
            if (url == target) return null // only plain http can be rewritten
            val slash = url.indexOf('/')
            val authority = if (slash < 0) url else url.substring(0, slash)
            val path = if (slash < 0) "/" else url.substring(slash)
            val (host, port) = splitHostPort(authority, defaultPort = 80) ?: return null
            Parsed(method, host, port, originPath = path, userAgent = userAgent)
        }
    }

    private fun splitHostPort(value: String, defaultPort: Int): Pair<String, Int>? {
        val colon = value.lastIndexOf(':')
        val host = if (colon > 0) value.substring(0, colon) else value
        if (host.isBlank() || host.any { it.isWhitespace() }) return null
        val port = if (colon > 0) value.substring(colon + 1).toIntOrNull() ?: return null else defaultPort
        return host to port
    }
}

/** Reads exactly [size] bytes; [java.io.InputStream.read] is allowed to return short. */
internal fun java.io.InputStream.readFully(buffer: ByteArray, size: Int = buffer.size): Boolean {
    var read = 0
    while (read < size) {
        val n = read(buffer, read, size - read)
        if (n <= 0) return false
        read += n
    }
    return true
}
