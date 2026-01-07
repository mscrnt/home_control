package com.homecontrol.sensors.ui.screens.tesla

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.sensors.data.repository.EntityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TeslaViewModel"

@HiltViewModel
class TeslaViewModel @Inject constructor(
    private val entityRepository: EntityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeslaUiState())
    val uiState: StateFlow<TeslaUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        loadTeslaEntities()
        startPolling()
    }

    fun loadTeslaEntities() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.allEntities.isEmpty(), error = null) }

            entityRepository.searchHAEntities("tessa")
                .onSuccess { entities ->
                    Log.d(TAG, "Loaded ${entities.size} Tesla entities")

                    // Parse online/asleep status from binary sensors
                    val isOnline = entities.find { it.entityId.contains("online") }?.state == "on"
                    val isAsleep = entities.find { it.entityId.contains("asleep") }?.state == "on"

                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            allEntities = entities,
                            isOnline = isOnline,
                            isAsleep = isAsleep,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to load Tesla entities: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = "Failed to load Tesla data: ${error.message}"
                        )
                    }
                }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadTeslaEntities()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(30000) // Poll every 30 seconds
                loadTeslaEntities()
            }
        }
    }

    fun toggleEntity(entityId: String) {
        viewModelScope.launch {
            entityRepository.toggleEntity(entityId)
                .onSuccess {
                    delay(500)
                    loadTeslaEntities()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = "Failed to toggle: ${error.message}") }
                }
        }
    }

    fun pressButton(entityId: String) {
        viewModelScope.launch {
            entityRepository.pressButton(entityId)
                .onSuccess {
                    delay(500)
                    loadTeslaEntities()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = "Failed to press button: ${error.message}") }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
