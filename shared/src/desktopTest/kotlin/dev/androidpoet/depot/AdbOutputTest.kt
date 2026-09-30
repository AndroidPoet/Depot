package dev.androidpoet.depot

import dev.androidpoet.depot.platform.AdbOutput
import dev.androidpoet.depot.platform.adbCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdbOutputTest {
    @Test
    fun test_parseDevices_mixedStates_returnsEach() {
        val output = """
            List of devices attached
            emulator-5554          device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 device:emu64a transport_id:1
            R5CT1234ABC            unauthorized usb:1-1 transport_id:2
            192.168.1.5:5555       offline

        """.trimIndent()

        val devices = AdbOutput.parseDevices(output)

        assertEquals(listOf("emulator-5554", "R5CT1234ABC", "192.168.1.5:5555"), devices.map { it.serial })
        assertEquals(listOf("device", "unauthorized", "offline"), devices.map { it.state })
        assertEquals("sdk_gphone64_arm64", devices.first().model)
        assertNull(devices[1].model)
    }

    @Test
    fun test_parseDevices_daemonStartupNoise_isIgnored() {
        val output = "* daemon not running; starting now at tcp:5037\n* daemon started successfully\nList of devices attached\n\n"

        assertEquals(emptyList(), AdbOutput.parseDevices(output))
    }

    @Test
    fun test_parseInstalled_showVersionCode_returnsMap() {
        val output = "package:org.fdroid.fdroid versionCode:1023052\npackage:com.android.shell versionCode:36\n\nWARNING: linker noise\n"

        assertEquals(mapOf("org.fdroid.fdroid" to 1023052L, "com.android.shell" to 36L), AdbOutput.parseInstalled(output))
    }

    @Test
    fun test_parseInstallResult_success_returnsNull() {
        assertNull(AdbOutput.parseInstallFailure("Performing Streamed Install\nSuccess\n"))
    }

    @Test
    fun test_parseInstallResult_failureLine_returnsReason() {
        val output = "Performing Streamed Install\nadb: failed to install a.apk: Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package org.x signatures do not match newer version; ignoring!]\n"

        val reason = AdbOutput.parseInstallFailure(output)

        assertTrue(reason!!.startsWith("INSTALL_FAILED_UPDATE_INCOMPATIBLE"))
    }

    @Test
    fun test_parseInstallResult_noSuccessLine_returnsOutput() {
        assertEquals("adb: device offline", AdbOutput.parseInstallFailure("adb: device offline\n"))
    }

    @Test
    fun test_parseAbis_commaList_returnsEach() {
        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), AdbOutput.parseAbis("arm64-v8a,armeabi-v7a\n"))
    }

    @Test
    fun test_adbCommand_anyArguments_carriesSerial() {
        assertEquals(
            listOf("/sdk/adb", "-s", "emulator-5554", "install", "-r", "/tmp/a b.apk"),
            adbCommand("/sdk/adb", "emulator-5554", "install", "-r", "/tmp/a b.apk"),
        )
    }
}
