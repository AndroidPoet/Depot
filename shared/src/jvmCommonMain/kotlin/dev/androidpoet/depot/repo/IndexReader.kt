package dev.androidpoet.depot.repo

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import dev.androidpoet.depot.catalog.AppVersion
import dev.androidpoet.depot.catalog.Catalog
import dev.androidpoet.depot.catalog.CatalogApp
import java.io.InputStream

object IndexReader {
    fun read(input: InputStream): Catalog {
        var timestamp = 0L
        val apps = ArrayList<CatalogApp>()
        JsonReader(input.reader(Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "repo" -> timestamp = JsonParser.parseReader(reader).asJsonObject.long("timestamp")
                    "packages" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val packageName = reader.nextName()
                            app(packageName, JsonParser.parseReader(reader).asJsonObject)?.let(apps::add)
                        }
                        reader.endObject()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        return Catalog(timestamp, apps)
    }

    private fun app(packageName: String, node: JsonObject): CatalogApp? {
        val metadata = node.obj("metadata") ?: return null
        val versions = node.obj("versions")?.entrySet().orEmpty()
            .mapNotNull { (_, value) -> version(value) }
            .sortedByDescending { it.versionCode }
        if (versions.isEmpty()) return null
        return CatalogApp(
            packageName = packageName,
            name = metadata.localized("name").ifBlank { packageName },
            summary = metadata.localized("summary"),
            description = metadata.localized("description"),
            iconUrl = metadata.obj("icon")?.asMap()?.pickLocalized()?.asJsonObject?.string("name")?.let(::repoUrl),
            categories = metadata.strings("categories"),
            license = metadata.string("license").orEmpty(),
            authorName = metadata.string("authorName"),
            sourceCode = metadata.string("sourceCode"),
            webSite = metadata.string("webSite"),
            added = metadata.long("added"),
            lastUpdated = metadata.long("lastUpdated"),
            preferredSigner = metadata.string("preferredSigner"),
            versions = versions,
        )
    }

    private fun version(element: JsonElement): AppVersion? {
        val node = element.asJsonObject
        val file = node.obj("file") ?: return null
        val manifest = node.obj("manifest") ?: return null
        val apkPath = file.string("name")?.takeIf { repoUrl(it) != null } ?: return null
        val sha256 = file.string("sha256")?.lowercase()?.takeIf(::isSha256) ?: return null
        val sdk = manifest.obj("usesSdk")
        val minSdk = sdk?.int("minSdkVersion") ?: 1
        return AppVersion(
            versionName = manifest.string("versionName").orEmpty(),
            versionCode = manifest.long("versionCode"),
            apkPath = apkPath,
            sha256 = sha256,
            size = file.long("size"),
            minSdk = minSdk,
            targetSdk = sdk?.int("targetSdkVersion") ?: minSdk,
            maxSdk = sdk?.int("maxSdkVersion"),
            abis = manifest.strings("nativecode"),
            added = node.long("added"),
            antiFeatures = node.obj("antiFeatures")?.keySet()?.toList().orEmpty(),
            beta = node.strings("releaseChannels").isNotEmpty(),
            signer = manifest.obj("signer")?.strings("sha256")?.firstOrNull(),
        )
    }
}

fun <T> Map<String, T>.pickLocalized(): T? =
    this["en-US"] ?: entries.firstOrNull { it.key.startsWith("en") }?.value ?: values.firstOrNull()

fun Map<String, String>.pickLocale(): String = pickLocalized().orEmpty()

private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.string(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

private fun JsonObject.long(key: String): Long = get(key)?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L

private fun JsonObject.int(key: String): Int? = get(key)?.takeIf { it.isJsonPrimitive }?.asInt

private fun JsonObject.strings(key: String): List<String> =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }.orEmpty()

private fun JsonObject.localized(key: String): String =
    obj(key)?.asMap()?.mapValues { it.value.asString }?.pickLocale().orEmpty()
