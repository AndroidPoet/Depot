package dev.androidpoet.depot

import dev.androidpoet.depot.repo.ApkIdentity
import dev.androidpoet.depot.repo.ApkInspector
import dev.androidpoet.depot.repo.IntegrityException
import dev.androidpoet.depot.repo.requireExpectedApk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApkInspectorTest {
    private val signer = "b71a381988d13cd954b3b66c543b9b3412068478e603057212733ac3b7889fa8"
    private val identity = ApkIdentity("com.zinaro.cachecleanerwidget", versionCode = 1, signers = listOf(signer))

    private fun apkFile(bytes: ByteArray): File = File.createTempFile("depot", ".apk").apply {
        deleteOnExit()
        writeBytes(bytes)
    }

    @Test
    fun test_inspect_realApk_returnsIdentity() {
        val apk = apkFile(fixtureBytes("sample.apk"))

        assertEquals(identity, ApkInspector.inspect(apk))
    }

    @Test
    fun test_inspect_tamperedApk_fails() {
        val bytes = fixtureBytes("sample.apk")
        bytes[100] = (bytes[100] + 1).toByte()

        assertFailsWith<IntegrityException> { ApkInspector.inspect(apkFile(bytes)) }
    }

    @Test
    fun test_inspect_signatureStripped_fails() {
        val entries = unzip(fixtureBytes("sample.apk"))
        entries.keys.removeAll { it.startsWith("META-INF/") }

        assertFailsWith<IntegrityException> { ApkInspector.inspect(apkFile(zip(entries))) }
    }

    @Test
    fun test_inspect_notAnApk_fails() {
        assertFailsWith<IntegrityException> { ApkInspector.inspect(apkFile(ByteArray(2048) { 3 })) }
    }

    @Test
    fun test_requireExpectedApk_matchingIdentity_passes() {
        requireExpectedApk(identity, "com.zinaro.cachecleanerwidget", version(1, signer = signer))
    }

    @Test
    fun test_requireExpectedApk_upperCaseSigner_passes() {
        requireExpectedApk(identity, "com.zinaro.cachecleanerwidget", version(1, signer = signer.uppercase()))
    }

    @Test
    fun test_requireExpectedApk_catalogueHasNoSigner_passes() {
        requireExpectedApk(identity, "com.zinaro.cachecleanerwidget", version(1, signer = null))
    }

    @Test
    fun test_requireExpectedApk_otherPackage_fails() {
        assertFailsWith<IntegrityException> { requireExpectedApk(identity, "org.other", version(1, signer = signer)) }
    }

    @Test
    fun test_requireExpectedApk_otherVersionCode_fails() {
        assertFailsWith<IntegrityException> { requireExpectedApk(identity, "com.zinaro.cachecleanerwidget", version(2, signer = signer)) }
    }

    @Test
    fun test_requireExpectedApk_otherSigner_fails() {
        assertFailsWith<IntegrityException> {
            requireExpectedApk(identity, "com.zinaro.cachecleanerwidget", version(1, signer = signer.replaceFirst('b', 'c')))
        }
    }

    @Test
    fun test_requireExpectedApk_extraSigner_fails() {
        val twoSigners = identity.copy(signers = listOf(signer, "00".repeat(32)))

        assertFailsWith<IntegrityException> { requireExpectedApk(twoSigners, "com.zinaro.cachecleanerwidget", version(1, signer = signer)) }
    }
}
