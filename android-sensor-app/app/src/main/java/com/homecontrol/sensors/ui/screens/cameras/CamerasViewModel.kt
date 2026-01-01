package com.homecontrol.sensors.ui.screens.cameras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.sensors.data.model.Camera
import com.homecontrol.sensors.data.repository.CameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CamerasViewModel @Inject constructor(
    private val cameraRepository: CameraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CamerasUiState())
    val uiState: StateFlow<CamerasUiState> = _uiState.asStateFlow()

    init {
        loadCameras()
        startAutoRefresh()
    }

    fun loadCameras() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.cameras.isEmpty()) }

            cameraRepository.getCameras()
                .onSuccess { cameras ->
                    // Default to front_door camera if available
                    val frontDoorIndex = cameras.indexOfFirst {
                        it.name.equals("front_door", ignoreCase = true)
                    }.takeIf { it >= 0 } ?: 0

                    _uiState.update {
                        it.copy(
                            cameras = cameras,
                            primaryCameraIndex = if (it.primaryCameraIndex == 0) frontDoorIndex else it.primaryCameraIndex,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load cameras: ${error.message}"
                        )
                    }
                }
        }
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(30_000) // Refresh every 30 seconds
                if (_uiState.value.selectedCamera == null) {
                    // Only refresh if not viewing full screen
                    _uiState.update { it.copy(refreshTrigger = it.refreshTrigger + 1) }
                }
            }
        }
    }

    fun selectCamera(camera: Camera) {
        _uiState.update { it.copy(selectedCamera = camera) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedCamera = null) }
    }

    fun setPrimaryCamera(index: Int) {
        _uiState.update { it.copy(primaryCameraIndex = index) }
    }

    fun refreshSnapshots() {
        _uiState.update { it.copy(refreshTrigger = it.refreshTrigger + 1) }
    }

    fun getSnapshotUrl(cameraName: String): String {
        return cameraRepository.getSnapshotUrl(cameraName)
    }

    fun getStreamUrl(cameraName: String): String {
        return cameraRepository.getStreamUrl(cameraName)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // Push-to-talk functionality
    fun startRecording() {
        _uiState.update { it.copy(isRecordingAudio = true) }
    }

    fun stopRecordingAndSend(cameraName: String, pcmData: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRecordingAudio = false) }

            cameraRepository.postAudio(cameraName, pcmData)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to send audio: ${error.message}")
                    }
                }
        }
    }

    fun cancelRecording() {
        _uiState.update { it.copy(isRecordingAudio = false) }
    }
}
