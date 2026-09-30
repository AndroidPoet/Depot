package dev.androidpoet.depot

import dev.androidpoet.depot.repo.EntryVerificationException
import dev.androidpoet.depot.repo.EntryVerifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EntryVerifierTest {
    private val realJar = fixtureBytes("entry.jar")

    @Test
    fun test_verifyEntry_realJarWithPinnedFingerprint_returnsEntry() {
        val entry = EntryVerifier.verify(realJar, FDROID_FINGERPRINT)

        assertEquals(1790718180755, entry.timestamp)
        assertEquals("/index-v2.json", entry.indexName)
        assertEquals("e0fde6a3d0882f4ff88b48b594e4cb818e4ad5ed46005007c47d203a2819d330", entry.indexSha256)
        assertEquals(61862942, entry.indexSize)
    }

    @Test
    fun test_verifyEntry_wrongFingerprint_fails() {
        val otherFingerprint = FDROID_FINGERPRINT.replaceFirst('4', '5')

        assertFailsWith<EntryVerificationException> { EntryVerifier.verify(realJar, otherFingerprint) }
    }

    @Test
    fun test_verifyEntry_tamperedEntryJson_fails() {
        val entries = unzip(realJar)
        entries["entry.json"] = String(entries.getValue("entry.json")).replace("1790718180755", "1790718180756").toByteArray()

        assertFailsWith<EntryVerificationException> { EntryVerifier.verify(zip(entries), FDROID_FINGERPRINT) }
    }

    @Test
    fun test_verifyEntry_tamperedManifestWithMatchingEntry_fails() {
        val entries = unzip(realJar)
        val forged = """{"timestamp": 9990718180755, "version": 30000, "index": {"name": "/index-v2.json", "sha256": "00", "size": 1, "numPackages": 1}}""".toByteArray()
        val digest = java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(forged))
        entries["entry.json"] = forged
        entries["META-INF/MANIFEST.MF"] = "Manifest-Version: 1.0\r\n\r\nName: entry.json\r\nSHA-256-Digest: $digest\r\n\r\n".toByteArray()

        assertFailsWith<EntryVerificationException> { EntryVerifier.verify(zip(entries), FDROID_FINGERPRINT) }
    }

    @Test
    fun test_verifyEntry_tamperedSignatureFile_fails() {
        val entries = unzip(realJar)
        val signatureFile = entries.keys.single { it.endsWith(".SF") }
        entries[signatureFile] = entries.getValue(signatureFile) + "X-Extra: 1\r\n".toByteArray()

        assertFailsWith<EntryVerificationException> { EntryVerifier.verify(zip(entries), FDROID_FINGERPRINT) }
    }

    @Test
    fun test_verifyEntry_missingSignatureBlock_fails() {
        val entries = unzip(realJar)
        entries.keys.removeAll { it.endsWith(".RSA") }

        assertFailsWith<EntryVerificationException> { EntryVerifier.verify(zip(entries), FDROID_FINGERPRINT) }
    }

    @Test
    fun test_verifyEntry_unsignedJar_fails() {
        val entries = unzip(realJar)
        entries.keys.removeAll { it.startsWith("META-INF/") }

        assertFailsWith<EntryVerificationException> { EntryVerifier.verify(zip(entries), FDROID_FINGERPRINT) }
    }

    @Test
    fun test_verifyEntry_oversizedJar_fails() {
        assertFailsWith<EntryVerificationException> { EntryVerifier.verify(ByteArray(70_000), FDROID_FINGERPRINT) }
    }

    @Test
    fun test_verifyEntry_garbage_fails() {
        assertFailsWith<EntryVerificationException> { EntryVerifier.verify(ByteArray(64) { 7 }, FDROID_FINGERPRINT) }
    }
}
