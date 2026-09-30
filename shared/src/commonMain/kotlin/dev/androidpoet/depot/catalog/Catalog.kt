package dev.androidpoet.depot.catalog

import kotlinx.serialization.Serializable

@Serializable
data class Catalog(
    val timestamp: Long,
    val apps: List<CatalogApp>,
)

@Serializable
data class CatalogApp(
    val packageName: String,
    val name: String,
    val summary: String,
    val description: String,
    val iconUrl: String?,
    val categories: List<String>,
    val license: String,
    val authorName: String?,
    val sourceCode: String?,
    val webSite: String?,
    val added: Long,
    val lastUpdated: Long,
    val preferredSigner: String?,
    val versions: List<AppVersion>,
)

@Serializable
data class AppVersion(
    val versionName: String,
    val versionCode: Long,
    val apkPath: String,
    val sha256: String,
    val size: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val maxSdk: Int?,
    val abis: List<String>,
    val added: Long,
    val antiFeatures: List<String>,
    val beta: Boolean,
    val signer: String?,
)

data class DeviceProfile(
    val sdk: Int,
    val abis: List<String>,
    val minTargetSdk: Int = 0,
)

data class InstalledApp(
    val versionCode: Long,
    val signer: String?,
)

fun AppVersion.runsOn(device: DeviceProfile?): Boolean {
    if (device == null) return true
    return minSdk <= device.sdk &&
        (maxSdk == null || maxSdk >= device.sdk) &&
        targetSdk >= device.minTargetSdk &&
        (abis.isEmpty() || abis.any { it in device.abis })
}

fun AppVersion.canReplace(installed: InstalledApp?): Boolean =
    installed?.signer == null || signer == null || signer == installed.signer

fun CatalogApp.suggestedVersion(device: DeviceProfile?, installed: InstalledApp? = null): AppVersion? {
    val usable = versions.filter { it.runsOn(device) && it.canReplace(installed) }
    val candidates = when {
        installed?.signer != null || preferredSigner == null -> usable
        else -> usable.filter { it.signer == null || it.signer == preferredSigner }.ifEmpty { usable }
    }
    val newest = candidates.filterNot { it.beta }.maxByOrNull { it.versionCode }
        ?: candidates.maxByOrNull { it.versionCode }
        ?: return null
    return candidates
        .filter { it.versionName == newest.versionName && it.beta == newest.beta }
        .minWithOrNull(compareBy<AppVersion> { it.abiRank(device) }.thenByDescending { it.versionCode })
}

fun CatalogApp.updateAvailable(installed: InstalledApp?, device: DeviceProfile?): Boolean {
    if (installed == null) return false
    val suggested = suggestedVersion(device, installed) ?: return false
    return suggested.versionCode > installed.versionCode
}

private fun AppVersion.abiRank(device: DeviceProfile?): Int {
    if (device == null || abis.isEmpty()) return 0
    return abis.minOf { abi -> device.abis.indexOf(abi).takeIf { it >= 0 } ?: Int.MAX_VALUE }
}
