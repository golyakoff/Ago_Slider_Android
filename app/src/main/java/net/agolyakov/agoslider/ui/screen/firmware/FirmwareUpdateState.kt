package net.agolyakov.agoslider.ui.screen.firmware

import net.agolyakov.agoslider.data.model.github.GithubRelease
import java.io.File

sealed class FirmwareUpdateState {
    object Idle : FirmwareUpdateState()
    object Checking : FirmwareUpdateState()
    data class UpdateAvailable(val release: GithubRelease, val currentVersion: String) : FirmwareUpdateState()
    data class NoUpdate(val currentVersion: String) : FirmwareUpdateState()
    data class Downloading(val progress: Float) : FirmwareUpdateState()
    data class ReadyToInstall(val file: File, val release: GithubRelease) : FirmwareUpdateState()
    data class Installing(val progress: Float) : FirmwareUpdateState()
    object Success : FirmwareUpdateState()
    data class Error(val message: String) : FirmwareUpdateState()
    object Processing : FirmwareUpdateState()
}