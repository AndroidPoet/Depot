package dev.androidpoet.depot.android

import android.app.Application
import dev.androidpoet.depot.engine.DepotEngine
import dev.androidpoet.depot.platform.AndroidDevice

class DepotApplication : Application() {
    val engine: DepotEngine by lazy {
        DepotEngine(dataDir = filesDir, cacheDir = cacheDir, target = AndroidDevice(this)).also { it.start() }
    }
}
