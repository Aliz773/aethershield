package com.aethershield.core

/** What the internet sees while the tunnel is up. */
data class ExitInfo(
    val ip: String,
    val countryCode: String,
    val colo: String? = null,
)

/**
 * Blocking lookup of the exit address, called off the main thread. Null means "could not
 * be learned right now" -- it may simply mean the tunnel is not carrying yet.
 */
fun interface ExitFinder {
    fun find(proxyHost: String, proxyPort: Int): ExitInfo?
}

/**
 * Reads Cloudflare's `/cdn-cgi/trace`: a plain `key=value` page served by the exit itself,
 * so `ip=` is the address the world sees. The Aether core uses the same endpoint for its
 * `--exit-loc` check, which is why it is known to be reachable from inside the tunnel.
 */
object ExitTrace {

    fun parse(body: String): ExitInfo? {
        var ip: String? = null
        var country: String? = null
        var colo: String? = null

        for (line in body.lineSequence()) {
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            when (key) {
                "ip" -> ip = value
                "loc" -> country = value
                "colo" -> colo = value
            }
        }

        if (ip.isNullOrBlank() || country.isNullOrBlank()) return null
        return ExitInfo(
            ip = ip,
            countryCode = country.uppercase(),
            colo = colo?.ifBlank { null },
        )
    }
}
