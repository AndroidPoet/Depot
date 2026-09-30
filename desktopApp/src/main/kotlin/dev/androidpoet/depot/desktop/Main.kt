package dev.androidpoet.depot.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.androidpoet.depot.Brand
import dev.androidpoet.depot.engine.DepotEngine
import dev.androidpoet.depot.platform.AdbDevice
import dev.androidpoet.depot.ui.DepotApp
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

fun main() {
    val (dataDir, cacheDir) = appDirs()
    val engine = DepotEngine(dataDir = dataDir.ownerOnly(), cacheDir = cacheDir.ownerOnly(), target = AdbDevice())
    engine.start()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = Brand.NAME,
            state = rememberWindowState(size = DpSize(1280.dp, 820.dp)),
        ) {
            DepotApp(
                depot = engine,
                dark = System.getenv("DEPOT_THEME")?.let { it == "dark" },
                initialPackage = System.getenv("DEPOT_OPEN"),
            )
        }
    }
}

private fun appDirs(): Pair<File, File> {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name")
    return when {
        os.startsWith("Mac") -> File(home, "Library/Application Support/Depot") to File(home, "Library/Caches/Depot")
        os.startsWith("Windows") ->
            File(System.getenv("APPDATA") ?: home, "Depot") to File(System.getenv("LOCALAPPDATA") ?: home, "Depot/Cache")
        else ->
            File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "depot") to
                File(System.getenv("XDG_CACHE_HOME") ?: "$home/.cache", "depot")
    }
}

private fun File.ownerOnly(): File {
    mkdirs()
    runCatching { Files.setPosixFilePermissions(toPath(), PosixFilePermissions.fromString("rwx------")) }
    return this
}
