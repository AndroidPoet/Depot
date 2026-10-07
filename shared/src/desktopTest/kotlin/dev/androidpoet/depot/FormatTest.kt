package dev.androidpoet.depot

import dev.androidpoet.depot.ui.androidRelease
import dev.androidpoet.depot.ui.byteSize
import dev.androidpoet.depot.ui.grouped
import dev.androidpoet.depot.ui.htmlToPlain
import dev.androidpoet.depot.ui.relativeTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    private val hour = 3_600_000L
    private val day = 24 * hour

    @Test
    fun test_relativeTime_eachScale_usesLargestWholeUnit() {
        assertEquals("just now", relativeTime(then = 1_000, now = 30_000))
        assertEquals("5m ago", relativeTime(then = 0, now = 5 * 60_000))
        assertEquals("2h ago", relativeTime(then = 0, now = 2 * hour + 5))
        assertEquals("3d ago", relativeTime(then = 0, now = 3 * day))
        assertEquals("4mo ago", relativeTime(then = 0, now = 125 * day))
        assertEquals("3y ago", relativeTime(then = 0, now = 3 * 365 * day + day))
    }

    @Test
    fun test_relativeTime_futureTimestamp_isJustNow() {
        assertEquals("just now", relativeTime(then = 10 * day, now = day))
    }

    @Test
    fun test_byteSize_eachScale_hasOneDecimal() {
        assertEquals("512 B", byteSize(512))
        assertEquals("9.6 kB", byteSize(9_649))
        assertEquals("12.5 MB", byteSize(12_537_183))
        assertEquals("1.2 GB", byteSize(1_200_000_000))
    }

    @Test
    fun test_grouped_thousands_areSeparated() {
        assertEquals("4,486", grouped(4486))
        assertEquals("12", grouped(12))
        assertEquals("1,000,000", grouped(1_000_000))
    }

    @Test
    fun test_androidRelease_knownLevels_mapToVersion() {
        assertEquals("7.0", androidRelease(24))
        assertEquals("12", androidRelease(32))
        assertEquals("14", androidRelease(34))
    }

    @Test
    fun test_androidRelease_levelsBeforeLollipop_mapToVersion() {
        assertEquals("1.0", androidRelease(1))
        assertEquals("2.3", androidRelease(9))
        assertEquals("4.0", androidRelease(14))
        assertEquals("4.1", androidRelease(16))
        assertEquals("4.4", androidRelease(19))
    }

    @Test
    fun test_htmlToPlain_paragraphsAndLists_becomeLines() {
        val html = "<p>First &amp; <b>bold</b>.</p><ul><li>One</li><li>Two</li></ul><p>Last<br>line</p>"

        assertEquals("First & bold.\n• One\n• Two\n\nLast\nline", htmlToPlain(html))
    }
}
