package com.aethershield.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionMathTest {

    @Test
    fun aHigherVersionIsNewer() {
        assertTrue(VersionMath.isNewer("1.2", "1.1"))
        assertTrue(VersionMath.isNewer("2.0", "1.9.9"))
        assertFalse(VersionMath.isNewer("1.1", "1.2"))
        assertFalse(VersionMath.isNewer("1.2", "1.2"))
    }

    @Test
    fun theTagPrefixIsIgnored() {
        assertTrue(VersionMath.isNewer("v1.3", "1.2"))
        assertEquals(0, VersionMath.compare("V1.2.0", "1.2"))
    }

    @Test
    fun missingComponentsCountAsZero() {
        assertEquals(0, VersionMath.compare("1.2", "1.2.0"))
        assertEquals(0, VersionMath.compare("1", "1.0.0"))
        assertTrue(VersionMath.isNewer("1.2.1", "1.2"))
    }

    @Test
    fun comparisonIsNumericNotLexicographic() {
        // "1.10" is newer than "1.9"; a string comparison would say the opposite.
        assertTrue(VersionMath.isNewer("1.10", "1.9"))
        assertEquals(1, VersionMath.compare("1.10.0", "1.9.9"))
    }

    @Test
    fun suffixesAndJunkDoNotConfuseIt() {
        assertEquals(0, VersionMath.compare("1.2.0-beta", "1.2"))
        assertEquals(0, VersionMath.compare("", "0"))
    }

    @Test
    fun aMalformedReleaseVersionNeverLooksNewerThanTheInstalledOne() {
        assertFalse(VersionMath.isNewer("", "1.2"))
        assertFalse(VersionMath.isNewer("latest", "1.2"))
    }
}
