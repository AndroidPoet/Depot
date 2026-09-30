package dev.androidpoet.depot

import dev.androidpoet.depot.catalog.BrowseQuery
import dev.androidpoet.depot.catalog.DeviceProfile
import dev.androidpoet.depot.catalog.InstalledApp
import dev.androidpoet.depot.catalog.SortOrder
import dev.androidpoet.depot.catalog.browse
import dev.androidpoet.depot.catalog.categoryCounts
import dev.androidpoet.depot.catalog.suggestedVersion
import dev.androidpoet.depot.catalog.updateAvailable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogRulesTest {
    private val arm64Phone = DeviceProfile(sdk = 34, abis = listOf("arm64-v8a", "armeabi-v7a"))

    @Test
    fun test_suggestedVersion_stableAndBeta_picksHighestStable() {
        val subject = app("a", versions = listOf(version(30, beta = true), version(20), version(10)))

        assertEquals(20, subject.suggestedVersion(arm64Phone)?.versionCode)
    }

    @Test
    fun test_suggestedVersion_onlyBeta_picksHighestBeta() {
        val subject = app("a", versions = listOf(version(30, beta = true), version(20, beta = true)))

        assertEquals(30, subject.suggestedVersion(arm64Phone)?.versionCode)
    }

    @Test
    fun test_suggestedVersion_abiSplit_picksDeviceAbi() {
        val subject = app(
            "a",
            versions = listOf(
                version(104, name = "5.4", abis = listOf("x86_64")),
                version(103, name = "5.4", abis = listOf("arm64-v8a")),
                version(102, name = "5.4", abis = listOf("x86")),
            ),
        )

        assertEquals(103, subject.suggestedVersion(arm64Phone)?.versionCode)
    }

    @Test
    fun test_suggestedVersion_bridgedAbi_prefersNativeBuild() {
        val x86Tablet = DeviceProfile(sdk = 34, abis = listOf("x86_64", "arm64-v8a"))
        val subject = app(
            "a",
            versions = listOf(
                version(104, name = "5.4", abis = listOf("arm64-v8a")),
                version(103, name = "5.4", abis = listOf("x86_64")),
                version(94, name = "5.3", abis = listOf("x86_64")),
            ),
        )

        assertEquals(103, subject.suggestedVersion(x86Tablet)?.versionCode)
    }

    @Test
    fun test_suggestedVersion_targetSdkBelowDeviceFloor_returnsNull() {
        val android15 = DeviceProfile(sdk = 35, abis = listOf("arm64-v8a"), minTargetSdk = 24)
        val subject = app("a", versions = listOf(version(5, targetSdk = 23)))

        assertNull(subject.suggestedVersion(android15))
    }

    @Test
    fun test_suggestedVersion_maxSdkBelowDevice_returnsNull() {
        val subject = app("a", versions = listOf(version(5, maxSdk = 30)))

        assertNull(subject.suggestedVersion(arm64Phone))
    }

    @Test
    fun test_suggestedVersion_minSdkTooHigh_returnsNull() {
        val subject = app("a", versions = listOf(version(5, minSdk = 35)))

        assertNull(subject.suggestedVersion(arm64Phone))
    }

    @Test
    fun test_suggestedVersion_noDevice_picksHighestStable() {
        val subject = app("a", versions = listOf(version(9, beta = true), version(8, minSdk = 99, abis = listOf("mips"))))

        assertEquals(8, subject.suggestedVersion(device = null)?.versionCode)
    }

    @Test
    fun test_suggestedVersion_installedWithOtherSigner_skipsIncompatibleSigner() {
        val subject = app("a", versions = listOf(version(3, signer = "aa"), version(2, signer = "bb")))

        assertEquals(2, subject.suggestedVersion(arm64Phone, InstalledApp(versionCode = 1, signer = "bb"))?.versionCode)
    }

    @Test
    fun test_suggestedVersion_notInstalled_prefersPreferredSigner() {
        val subject = app("a", preferredSigner = "bb", versions = listOf(version(3, signer = "aa"), version(2, signer = "bb")))

        assertEquals(2, subject.suggestedVersion(arm64Phone)?.versionCode)
    }

    @Test
    fun test_updateAvailable_installedOlder_true() {
        val subject = app("a", versions = listOf(version(3), version(2)))

        assertTrue(subject.updateAvailable(InstalledApp(2, signer = null), arm64Phone))
    }

    @Test
    fun test_updateAvailable_installedSame_false() {
        val subject = app("a", versions = listOf(version(3)))

        assertFalse(subject.updateAvailable(InstalledApp(3, signer = null), arm64Phone))
    }

    @Test
    fun test_updateAvailable_notInstalled_false() {
        val subject = app("a", versions = listOf(version(3)))

        assertFalse(subject.updateAvailable(installed = null, device = arm64Phone))
    }

    @Test
    fun test_updateAvailable_newerVersionHasOtherSigner_false() {
        val subject = app("a", versions = listOf(version(3, signer = "aa"), version(2, signer = "bb")))

        assertFalse(subject.updateAvailable(InstalledApp(2, signer = "bb"), arm64Phone))
    }

    private val shelf = listOf(
        app("org.notes", name = "Quill", summary = "Plain text notes", categories = listOf("Writing"), added = 1, lastUpdated = 30),
        app("org.maps", name = "atlas", summary = "Offline maps", categories = listOf("Navigation"), added = 3, lastUpdated = 10),
        app("org.quillwork", name = "Inkpot", summary = "Drawing", categories = listOf("Writing", "Graphics"), added = 2, lastUpdated = 20),
        app("org.feeds", name = "Tranquil", summary = "A feed reader", categories = listOf("Reading"), added = 4, lastUpdated = 40),
    )

    @Test
    fun test_search_nameSummaryPackage_matchCaseInsensitive() {
        assertEquals(setOf("org.maps"), shelf.browse(BrowseQuery(text = "OFFLINE")).map { it.packageName }.toSet())
        assertEquals(setOf("org.maps"), shelf.browse(BrowseQuery(text = "Atlas")).map { it.packageName }.toSet())
        assertEquals(setOf("org.feeds"), shelf.browse(BrowseQuery(text = "org.FEEDS")).map { it.packageName }.toSet())
        assertEquals(emptyList(), shelf.browse(BrowseQuery(text = "zzz")))
    }

    @Test
    fun test_search_namePrefix_ranksFirst() {
        val found = shelf.browse(BrowseQuery(text = "quil"))

        assertEquals(listOf("org.notes", "org.feeds", "org.quillwork"), found.map { it.packageName })
    }

    @Test
    fun test_search_blankText_returnsEverything() {
        assertEquals(4, shelf.browse(BrowseQuery(text = "   ")).size)
    }

    @Test
    fun test_filter_category_returnsOnlyThatCategory() {
        val found = shelf.browse(BrowseQuery(category = "Writing"))

        assertEquals(listOf("org.notes", "org.quillwork"), found.map { it.packageName })
    }

    @Test
    fun test_sort_updated_isNewestUpdateFirst() {
        assertEquals(listOf(40L, 30L, 20L, 10L), shelf.browse(BrowseQuery()).map { it.lastUpdated })
    }

    @Test
    fun test_sort_newest_isLatestAddedFirst() {
        assertEquals(listOf(4L, 3L, 2L, 1L), shelf.browse(BrowseQuery(sort = SortOrder.Newest)).map { it.added })
    }

    @Test
    fun test_sort_name_isAlphabeticalIgnoringCase() {
        assertEquals(listOf("atlas", "Inkpot", "Quill", "Tranquil"), shelf.browse(BrowseQuery(sort = SortOrder.Name)).map { it.name })
    }

    @Test
    fun test_categoryCounts_shelf_countsAlphabetically() {
        assertEquals(listOf("Graphics" to 1, "Navigation" to 1, "Reading" to 1, "Writing" to 2), shelf.categoryCounts())
    }
}
