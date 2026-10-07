package dev.androidpoet.depot.platform

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import dev.androidpoet.depot.Brand
import dev.androidpoet.depot.catalog.DeviceProfile
import dev.androidpoet.depot.catalog.InstalledApp
import dev.androidpoet.depot.engine.DeviceState
import dev.androidpoet.depot.engine.TargetDevice
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume

class AndroidDevice(private val context: Context) : TargetDevice {
    private val installer get() = context.packageManager.packageInstaller

    init {
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
    }

    override suspend fun probe(): DeviceState = DeviceState.Ready(
        name = Build.MODEL,
        profile = DeviceProfile(
            sdk = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.toList(),
            minTargetSdk = when {
                Build.VERSION.SDK_INT >= 35 -> 24
                Build.VERSION.SDK_INT == 34 -> 23
                else -> 0
            },
        ),
        installed = installedPackages().associate { info ->
            info.packageName to InstalledApp(PackageInfoCompat.getLongVersionCode(info), signerOf(info))
        },
    )

    override suspend fun install(apk: File, packageName: String, onAwaitingConfirmation: () -> Unit): String? {
        if (!installsAllowed(onAwaitingConfirmation)) {
            return "Allow ${Brand.NAME} to install apps, then try again"
        }
        return commit(apk, packageName, onAwaitingConfirmation)
    }

    // The system aborts a session that detours through this setting, so it is settled before a session exists.
    private suspend fun installsAllowed(onAwaitingConfirmation: () -> Unit): Boolean {
        val packages = context.packageManager
        if (packages.canRequestPackageInstalls()) return true
        onAwaitingConfirmation()
        awaitReturnFrom(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        return packages.canRequestPackageInstalls()
    }

    private suspend fun awaitReturnFrom(screen: Intent) = suspendCancellableCoroutine { continuation ->
        val app = context.applicationContext as Application
        val onReturn = object : ResumeCallbacks() {
            override fun onActivityResumed(activity: Activity) {
                app.unregisterActivityLifecycleCallbacks(this)
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        app.registerActivityLifecycleCallbacks(onReturn)
        continuation.invokeOnCancellation { app.unregisterActivityLifecycleCallbacks(onReturn) }
        try {
            context.startActivity(screen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            app.unregisterActivityLifecycleCallbacks(onReturn)
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    private suspend fun commit(apk: File, packageName: String, onAwaitingConfirmation: () -> Unit): String? =
        suspendCancellableCoroutine { continuation ->
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(packageName)
                setSize(apk.length())
            }
            val sessionId = installer.createSession(params)
            val action = "${context.packageName}.INSTALL_RESULT.$sessionId"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(received: Context, intent: Intent) {
                    when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            onAwaitingConfirmation()
                            confirmationIntent(intent)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                        else -> {
                            context.unregisterReceiver(this)
                            if (continuation.isActive) continuation.resume(failureText(status, intent))
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            continuation.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
                runCatching { installer.abandonSession(sessionId) }
            }
            try {
                installer.openSession(sessionId).use { session ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        apk.inputStream().use { it.copyTo(output) }
                        session.fsync(output)
                    }
                    val mutability = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                    val result = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        Intent(action).setPackage(context.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or mutability,
                    )
                    session.commit(result.intentSender)
                }
            } catch (e: Exception) {
                runCatching { context.unregisterReceiver(receiver) }
                runCatching { installer.abandonSession(sessionId) }
                if (continuation.isActive) continuation.resume("The installer refused the file: ${e.message}")
            }
        }

    private fun failureText(status: Int, intent: Intent): String? = when (status) {
        PackageInstaller.STATUS_SUCCESS -> null
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Install was cancelled"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough free storage on the device"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This version does not run on the device"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "Conflicts with the installed copy, which is signed by someone else"
        else -> intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed with status $status"
    }

    @Suppress("DEPRECATION")
    private fun confirmationIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_INTENT)

    @Suppress("DEPRECATION")
    private fun installedPackages(): List<PackageInfo> {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return context.packageManager.getInstalledPackages(flags)
    }

    @Suppress("DEPRECATION")
    private fun signerOf(info: PackageInfo): String? {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.takeUnless { it.hasMultipleSigners() }?.signingCertificateHistory
        } else {
            info.signatures
        }
        val current = signatures?.lastOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256").digest(current.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

private abstract class ResumeCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
