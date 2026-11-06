package com.aethershield.update

import android.content.Context

/** Remembers the last release GitHub reported, so the UI can show it without a network call. */
object UpdateStore {

    private const val PREFS = "aethershield_updates"
    private const val KEY_VERSION = "latest_version"
    private const val KEY_NOTES = "latest_notes"
    private const val KEY_URL = "latest_url"
    private const val KEY_APK = "latest_apk"
    private const val KEY_CHECKED = "checked_at"

    data class Known(
        val version: String,
        val notes: String,
        val releaseUrl: String,
        val apkUrl: String?,
        val checkedAt: Long,
    )

    fun save(context: Context, info: UpdateInfo) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_VERSION, info.version)
            .putString(KEY_NOTES, info.notes)
            .putString(KEY_URL, info.releaseUrl)
            .putString(KEY_APK, info.apkUrl)
            .putLong(KEY_CHECKED, System.currentTimeMillis())
            .apply()
    }

    fun load(context: Context): Known? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = prefs.getString(KEY_VERSION, null) ?: return null
        return Known(
            version = version,
            notes = prefs.getString(KEY_NOTES, "").orEmpty(),
            releaseUrl = prefs.getString(KEY_URL, "").orEmpty(),
            apkUrl = prefs.getString(KEY_APK, null),
            checkedAt = prefs.getLong(KEY_CHECKED, 0L),
        )
    }

    /** True when the remembered release is newer than [installedVersion]. */
    fun hasUpdate(context: Context, installedVersion: String): Known? =
        load(context)?.takeIf { VersionMath.isNewer(it.version, installedVersion) }
}
