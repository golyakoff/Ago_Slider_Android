package net.agolyakov.agoslider.ui.screen.firmware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.agolyakov.agoslider.data.repository.FirmwareRepository
import javax.inject.Inject

@HiltViewModel
class FirmwareViewModel @Inject constructor(
    private val firmwareRepository: FirmwareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FirmwareRepository.UpdateState>(FirmwareRepository.UpdateState.Idle)
    val uiState: StateFlow<FirmwareRepository.UpdateState> = _uiState.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    init {
        checkForUpdates()
    }

    fun checkForUpdates(includePreReleases: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = FirmwareRepository.UpdateState.Checking
            try {
                val state = firmwareRepository.checkForUpdates(includePreReleases)
                _uiState.value = state
            } catch (e: Exception) {
                _uiState.value = FirmwareRepository.UpdateState.Error(e.message ?: "Check failed")
            }
        }
    }

    fun downloadFirmware(release: net.agolyakov.agoslider.data.model.github.GithubRelease) {
        viewModelScope.launch {
            _uiState.value = FirmwareRepository.UpdateState.Downloading(0f)
            val state = firmwareRepository.downloadFirmware(release) { progress ->
                _progress.value = progress
                _statusMessage.value = "Downloading: ${progress.toInt()}%"
            }
            _uiState.value = state
        }
    }

    fun installFirmware(file: java.io.File) {
        viewModelScope.launch {
            _uiState.value = FirmwareRepository.UpdateState.Installing(0f)
            val state = firmwareRepository.installFirmware(file) { progress ->
                _progress.value = progress
                _statusMessage.value = "Installing: ${"%.1f".format(progress)}%"
            }
            _uiState.value = state
        }
    }

    fun performCompleteUpdate(includePreReleases: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = FirmwareRepository.UpdateState.Processing
            _progress.value = 0f
            _statusMessage.value = "Starting update..."

            val result = firmwareRepository.performCompleteUpdate(includePreReleases) { progress, message ->
                _progress.value = progress
                _statusMessage.value = message
            }

            when (result) {
                is FirmwareRepository.UpdateResult.Success -> {
                    _uiState.value = FirmwareRepository.UpdateState.Success
                    _progress.value = 100f
                    _statusMessage.value = "Update successful! ${result.oldVersion} → ${result.newVersion}"
                }
                is FirmwareRepository.UpdateResult.NoUpdateAvailable -> {
                    _uiState.value = FirmwareRepository.UpdateState.NoUpdate(result.currentVersion)
                    _statusMessage.value = "Already on latest version: ${result.currentVersion}"
                }
                is FirmwareRepository.UpdateResult.Error -> {
                    _uiState.value = FirmwareRepository.UpdateState.Error(result.message)
                    _statusMessage.value = "Error: ${result.message}"
                }
                FirmwareRepository.UpdateResult.Cancelled -> {
                    _uiState.value = FirmwareRepository.UpdateState.Idle
                    _statusMessage.value = "Update cancelled"
                }
            }
        }
    }

    fun abortUpdate() {
        viewModelScope.launch {
            try {
                firmwareRepository.abortOta()
                _uiState.value = FirmwareRepository.UpdateState.Idle
                _statusMessage.value = "Update aborted"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to abort: ${e.message}"
            }
        }
    }

    fun resetState() {
        _uiState.value = FirmwareRepository.UpdateState.Idle
        _progress.value = 0f
        _statusMessage.value = ""
    }
}