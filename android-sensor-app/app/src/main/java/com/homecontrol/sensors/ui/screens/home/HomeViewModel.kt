package com.homecontrol.sensors.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.sensors.data.model.ClimateEntity
import com.homecontrol.sensors.data.model.FanEntity
import com.homecontrol.sensors.data.model.FilteredAutomation
import com.homecontrol.sensors.data.repository.EntityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
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
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.automations.isEmpty() && it.fans.isEmpty() && it.climates.isEmpty(), error = null) }

            // Load automations, fans, and climates in parallel
            val automationsDeferred = async { entityRepository.getFilteredAutomations() }
            val fansDeferred = async { entityRepository.getFans() }
            val climatesDeferred = async { entityRepository.getClimateEntities() }

            val automationsResult = automationsDeferred.await()
            val fansResult = fansDeferred.await()
            val climatesResult = climatesDeferred.await()

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isRefreshing = false,
                    automations = automationsResult.getOrDefault(state.automations),
                    fans = fansResult.getOrDefault(state.fans),
                    climates = climatesResult.getOrDefault(state.climates),
                    error = automationsResult.exceptionOrNull()?.message
                        ?: fansResult.exceptionOrNull()?.message
                        ?: climatesResult.exceptionOrNull()?.message
                )
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadData()
    }

    fun pressAutomation(automation: FilteredAutomation) {
        viewModelScope.launch {
            val result = if (automation.triggerType == "button") {
                // For buttons, we need to press them
                entityRepository.pressButton(automation.entityId)
            } else {
                // For switches, toggle them
                entityRepository.toggleEntity(automation.entityId)
            }

            result.onSuccess {
                loadData()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(error = "Failed to activate: ${error.message}")
                }
            }
        }
    }

    fun toggleFan(fan: FanEntity) {
        viewModelScope.launch {
            entityRepository.toggleEntity(fan.entityId)
                .onSuccess {
                    loadData()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to toggle fan: ${error.message}")
                    }
                }
        }
    }

    fun setClimateFanMode(climate: ClimateEntity, fanMode: String) {
        viewModelScope.launch {
            entityRepository.setClimateFanMode(climate.entityId, fanMode)
                .onSuccess {
                    loadData()
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
}
