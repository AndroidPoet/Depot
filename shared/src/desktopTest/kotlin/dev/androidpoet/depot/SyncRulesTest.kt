package dev.androidpoet.depot

import dev.androidpoet.depot.repo.IntegrityException
import dev.androidpoet.depot.repo.SyncDecision
import dev.androidpoet.depot.repo.requireIntegrity
import dev.androidpoet.depot.repo.syncDecision
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SyncRulesTest {
    private val helloSha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

    private fun helloFile(): File = File.createTempFile("depot", ".bin").apply {
        deleteOnExit()
        writeText("hello")
    }

    @Test
    fun test_syncDecision_olderTimestamp_rejectsAsRollback() {
        assertEquals(SyncDecision.Rollback, syncDecision(cachedTimestamp = 200, entryTimestamp = 100))
    }

    @Test
    fun test_syncDecision_sameTimestamp_isUpToDate() {
        assertEquals(SyncDecision.UpToDate, syncDecision(cachedTimestamp = 200, entryTimestamp = 200))
    }

    @Test
    fun test_syncDecision_newerTimestamp_downloads() {
        assertEquals(SyncDecision.Download, syncDecision(cachedTimestamp = 200, entryTimestamp = 300))
    }

    @Test
    fun test_syncDecision_noCache_downloads() {
        assertEquals(SyncDecision.Download, syncDecision(cachedTimestamp = null, entryTimestamp = 300))
    }

    @Test
    fun test_hashGate_matchingBytes_passes() {
        requireIntegrity(helloFile(), helloSha256, size = 5)
    }

    @Test
    fun test_hashGate_upperCaseHash_passes() {
        requireIntegrity(helloFile(), helloSha256.uppercase(), size = 5)
    }

    @Test
    fun test_hashGate_wrongHash_fails() {
        assertFailsWith<IntegrityException> { requireIntegrity(helloFile(), helloSha256.replaceFirst('2', '3'), size = 5) }
    }

    @Test
    fun test_hashGate_wrongSize_fails() {
        assertFailsWith<IntegrityException> { requireIntegrity(helloFile(), helloSha256, size = 6) }
    }
}
