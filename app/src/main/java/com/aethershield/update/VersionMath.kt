package com.aethershield.update

/**
 * Version comparison for the update checker. Pure, so it can be tested without a network.
 *
 * Tags on GitHub look like `v1.2` or `1.2.0`; both are accepted, and a missing component
 * counts as zero so `1.2` and `1.2.0` are the same release.
 */
object VersionMath {

    fun isNewer(candidate: String, installed: String): Boolean = compare(candidate, installed) > 0

    fun compare(a: String, b: String): Int {
        val left = parts(a)
        val right = parts(b)
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return if (l > r) 1 else -1
        }
        return 0
    }

    private fun parts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split('.', '-', '+')
            .map { token -> token.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
