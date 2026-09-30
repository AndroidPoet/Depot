package dev.androidpoet.depot.engine

import java.io.File

interface TargetDevice {
    suspend fun probe(): DeviceState

    suspend fun install(apk: File, packageName: String, onAwaitingConfirmation: () -> Unit): String?
}
