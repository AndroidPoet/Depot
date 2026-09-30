package dev.androidpoet.depot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.androidpoet.depot.catalog.AppVersion
import dev.androidpoet.depot.catalog.Catalog
import dev.androidpoet.depot.catalog.CatalogApp
import dev.androidpoet.depot.catalog.DeviceProfile
import dev.androidpoet.depot.catalog.InstalledApp
import dev.androidpoet.depot.engine.Depot
import dev.androidpoet.depot.engine.DeviceState
import dev.androidpoet.depot.engine.InstallState
import dev.androidpoet.depot.engine.SyncState
import dev.androidpoet.depot.repo.CatalogCache
import dev.androidpoet.depot.ui.DepotApp
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

class ScreenshotTest {
    private class StillDepot(catalog: Catalog, device: DeviceState, installs: Map<String, InstallState>) : Depot {
        override val catalog = MutableStateFlow<Catalog?>(catalog)
        override val lastChecked = MutableStateFlow<Long?>(System.currentTimeMillis() - 2 * 3_600_000)
        override val sync = MutableStateFlow<SyncState>(SyncState.Idle)
        override val device = MutableStateFlow(device)
        override val installs = MutableStateFlow(installs)
        override fun start() = Unit
        override fun refresh() = Unit
        override fun refreshDevice() = Unit
        override fun install(app: CatalogApp, version: AppVersion) = Unit
        override fun cancelInstall(packageName: String) = Unit
    }

    private class Host : ViewModelStoreOwner, LifecycleOwner {
        override val viewModelStore = ViewModelStore()
        private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun test_screens_withCachedCatalogue_renderToPng() {
        val out = System.getenv("DEPOT_SHOTS")?.let(::File) ?: return
        val catalog = System.getenv("DEPOT_CATALOG_DIR")?.let { CatalogCache(File(it)).load() } ?: return
        out.mkdirs()
        val recent = catalog.apps.sortedByDescending { it.lastUpdated }
        val phone = DeviceState.Ready(
            name = "Pixel 8",
            profile = DeviceProfile(sdk = 35, abis = listOf("arm64-v8a", "armeabi-v7a"), minTargetSdk = 0),
            installed = mapOf(
                recent[1].packageName to InstalledApp(recent[1].versions.first().versionCode, null),
                recent[3].packageName to InstalledApp(recent[3].versions.last().versionCode - 1, null),
                "org.fdroid.fdroid" to InstalledApp(1, null),
            ),
        )
        shot(out, "wide-dark-home", 1280, 820, 2f, true, null, StillDepot(catalog, phone, emptyMap()))
        shot(out, "wide-light-home", 1280, 820, 2f, false, null, StillDepot(catalog, DeviceState.NoDevice, emptyMap()))
        shot(out, "wide-light-detail", 1280, 820, 2f, false, "InfinityLoop1309.NewPipeEnhanced", StillDepot(catalog, phone, emptyMap()))
        shot(
            out, "wide-dark-installing", 1280, 820, 2f, true, "org.fdroid.fdroid",
            StillDepot(catalog, phone, mapOf("org.fdroid.fdroid" to InstallState.Downloading(0.42f))),
        )
        shot(out, "phone-light-home", 412, 915, 2.625f, false, null, StillDepot(catalog, phone, emptyMap()))
        shot(out, "phone-dark-detail", 412, 915, 2.625f, true, "org.fdroid.fdroid", StillDepot(catalog, phone, emptyMap()))
    }

    private fun shot(out: File, name: String, widthDp: Int, heightDp: Int, density: Float, dark: Boolean, open: String?, depot: Depot) {
        val host = Host()
        val scene = ImageComposeScene((widthDp * density).toInt(), (heightDp * density).toInt(), Density(density)) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides host, LocalLifecycleOwner provides host) {
                DepotApp(depot, dark = dark, initialPackage = open)
            }
        }
        try {
            val start = System.nanoTime()
            repeat(60) {
                scene.render(System.nanoTime() - start)
                Thread.sleep(100)
            }
            val png = scene.render(System.nanoTime() - start).encodeToData(EncodedImageFormat.PNG) ?: error("png encoding failed")
            File(out, "$name.png").writeBytes(png.bytes)
        } finally {
            scene.close()
        }
    }
}
