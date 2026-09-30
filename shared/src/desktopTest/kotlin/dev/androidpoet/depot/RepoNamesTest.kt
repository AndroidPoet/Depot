package dev.androidpoet.depot

import dev.androidpoet.depot.repo.apkFileName
import dev.androidpoet.depot.repo.repoUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RepoNamesTest {
    @Test
    fun test_repoUrl_rootedName_isAppendedToBase() {
        assertEquals("https://f-droid.org/repo/org.fdroid.fdroid_2000051.apk", repoUrl("/org.fdroid.fdroid_2000051.apk"))
        assertEquals(
            "https://f-droid.org/repo/org.fdroid.fdroid/en-US/icon_0slNR8J9edqDZDoyWABFZrqQONU9EyiNVwM6B3Jui_Q=.png",
            repoUrl("/org.fdroid.fdroid/en-US/icon_0slNR8J9edqDZDoyWABFZrqQONU9EyiNVwM6B3Jui_Q=.png"),
        )
    }

    @Test
    fun test_repoUrl_escapingOrForeignName_returnsNull() {
        listOf(
            "x.apk",
            "/../x.apk",
            "/a/../../x.apk",
            "/a\\b.apk",
            "/%2e%2e/x.apk",
            "//evil.example/x.apk",
            "/x.apk?y=1",
            "/x.apk#frag",
            "/http://evil.example/x.apk",
            "/a b.apk",
            "",
        ).forEach { name -> assertNull(repoUrl(name), "accepted $name") }
    }

    @Test
    fun test_apkFileName_validSha256_isHashDotApk() {
        assertEquals("${"ab".repeat(32)}.apk", apkFileName("ab".repeat(32)))
    }

    @Test
    fun test_apkFileName_notSha256_fails() {
        assertFailsWith<IllegalArgumentException> { apkFileName("../../etc/passwd") }
    }
}
