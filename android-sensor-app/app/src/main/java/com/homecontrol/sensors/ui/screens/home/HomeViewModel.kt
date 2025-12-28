package com.homecontrol.sensors.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.sensors.data.model.Entity
import com.homecontrol.sensors.data.model.TemperatureRequest
import com.homecontrol.sensors.data.repository.EntityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val entityRepository: EntityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadEntities()
        startPolling()
    }

    fun loadEntities() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.groups.isEmpty(), error = null) }

            entityRepository.getEntities()
                .onSuccess { groups ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            groups = groups,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "Failed to load entities"
                        )
                    }
                }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadEntities()
    }

    fun toggleEntity(entity: Entity) {
        viewModelScope.launch {
            entityRepository.toggleEntity(entity.id)
                .onSuccess {
                    // Refresh to get updated state
                    loadEntities()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to toggle: ${error.message}")
                    }
                }
        }
    }

    fun selectEntity(entity: Entity?) {
        _uiState.update { it.copy(selectedEntity = entity) }
    }

    fun setClimateTemperature(entityId: String, temperature: Double) {
        viewModelScope.launch {
            entityRepository.setClimateTemperature(
                entityId,
                TemperatureRequest(temperature = temperature)
            ).onSuccess {
                loadEntities()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(error = "Failed to set temperature: ${error.message}")
                }
            }
        }
    }

    fun setClimateMode(entityId: String, mode: String) {
        viewModelScope.launch {
            entityRepository.setClimateMode(entityId, mode)
                .onSuccess {
                    loadEntities()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to set mode: ${error.message}")
                    }
                }
        }
    }

    fun setClimateFanMode(entityId: String, fanMode: String) {
        viewModelScope.launch {
            entityRepository.setClimateFanMode(entityId, fanMode)
                .onSuccess {
                    loadEntities()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to set fan mode: ${error.message}")
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
                delay(10_000) // Poll every 10 seconds
                if (!_uiState.value.isLoading && !_uiState.value.isRefreshing) {
                    loadEntities()
                }
            }
        }
    }
}
