package dev.androidpoet.depot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.androidpoet.depot.catalog.AppVersion
import dev.androidpoet.depot.catalog.BrowseQuery
import dev.androidpoet.depot.catalog.Catalog
import dev.androidpoet.depot.catalog.CatalogApp
import dev.androidpoet.depot.catalog.DeviceProfile
import dev.androidpoet.depot.catalog.InstalledApp
import dev.androidpoet.depot.catalog.SortOrder
import dev.androidpoet.depot.catalog.browse
import dev.androidpoet.depot.catalog.categoryCounts
import dev.androidpoet.depot.catalog.suggestedVersion
import dev.androidpoet.depot.catalog.updateAvailable
import dev.androidpoet.depot.engine.Depot
import dev.androidpoet.depot.engine.DeviceState
import dev.androidpoet.depot.engine.InstallState
import dev.androidpoet.depot.engine.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class Shelf(val label: String) {
    All("All apps"),
    Installed("Installed"),
    Updates("Updates"),
}

data class Listing(
    val apps: List<CatalogApp> = emptyList(),
    val total: Int = 0,
    val installedCount: Int = 0,
    val updateCount: Int = 0,
)

data class HomeState(
    val query: BrowseQuery = BrowseQuery(),
    val shelf: Shelf = Shelf.All,
    val listing: Listing = Listing(),
    val categories: List<Pair<String, Int>> = emptyList(),
    val hasCatalog: Boolean = false,
    val sync: SyncState = SyncState.Idle,
    val lastChecked: Long? = null,
    val device: DeviceState = DeviceState.Unknown,
    val installs: Map<String, InstallState> = emptyMap(),
    val selected: CatalogApp? = null,
)

sealed interface AppStatus {
    data object NotCompatible : AppStatus
    data class Available(val version: AppVersion) : AppStatus
    data class UpToDate(val installed: InstalledApp) : AppStatus
    data class Update(val version: AppVersion, val installed: InstalledApp) : AppStatus
}

fun CatalogApp.statusOn(device: DeviceState): AppStatus {
    val ready = device as? DeviceState.Ready
    val installed = ready?.installed?.get(packageName)
    val suggested = suggestedVersion(ready?.profile, installed)
    return when {
        installed != null && suggested != null && suggested.versionCode > installed.versionCode -> AppStatus.Update(suggested, installed)
        installed != null -> AppStatus.UpToDate(installed)
        suggested != null -> AppStatus.Available(suggested)
        else -> AppStatus.NotCompatible
    }
}

class DepotViewModel(private val depot: Depot, initialPackage: String? = null) : ViewModel() {
    private val query = MutableStateFlow(BrowseQuery())
    private val shelf = MutableStateFlow(Shelf.All)
    private val selectedPackage = MutableStateFlow(initialPackage)

    private val listing = combine(depot.catalog, depot.device, query, shelf, ::listingOf).flowOn(Dispatchers.Default)
    private val categories = depot.catalog.map { it?.apps.orEmpty().categoryCounts() }.flowOn(Dispatchers.Default)
    private val browsing = combine(query, shelf, listing, categories, selectedPackage) { query, shelf, listing, categories, selected ->
        HomeState(query = query, shelf = shelf, listing = listing, categories = categories, selected = depot.catalog.value?.apps?.find { it.packageName == selected })
    }
    private val engine = combine(depot.catalog, depot.sync, depot.lastChecked, depot.device, depot.installs) { catalog, sync, lastChecked, device, installs ->
        HomeState(hasCatalog = catalog != null, sync = sync, lastChecked = lastChecked, device = device, installs = installs)
    }

    val state: StateFlow<HomeState> = combine(browsing, engine) { browsing, engine ->
        browsing.copy(hasCatalog = engine.hasCatalog, sync = engine.sync, lastChecked = engine.lastChecked, device = engine.device, installs = engine.installs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun search(text: String) = query.update { it.copy(text = text) }

    fun pickCategory(category: String?) = query.update { it.copy(category = category) }

    fun sortBy(order: SortOrder) = query.update { it.copy(sort = order) }

    fun showShelf(next: Shelf) {
        shelf.value = next
    }

    fun open(app: CatalogApp) {
        selectedPackage.value = app.packageName
    }

    fun closeDetail() {
        selectedPackage.value = null
    }

    fun refresh() {
        depot.refresh()
        depot.refreshDevice()
    }

    fun install(app: CatalogApp, version: AppVersion) = depot.install(app, version)

    fun cancelInstall(app: CatalogApp) = depot.cancelInstall(app.packageName)

    private fun listingOf(catalog: Catalog?, device: DeviceState, query: BrowseQuery, shelf: Shelf): Listing {
        val apps = catalog?.apps.orEmpty()
        val ready = device as? DeviceState.Ready
        val installed = ready?.installed.orEmpty()
        val profile: DeviceProfile? = ready?.profile
        val onDevice = apps.filter { it.packageName in installed }
        val withUpdates = onDevice.filter { it.updateAvailable(installed[it.packageName], profile) }
        val shelved = when (shelf) {
            Shelf.All -> apps
            Shelf.Installed -> onDevice
            Shelf.Updates -> withUpdates
        }
        return Listing(
            apps = shelved.browse(query),
            total = apps.size,
            installedCount = onDevice.size,
            updateCount = withUpdates.size,
        )
    }
}
