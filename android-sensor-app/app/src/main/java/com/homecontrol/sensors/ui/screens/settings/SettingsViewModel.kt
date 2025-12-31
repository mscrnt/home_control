package com.homecontrol.sensors.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.sensors.data.repository.AppMode
import com.homecontrol.sensors.data.repository.AppSettings
import com.homecontrol.sensors.data.repository.SettingsRepository
import com.homecontrol.sensors.data.repository.ThemeMode
import com.homecontrol.sensors.data.repository.UpdateRepository
import com.homecontrol.sensors.data.repository.UpdateManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val serverUrlInput: String = "",
    val showRestartRequired: Boolean = false,
    // Update state
    val currentVersion: String = "",
    val isCheckingForUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Float = 0f,
    val availableUpdate: UpdateManifest? = null,
    val updateError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(
        currentVersion = updateRepository.getCurrentVersion()
    ))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observeUpdateState()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        serverUrlInput = settings.serverUrl,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun observeUpdateState() {
        viewModelScope.launch {
            updateRepository.updateState.collect { updateState ->
                _uiState.update {
                    it.copy(
                        isCheckingForUpdate = updateState.isChecking,
                        isDownloadingUpdate = updateState.isDownloading,
                        downloadProgress = updateState.downloadProgress,
                        availableUpdate = updateState.availableUpdate,
                        updateError = updateState.error
                    )
                }
            }
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            updateRepository.checkForUpdate()
        }
    }

    fun installUpdate() {
        val update = _uiState.value.availableUpdate ?: return
        viewModelScope.launch {
            updateRepository.downloadAndInstall(update)
        }
    }

    fun clearUpdateError() {
        updateRepository.clearError()
    }

    fun updateServerUrl(url: String) {
        _uiState.update { it.copy(serverUrlInput = url) }
    }

    fun saveServerUrl() {
        val url = _uiState.value.serverUrlInput.trim()
        if (url.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    // Ensure URL ends with /
                    val normalizedUrl = if (url.endsWith("/")) url else "$url/"
                    settingsRepository.setServerUrl(normalizedUrl)
                    _uiState.update {
                        it.copy(
                            serverUrlInput = normalizedUrl,
                            showRestartRequired = true
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(error = "Failed to save server URL: ${e.message}")
                    }
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setAppMode(mode: AppMode) {
        viewModelScope.launch {
            settingsRepository.setAppMode(mode)
            _uiState.update { it.copy(showRestartRequired = true) }
        }
    }

    fun setIdleTimeout(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setIdleTimeout(seconds)
        }
    }

    fun setProximityTimeoutMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setProximityTimeoutMinutes(minutes)
        }
    }

    fun setAdaptiveBrightness(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAdaptiveBrightness(enabled)
        }
    }

    fun setShowNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowNotifications(enabled)
        }
    }

    fun setUse24HourFormat(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUse24HourFormat(enabled)
        }
    }

    fun dismissRestartRequired() {
        _uiState.update { it.copy(showRestartRequired = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
