package dev.androidpoet.depot.platform

import dev.androidpoet.depot.catalog.DeviceProfile
import dev.androidpoet.depot.catalog.InstalledApp
import dev.androidpoet.depot.engine.DeviceState
import dev.androidpoet.depot.engine.TargetDevice
import java.io.File
import java.util.concurrent.TimeUnit

class AdbDevice : TargetDevice {
    private var ready: Pair<String, Int>? = null

    override suspend fun probe(): DeviceState {
        ready = null
        val adb = findAdb() ?: return DeviceState.AdbMissing
        val devices = AdbOutput.parseDevices(run(listOf(adb, "devices", "-l"), QUERY_SECONDS))
        val device = devices.firstOrNull { it.state == "device" }
            ?: return devices.firstOrNull { it.state == "unauthorized" }?.let { DeviceState.Unauthorized(it.serial) }
                ?: DeviceState.NoDevice
        val serial = device.serial
        val sdk = shell(adb, serial, "getprop", "ro.build.version.sdk").trim().toIntOrNull() ?: return DeviceState.NoDevice
        val name = shell(adb, serial, "getprop", "ro.product.model").trim().ifEmpty { device.model ?: serial }
        val abis = AdbOutput.parseAbis(shell(adb, serial, "getprop", "ro.product.cpu.abilist"))
        val installed = AdbOutput.parseInstalled(shell(adb, serial, "pm", "list", "packages", "--show-versioncode"))
        ready = serial to sdk
        return DeviceState.Ready(
            name = name,
            profile = DeviceProfile(sdk = sdk, abis = abis),
            installed = installed.mapValues { InstalledApp(versionCode = it.value, signer = null) },
        )
    }

    override suspend fun install(apk: File, packageName: String, onAwaitingConfirmation: () -> Unit): String? {
        val adb = findAdb() ?: return "adb was not found"
        val (serial, sdk) = ready ?: return "No device is connected"
        val stillThere = AdbOutput.parseDevices(run(listOf(adb, "devices", "-l"), QUERY_SECONDS))
            .any { it.serial == serial && it.state == "device" }
        if (!stillThere) return "The device was disconnected"
        val flags = if (sdk >= 34) arrayOf("-r", "--bypass-low-target-sdk-block") else arrayOf("-r")
        return AdbOutput.parseInstallFailure(run(adbCommand(adb, serial, "install", *flags, apk.absolutePath), INSTALL_SECONDS))
    }

    private fun shell(adb: String, serial: String, vararg arguments: String): String =
        run(adbCommand(adb, serial, "shell", *arguments), QUERY_SECONDS)

    private fun run(command: List<String>, timeoutSeconds: Long): String {
        val output = File.createTempFile("depot-adb", ".log")
        try {
            val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output).start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return "adb did not answer within $timeoutSeconds seconds"
            }
            return output.readText()
        } finally {
            output.delete()
        }
    }

    private fun findAdb(): String? {
        val executable = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"
        val home = System.getProperty("user.home")
        val sdkRoots = listOfNotNull(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            "$home/Library/Android/sdk",
            "$home/Android/Sdk",
            System.getenv("LOCALAPPDATA")?.let { "$it\\Android\\Sdk" },
        ).map { File(File(it, "platform-tools"), executable) }
        val onPath = System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, executable) }
        return (sdkRoots + onPath).firstOrNull { it.canExecute() }?.absolutePath
    }

    private companion object {
        const val QUERY_SECONDS = 15L
        const val INSTALL_SECONDS = 300L
    }
}
