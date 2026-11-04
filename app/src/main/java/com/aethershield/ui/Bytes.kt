package com.aethershield.ui

import java.util.Locale

/** Human readable byte count. Shared by the traffic cards and the sharing screen. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[i])
}

fun formatRate(bytesPerSecond: Long): String = formatBytes(bytesPerSecond) + "/s"
