package dev.androidpoet.depot.repo

const val FDROID_REPO = "https://f-droid.org/repo"
const val FDROID_FINGERPRINT = "43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab"

private val sha256Pattern = Regex("[0-9a-f]{64}")
private val forbiddenInName = Regex("""\.\.|//|[\\%?#:\s]""")

fun repoUrl(name: String): String? =
    if (name.startsWith("/") && !forbiddenInName.containsMatchIn(name)) FDROID_REPO + name else null

fun isSha256(value: String): Boolean = sha256Pattern.matches(value)

fun apkFileName(sha256: String): String {
    require(isSha256(sha256)) { "not a sha256: $sha256" }
    return "$sha256.apk"
}
