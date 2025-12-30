package com.homecontrol.sensors.ui.screens.hue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.sensors.data.repository.HueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HueViewModel @Inject constructor(
    private val hueRepository: HueRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HueUiState())
    val uiState: StateFlow<HueUiState> = _uiState.asStateFlow()

    init {
        loadData()
        startPolling()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.rooms.isEmpty(), error = null) }

            // Load rooms
            hueRepository.getRooms()
                .onSuccess { rooms ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            rooms = rooms,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "Failed to load rooms"
                        )
                    }
                }

            // Load sync boxes
            hueRepository.getSyncBoxes()
                .onSuccess { boxes ->
                    _uiState.update { it.copy(syncBoxes = boxes) }
                    // Load status for each sync box
                    boxes.forEach { box ->
                        loadSyncBoxStatus(box.index)
                    }
                }
        }
    }

    private fun loadSyncBoxStatus(index: Int) {
        viewModelScope.launch {
            hueRepository.getSyncBoxStatus(index)
                .onSuccess { status ->
                    android.util.Log.d("HueViewModel", "SyncBox $index status loaded: syncActive=${status.syncActive}, mode=${status.mode}")
                    _uiState.update {
                        it.copy(syncBoxStatuses = it.syncBoxStatuses + (index to status))
                    }
                }
                .onFailure { error ->
                    android.util.Log.e("HueViewModel", "Failed to load SyncBox $index status: ${error.message}", error)
                }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadData()
    }

    fun toggleRoom(roomId: String) {
        viewModelScope.launch {
            hueRepository.toggleGroup(roomId)
                .onSuccess { loadData() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to toggle room: ${error.message}")
                    }
                }
        }
    }

    fun toggleLight(lightId: String) {
        viewModelScope.launch {
            hueRepository.toggleLight(lightId)
                .onSuccess { loadData() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to toggle light: ${error.message}")
                    }
                }
        }
    }

    fun setLightBrightness(lightId: String, brightness: Int) {
        viewModelScope.launch {
            hueRepository.setLightBrightness(lightId, brightness)
                .onSuccess { loadData() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to set brightness: ${error.message}")
                    }
                }
        }
    }

    fun setRoomBrightness(roomId: String, brightness: Int) {
        viewModelScope.launch {
            hueRepository.setGroupBrightness(roomId, brightness)
                .onSuccess { loadData() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to set brightness: ${error.message}")
                    }
                }
        }
    }

    fun activateScene(sceneId: String) {
        viewModelScope.launch {
            hueRepository.activateScene(sceneId)
                .onSuccess { loadData() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to activate scene: ${error.message}")
                    }
                }
        }
    }

    fun expandRoom(roomId: String?) {
        _uiState.update {
            it.copy(expandedRoomId = if (it.expandedRoomId == roomId) null else roomId)
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
    }

    // Sync Box controls
    fun toggleSyncBoxSync(index: Int, sync: Boolean) {
        viewModelScope.launch {
            hueRepository.setSyncBoxSync(index, sync)
                .onSuccess { loadSyncBoxStatus(index) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to toggle sync: ${error.message}")
                    }
                }
        }
    }

    fun setSyncBoxMode(index: Int, mode: String) {
        viewModelScope.launch {
            hueRepository.setSyncBoxMode(index, mode)
                .onSuccess { loadSyncBoxStatus(index) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to set mode: ${error.message}")
                    }
                }
        }
    }

    fun setSyncBoxBrightness(index: Int, brightness: Int) {
        viewModelScope.launch {
            hueRepository.setSyncBoxBrightness(index, brightness)
                .onSuccess { loadSyncBoxStatus(index) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to set brightness: ${error.message}")
                    }
                }
        }
    }

    fun setSyncBoxInput(index: Int, input: String) {
        viewModelScope.launch {
            hueRepository.setSyncBoxInput(index, input)
                .onSuccess { loadSyncBoxStatus(index) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to set input: ${error.message}")
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(5_000) // Poll every 5 seconds
                if (!_uiState.value.isLoading && !_uiState.value.isRefreshing) {
                    loadData()
                }
            }
        }
    }
}
