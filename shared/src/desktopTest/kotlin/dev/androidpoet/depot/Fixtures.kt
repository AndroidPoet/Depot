package dev.androidpoet.depot

import dev.androidpoet.depot.catalog.AppVersion
import dev.androidpoet.depot.catalog.CatalogApp
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

const val FDROID_FINGERPRINT = "43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab"

fun fixtureBytes(name: String): ByteArray =
    checkNotNull(object {}.javaClass.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }.use { it.readBytes() }

fun unzip(jar: ByteArray): LinkedHashMap<String, ByteArray> {
    val entries = LinkedHashMap<String, ByteArray>()
    ZipInputStream(jar.inputStream()).use { zip ->
        generateSequence { zip.nextEntry }.forEach { entries[it.name] = zip.readBytes() }
    }
    return entries
}

fun zip(entries: Map<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

fun version(
    code: Long,
    name: String = "v$code",
    beta: Boolean = false,
    minSdk: Int = 21,
    targetSdk: Int = 34,
    maxSdk: Int? = null,
    abis: List<String> = emptyList(),
    signer: String? = null,
) = AppVersion(
    versionName = name,
    versionCode = code,
    apkPath = "/app_$code.apk",
    sha256 = "00",
    size = 1,
    minSdk = minSdk,
    targetSdk = targetSdk,
    maxSdk = maxSdk,
    abis = abis,
    added = code,
    antiFeatures = emptyList(),
    beta = beta,
    signer = signer,
)

fun app(
    packageName: String,
    name: String = packageName,
    summary: String = "",
    categories: List<String> = emptyList(),
    added: Long = 0,
    lastUpdated: Long = 0,
    preferredSigner: String? = null,
    versions: List<AppVersion> = emptyList(),
) = CatalogApp(
    packageName = packageName,
    name = name,
    summary = summary,
    description = "",
    iconUrl = null,
    categories = categories,
    license = "MIT",
    authorName = null,
    sourceCode = null,
    webSite = null,
    added = added,
    lastUpdated = lastUpdated,
    preferredSigner = preferredSigner,
    versions = versions,
)
