package dev.androidpoet.depot

import dev.androidpoet.depot.catalog.Catalog
import dev.androidpoet.depot.repo.IndexReader
import dev.androidpoet.depot.repo.pickLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexReaderTest {
    private val catalog: Catalog by lazy { IndexReader.read(fixtureBytes("index-v2-sample.json").inputStream()) }

    private fun app(packageName: String) = catalog.apps.single { it.packageName == packageName }

    @Test
    fun test_readIndex_sample_returnsAllPackages() {
        assertEquals(1790718180755, catalog.timestamp)
        assertEquals(
            setOf(
                "org.fdroid.fdroid",
                "anonvpn.anon_next.android",
                "InfinityLoop1309.NewPipeEnhanced",
                "be.humanoids.webthingify",
                "com.ammar.sharing",
            ),
            catalog.apps.map { it.packageName }.toSet(),
        )
    }

    @Test
    fun test_readIndex_metadata_isMapped() {
        val fdroid = app("org.fdroid.fdroid")

        assertEquals("F-Droid", fdroid.name)
        assertEquals(listOf("App Store & Updater", "System"), fdroid.categories)
        assertEquals("GPL-3.0-or-later", fdroid.license)
        assertEquals("F-Droid", fdroid.authorName)
        assertEquals("https://gitlab.com/fdroid/fdroidclient", fdroid.sourceCode)
        assertEquals(1295222400000, fdroid.added)
        assertEquals(1790715170525, fdroid.lastUpdated)
        assertEquals(FDROID_FINGERPRINT, fdroid.preferredSigner)
        assertEquals(
            "https://f-droid.org/repo/org.fdroid.fdroid/en-US/icon_0slNR8J9edqDZDoyWABFZrqQONU9EyiNVwM6B3Jui_Q=.png",
            fdroid.iconUrl,
        )
        assertTrue(fdroid.description.isNotBlank())
    }

    @Test
    fun test_readIndex_localePick_prefersEnUsThenEnThenFirst() {
        assertEquals("Turn your Android device into a web thing (Mozilla IoT compatible)", app("be.humanoids.webthingify").summary)
        assertEquals("us", mapOf("de" to "de", "en-GB" to "gb", "en-US" to "us").pickLocale())
        assertEquals("gb", mapOf("de" to "de", "en-GB" to "gb").pickLocale())
        assertEquals("de", mapOf("de" to "de", "fr" to "fr").pickLocale())
        assertEquals("", emptyMap<String, String>().pickLocale())
    }

    @Test
    fun test_readIndex_appWithoutIcon_hasNullIconUrl() {
        assertNull(app("anonvpn.anon_next.android").iconUrl)
    }

    @Test
    fun test_readIndex_versions_sortedNewestFirstWithBetaFlag() {
        val versions = app("org.fdroid.fdroid").versions

        assertEquals(versions.map { it.versionCode }.sortedDescending(), versions.map { it.versionCode })
        assertEquals(10, versions.size)
        assertEquals(2000051, versions.first().versionCode)
        assertTrue(versions.first().beta)
        assertEquals(1023052, versions.last().versionCode)
        assertFalse(versions.last().beta)
    }

    @Test
    fun test_readIndex_versionFile_isMapped() {
        val newest = app("org.fdroid.fdroid").versions.first()

        assertEquals("2.0.1", newest.versionName)
        assertEquals("/org.fdroid.fdroid_2000051.apk", newest.apkPath)
        assertEquals("83d3fe522281c3cb89fce3bc05038f81b3c3b32c108536941f184c5f9bb53778", newest.sha256)
        assertEquals(12537183, newest.size)
        assertEquals(24, newest.minSdk)
        assertEquals(listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"), newest.abis)
        assertEquals(FDROID_FINGERPRINT, newest.signer)
    }

    @Test
    fun test_readIndex_universalApk_hasNoAbis() {
        assertEquals(emptyList(), app("be.humanoids.webthingify").versions.first().abis)
    }

    @Test
    fun test_readIndex_antiFeatures_areCarriedOnVersion() {
        assertEquals(listOf("NonFreeNet"), app("anonvpn.anon_next.android").versions.single().antiFeatures)
        assertEquals(listOf("NonFreeAssets"), app("com.ammar.sharing").versions.single { it.versionCode == 19L }.antiFeatures)
        assertEquals(emptyList(), app("com.ammar.sharing").versions.single { it.versionCode == 21L }.antiFeatures)
    }

    @Test
    fun test_readIndex_targetSdk_isMapped() {
        assertEquals(37, app("org.fdroid.fdroid").versions.first().targetSdk)
        assertNull(app("org.fdroid.fdroid").versions.first().maxSdk)
    }

    private fun inline(versionFile: String, manifest: String, metadata: String = "{}") = IndexReader.read(
        """{"repo":{"timestamp":5},"packages":{"org.x":{"metadata":$metadata,"versions":{"a":{"added":1,"file":$versionFile,"manifest":$manifest}}}}}""".byteInputStream(),
    )

    private val goodFile = """{"name":"/org.x_1.apk","sha256":"${"ab".repeat(32)}","size":9}"""

    @Test
    fun test_readIndex_missingName_usesPackageName() {
        val parsed = inline(goodFile, """{"versionName":"1","versionCode":1}""")

        assertEquals("org.x", parsed.apps.single().name)
    }

    @Test
    fun test_readIndex_missingUsesSdk_defaultsToOne() {
        val parsed = inline(goodFile, """{"versionName":"1","versionCode":1}""")

        assertEquals(1, parsed.apps.single().versions.single().minSdk)
        assertEquals(1, parsed.apps.single().versions.single().targetSdk)
    }

    @Test
    fun test_readIndex_escapingApkName_dropsApp() {
        val parsed = inline("""{"name":"/../../etc/x.apk","sha256":"${"ab".repeat(32)}","size":9}""", """{"versionName":"1","versionCode":1}""")

        assertEquals(emptyList(), parsed.apps)
    }

    @Test
    fun test_readIndex_malformedSha256_dropsApp() {
        val parsed = inline("""{"name":"/org.x_1.apk","sha256":"../../x","size":9}""", """{"versionName":"1","versionCode":1}""")

        assertEquals(emptyList(), parsed.apps)
    }

    @Test
    fun test_readIndex_iconLocale_usesSamePickRule() {
        val parsed = inline(goodFile, """{"versionName":"1","versionCode":1}""", """{"icon":{"de":{"name":"/org.x/de/icon.png"},"en":{"name":"/org.x/en/icon.png"}}}""")

        assertEquals("https://f-droid.org/repo/org.x/en/icon.png", parsed.apps.single().iconUrl)
    }
}
