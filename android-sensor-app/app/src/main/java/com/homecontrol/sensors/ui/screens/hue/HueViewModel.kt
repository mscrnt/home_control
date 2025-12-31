package com.homecontrol.sensors.ui.screens.hue

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.sensors.data.api.HueEventClient
import com.homecontrol.sensors.data.api.HueSSEEvent
import com.homecontrol.sensors.data.api.HueSSEState
import com.homecontrol.sensors.data.repository.HueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HueViewModel @Inject constructor(
    private val hueRepository: HueRepository,
    private val hueEventClient: HueEventClient
) : ViewModel() {

    companion object {
        private const val TAG = "HueViewModel"
        private const val SSE_DEBOUNCE_MS = 1000L // Debounce SSE-triggered reloads
    }

    private val _uiState = MutableStateFlow(HueUiState())
    val uiState: StateFlow<HueUiState> = _uiState.asStateFlow()

    private var sseJob: Job? = null
    private var fallbackPollingJob: Job? = null
    private var pendingReloadJob: Job? = null
    private var lastReloadTime = 0L

    init {
        loadData()
        startSSE()
    }

    override fun onCleared() {
        super.onCleared()
        hueEventClient.disconnect()
    }

    private fun startSSE() {
        // Connect to SSE
        hueEventClient.connect()

        // Listen for events
        sseJob = viewModelScope.launch {
            hueEventClient.events.collect { event ->
                Log.d(TAG, "SSE event received: $event")
                when (event) {
                    is HueSSEEvent.Connected -> {
                        Log.d(TAG, "SSE connected, refreshing data")
                        stopFallbackPolling()
                        loadData()
                    }
                    is HueSSEEvent.Disconnected -> {
                        Log.d(TAG, "SSE disconnected, starting fallback polling")
                        startFallbackPolling()
                    }
                    is HueSSEEvent.LightUpdate,
                    is HueSSEEvent.GroupUpdate,
                    is HueSSEEvent.SceneUpdate,
                    is HueSSEEvent.DataChanged -> {
                        // Debounce SSE-triggered reloads to prevent API flooding
                        debouncedReload()
                    }
                }
            }
        }

        // Also monitor connection state
        viewModelScope.launch {
            hueEventClient.state.collect { state ->
                Log.d(TAG, "SSE state: $state")
                _uiState.update { it.copy(sseConnected = state == HueSSEState.CONNECTED) }
            }
        }
    }

    private fun startFallbackPolling() {
        if (fallbackPollingJob?.isActive == true) return

        Log.d(TAG, "Starting fallback polling")
        fallbackPollingJob = viewModelScope.launch {
            while (true) {
                delay(5_000)
                if (!_uiState.value.isLoading && !_uiState.value.isRefreshing) {
                    loadData()
                }
            }
        }
    }

    private fun stopFallbackPolling() {
        fallbackPollingJob?.cancel()
        fallbackPollingJob = null
    }

    private fun debouncedReload() {
        val now = System.currentTimeMillis()

        // Cancel any pending reload
        pendingReloadJob?.cancel()

        // If we recently loaded, schedule a delayed reload
        val timeSinceLastReload = now - lastReloadTime
        if (timeSinceLastReload < SSE_DEBOUNCE_MS) {
            pendingReloadJob = viewModelScope.launch {
                delay(SSE_DEBOUNCE_MS - timeSinceLastReload)
                Log.d(TAG, "Debounced reload triggered")
                loadData()
            }
        } else {
            // Enough time has passed, reload immediately
            Log.d(TAG, "Immediate SSE-triggered reload")
            loadData()
        }
    }

    fun loadData() {
        lastReloadTime = System.currentTimeMillis()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.rooms.isEmpty(), error = null) }

            // Load rooms
            hueRepository.getRooms()
                .onSuccess { rooms ->
                    // Find "Living Room" index for default tab selection
                    val filteredRooms = rooms.filter { it.type == "Room" || it.type == "Zone" }
                    val livingRoomIndex = filteredRooms.indexOfFirst {
                        it.name.equals("Living Room", ignoreCase = true)
                    }.takeIf { it >= 0 } ?: 0

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            rooms = rooms,
                            error = null,
                            // Only set default tab on initial load (when we had no rooms before)
                            selectedTabIndex = if (it.rooms.isEmpty()) livingRoomIndex else it.selectedTabIndex
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
                    Log.d(TAG, "SyncBox $index status loaded: syncActive=${status.syncActive}, mode=${status.mode}")
                    _uiState.update {
                        it.copy(syncBoxStatuses = it.syncBoxStatuses + (index to status))
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to load SyncBox $index status: ${error.message}", error)
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
                .onSuccess { /* SSE will trigger refresh */ }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to toggle room: ${error.message}")
                    }
                    loadData() // Fallback refresh on error
                }
        }
    }

    fun toggleLight(lightId: String) {
        viewModelScope.launch {
            hueRepository.toggleLight(lightId)
                .onSuccess { /* SSE will trigger refresh */ }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to toggle light: ${error.message}")
                    }
                    loadData()
                }
        }
    }

    fun setLightBrightness(lightId: String, brightness: Int) {
        viewModelScope.launch {
            hueRepository.setLightBrightness(lightId, brightness)
                .onSuccess { /* SSE will trigger refresh */ }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to set brightness: ${error.message}")
                    }
                    loadData()
                }
        }
    }

    fun setRoomBrightness(roomId: String, brightness: Int) {
        viewModelScope.launch {
            hueRepository.setGroupBrightness(roomId, brightness)
                .onSuccess { /* SSE will trigger refresh */ }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to set brightness: ${error.message}")
                    }
                    loadData()
                }
        }
    }

    fun activateScene(sceneId: String) {
        viewModelScope.launch {
            hueRepository.activateScene(sceneId)
                .onSuccess { /* SSE will trigger refresh */ }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to activate scene: ${error.message}")
                    }
                    loadData()
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
}
