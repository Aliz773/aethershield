package com.aethershield.update

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A published release that is newer than what is installed. */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val releaseUrl: String,
    val apkUrl: String?,
)

/**
 * Reads the project's published releases from the GitHub API.
 *
 * The repository owner and name are a constant because the APK has to know where to look
 * without any configuration. If the repository is renamed, these two values are the only
 * thing to change.
 */
object UpdateSource {

    const val OWNER = "Aliz773"
    const val REPO = "AetherShield"

    private const val ENDPOINT = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val TIMEOUT_MS = 8_000

    /** Blocking; call from a background dispatcher or a Worker. */
    fun latest(): UpdateInfo? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "AetherShield")
            }
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            // A malformed answer must never crash the app; it just means "no update known".
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    internal fun parse(body: String): UpdateInfo? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        // Drafts never reach this endpoint; a prerelease is not something to push on users.
        if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) return null

        val tag = json.optString("tag_name").ifBlank { return null }
        val url = json.optString("html_url")
        val notes = json.optString("body")
        val apk = json.optJSONArray("assets")
            ?.let { assets ->
                (0 until assets.length())
                    .map { assets.optJSONObject(it) }
                    .firstOrNull { it?.optString("name")?.endsWith(".apk", ignoreCase = true) == true }
            }
            ?.optString("browser_download_url")
            ?.takeIf { it.isNotBlank() }

        return UpdateInfo(
            version = tag.trim().removePrefix("v").removePrefix("V"),
            notes = notes,
            releaseUrl = url,
            apkUrl = apk,
        )
    }
}
