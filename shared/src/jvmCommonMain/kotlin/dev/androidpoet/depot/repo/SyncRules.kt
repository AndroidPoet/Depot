package dev.androidpoet.depot.repo

import java.io.File
import java.security.MessageDigest

enum class SyncDecision { UpToDate, Download, Rollback }

fun syncDecision(cachedTimestamp: Long?, entryTimestamp: Long): SyncDecision = when {
    cachedTimestamp == null || entryTimestamp > cachedTimestamp -> SyncDecision.Download
    entryTimestamp == cachedTimestamp -> SyncDecision.UpToDate
    else -> SyncDecision.Rollback
}

class IntegrityException(message: String) : Exception(message)

fun requireIntegrity(file: File, sha256: String, size: Long) {
    if (file.length() != size) {
        throw IntegrityException("${file.name} is ${file.length()} bytes, expected $size")
    }
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    if (!actual.equals(sha256, ignoreCase = true)) {
        throw IntegrityException("${file.name} hashes to $actual, expected $sha256")
    }
}
