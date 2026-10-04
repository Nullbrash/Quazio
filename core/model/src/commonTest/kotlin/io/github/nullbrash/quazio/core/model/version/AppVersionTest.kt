package io.github.nullbrash.quazio.core.model.version

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppVersionTest {

    @Test
    fun releaseParsesAndRoundTrips() {
        val v = AppVersion.parse("1.2.3")
        assertEquals(AppVersion(1, 2, 3, AppVersion.Stage.RELEASE, 0), v)
        assertEquals("1.2.3", v.name)
        assertEquals("1.2.3", v.numericName)
        assertEquals(1_020_399, v.versionCode)
        assertFalse(v.isPrerelease)
    }

    @Test
    fun prereleasesParseAndRoundTrip() {
        val beta = AppVersion.parse("1.2.0-beta.3")
        assertEquals("1.2.0-beta.3", beta.name)
        assertEquals("1.2.0", beta.numericName)
        assertEquals(1_020_003, beta.versionCode)
        assertTrue(beta.isPrerelease)

        val updtest = AppVersion.parse("1.2.0-updtest.1")
        assertEquals("1.2.0-updtest.1", updtest.name)
        assertEquals(1_020_090, updtest.versionCode)
        assertTrue(updtest.isPrerelease)
    }

    @Test
    fun versionCodeGrowsInVersionOrder() {
        val ordered = listOf(
            "0.1.0",
            "0.1.1-beta.1",
            "0.1.1-beta.89",
            "0.1.1-updtest.1",
            "0.1.1-updtest.9",
            "0.1.1",
            "0.1.99",
            "0.2.0-beta.1",
            "0.99.99",
            "1.0.0-beta.1",
            "1.0.0",
            "255.99.99",
        ).map(AppVersion::parse)

        ordered.zipWithNext { older, newer ->
            assertTrue(older.versionCode < newer.versionCode, "$older должна быть младше $newer")
            assertTrue(older < newer, "$older должна быть младше $newer")
        }
    }

    @Test
    fun maximalVersionFitsAndroidLimit() {
        // Android принимает versionCode не больше 2 100 000 000.
        assertTrue(AppVersion.parse("255.99.99").versionCode <= 2_100_000_000)
    }

    @Test
    fun rejectsMalformedVersions() {
        val bad = listOf(
            "",
            "1.2",
            "1.2.3.4",
            "v1.2.3",
            "01.2.3",
            "1.02.3",
            "1.2.3-rc.1",
            "1.2.3-beta",
            "1.2.3-beta.0",
            "1.2.3-beta.01",
            "1.2.3-beta.90",
            "1.2.3-updtest.10",
            "1.100.0",
            "1.2.100",
            "256.0.0",
        )
        for (text in bad) {
            assertFailsWith<IllegalArgumentException>("должна быть отвергнута: \"$text\"") {
                AppVersion.parse(text)
            }
        }
    }
}
