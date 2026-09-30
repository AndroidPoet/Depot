package dev.androidpoet.depot.platform

data class AdbDeviceLine(
    val serial: String,
    val state: String,
    val model: String?,
)

fun adbCommand(adb: String, serial: String, vararg arguments: String): List<String> =
    listOf(adb, "-s", serial) + arguments

object AdbOutput {
    private val installedLine = Regex("""^package:(\S+) versionCode:(\d+)""")
    private val failureReason = Regex("""Failure \[(.+)]""")

    fun parseDevices(output: String): List<AdbDeviceLine> =
        output.lineSequence()
            .dropWhile { !it.startsWith("List of devices") }
            .drop(1)
            .map { it.trim().split(Regex("\\s+")) }
            .filter { it.size >= 2 }
            .map { parts ->
                AdbDeviceLine(
                    serial = parts[0],
                    state = parts[1],
                    model = parts.firstOrNull { it.startsWith("model:") }?.removePrefix("model:"),
                )
            }
            .toList()

    fun parseInstalled(output: String): Map<String, Long> =
        output.lineSequence()
            .mapNotNull { installedLine.find(it.trim()) }
            .associate { it.groupValues[1] to it.groupValues[2].toLong() }

    fun parseInstallFailure(output: String): String? {
        failureReason.find(output)?.let { return it.groupValues[1] }
        if (output.lineSequence().any { it.trim() == "Success" }) return null
        return output.trim().lineSequence().lastOrNull()?.trim().orEmpty().ifEmpty { "adb gave no output" }
    }

    fun parseAbis(output: String): List<String> =
        output.trim().split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
