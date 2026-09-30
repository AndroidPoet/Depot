package dev.androidpoet.depot.repo

import dev.androidpoet.depot.catalog.Catalog
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class CatalogCache(private val dataDir: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val catalogFile = File(dataDir, "catalog.json")
    private val checkedFile = File(dataDir, "last-checked")

    fun load(): Catalog? = runCatching {
        catalogFile.takeIf { it.exists() }?.let { json.decodeFromString<Catalog>(it.readText()) }
    }.getOrNull()

    fun save(catalog: Catalog) {
        dataDir.mkdirs()
        val temp = File(dataDir, "catalog.json.tmp")
        temp.writeText(json.encodeToString(Catalog.serializer(), catalog))
        Files.move(temp.toPath(), catalogFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun lastChecked(): Long? = runCatching { checkedFile.readText().trim().toLong() }.getOrNull()

    fun markChecked(at: Long) {
        dataDir.mkdirs()
        checkedFile.writeText(at.toString())
    }
}
