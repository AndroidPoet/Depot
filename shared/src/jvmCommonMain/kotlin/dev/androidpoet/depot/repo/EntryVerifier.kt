package dev.androidpoet.depot.repo

import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import java.util.jar.JarFile

data class RepoEntry(
    val timestamp: Long,
    val indexName: String,
    val indexSha256: String,
    val indexSize: Long,
)

class EntryVerificationException(message: String, cause: Throwable? = null) : Exception(message, cause)

object EntryVerifier {
    const val MAX_JAR_BYTES = 64 * 1024L
    private const val ENTRY_NAME = "entry.json"

    fun verify(jar: ByteArray, pinnedFingerprint: String): RepoEntry {
        if (jar.size > MAX_JAR_BYTES) throw EntryVerificationException("entry.jar is ${jar.size} bytes, over the $MAX_JAR_BYTES limit")
        val file = File.createTempFile("entry", ".jar")
        try {
            file.writeBytes(jar)
            return parse(signedEntryJson(file, pinnedFingerprint))
        } catch (e: EntryVerificationException) {
            throw e
        } catch (e: Exception) {
            throw EntryVerificationException("entry.jar could not be verified: ${e.message}", e)
        } finally {
            file.delete()
        }
    }

    private fun signedEntryJson(file: File, pinnedFingerprint: String): ByteArray =
        JarFile(file, true).use { jar ->
            val entry = jar.getJarEntry(ENTRY_NAME) ?: throw EntryVerificationException("entry.jar has no $ENTRY_NAME")
            val json = jar.getInputStream(entry).use { it.readBytes() }
            val signers = entry.codeSigners.orEmpty()
            if (signers.size != 1) throw EntryVerificationException("$ENTRY_NAME has ${signers.size} signers, expected exactly 1")
            val leaf = signers.single().signerCertPath.certificates.firstOrNull()
                ?: throw EntryVerificationException("$ENTRY_NAME signer has no certificate")
            val fingerprint = sha256Hex(leaf.encoded)
            if (!fingerprint.equals(pinnedFingerprint, ignoreCase = true)) {
                throw EntryVerificationException("$ENTRY_NAME is signed by $fingerprint, not the pinned $pinnedFingerprint")
            }
            json
        }

    private fun parse(json: ByteArray): RepoEntry {
        val root = JsonParser.parseString(json.decodeToString()).asJsonObject
        val index = root.getAsJsonObject("index")
        return RepoEntry(
            timestamp = root["timestamp"].asLong,
            indexName = index["name"].asString,
            indexSha256 = index["sha256"].asString,
            indexSize = index["size"].asLong,
        )
    }
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
