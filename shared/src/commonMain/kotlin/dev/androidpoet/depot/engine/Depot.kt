package dev.androidpoet.depot.engine

import dev.androidpoet.depot.catalog.AppVersion
import dev.androidpoet.depot.catalog.Catalog
import dev.androidpoet.depot.catalog.CatalogApp
import dev.androidpoet.depot.catalog.DeviceProfile
import dev.androidpoet.depot.catalog.InstalledApp
import kotlinx.coroutines.flow.StateFlow

sealed interface SyncState {
    data object Idle : SyncState
    data object Checking : SyncState
    data class Downloading(val bytes: Long, val total: Long) : SyncState
    data object Reading : SyncState
    data class Failed(val message: String) : SyncState
}

sealed interface DeviceState {
    data object Unknown : DeviceState
    data object AdbMissing : DeviceState
    data object NoDevice : DeviceState
    data class Unauthorized(val serial: String) : DeviceState
    data class Ready(
        val name: String,
        val profile: DeviceProfile,
        val installed: Map<String, InstalledApp>,
    ) : DeviceState
}

sealed interface InstallState {
    val busy: Boolean get() = true

    data class Downloading(val fraction: Float) : InstallState
    data object Verifying : InstallState
    data object Installing : InstallState
    data object AwaitingConfirmation : InstallState
    data object Done : InstallState { override val busy get() = false }
    data class Failed(val reason: String) : InstallState { override val busy get() = false }
}

interface Depot {
    val catalog: StateFlow<Catalog?>
    val lastChecked: StateFlow<Long?>
    val sync: StateFlow<SyncState>
    val device: StateFlow<DeviceState>
    val installs: StateFlow<Map<String, InstallState>>

    fun start()
    fun refresh()
    fun refreshDevice()
    fun install(app: CatalogApp, version: AppVersion)
    fun cancelInstall(packageName: String)
}
