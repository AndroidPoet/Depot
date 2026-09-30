package dev.androidpoet.depot.repo

import com.android.apksig.ApkVerifier
import com.android.apksig.apk.ApkUtils
import com.android.apksig.util.DataSources
import dev.androidpoet.depot.catalog.AppVersion
import java.io.File
import java.io.RandomAccessFile

data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val signers: List<String>,
)

object ApkInspector {
    fun inspect(apk: File): ApkIdentity {
        val result = try {
            ApkVerifier.Builder(apk).build().verify()
        } catch (e: Exception) {
            throw IntegrityException("The APK could not be read: ${e.message}")
        }
        if (!result.isVerified) {
            throw IntegrityException("The APK signature is not valid: ${result.errors.firstOrNull() ?: "no valid signer"}")
        }
        return RandomAccessFile(apk, "r").use { file ->
            val manifest = ApkUtils.getAndroidManifest(DataSources.asDataSource(file))
            ApkIdentity(
                packageName = ApkUtils.getPackageNameFromBinaryAndroidManifest(manifest.duplicate()),
                versionCode = ApkUtils.getLongVersionCodeFromBinaryAndroidManifest(manifest.duplicate()),
                signers = result.signerCertificates.map { sha256Hex(it.encoded) },
            )
        }
    }
}

fun requireExpectedApk(identity: ApkIdentity, packageName: String, version: AppVersion) {
    if (identity.packageName != packageName) {
        throw IntegrityException("The APK is for ${identity.packageName}, not $packageName")
    }
    if (identity.versionCode != version.versionCode) {
        throw IntegrityException("The APK is version code ${identity.versionCode}, the catalogue says ${version.versionCode}")
    }
    val expected = version.signer ?: return
    if (identity.signers.size != 1 || !identity.signers.single().equals(expected, ignoreCase = true)) {
        throw IntegrityException("The APK is signed by ${identity.signers.joinToString()}, the catalogue says $expected")
    }
}
