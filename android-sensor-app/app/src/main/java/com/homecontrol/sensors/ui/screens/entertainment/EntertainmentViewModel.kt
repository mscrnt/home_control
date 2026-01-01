package com.homecontrol.sensors.ui.screens.entertainment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.sensors.data.repository.EntertainmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EntertainmentViewModel @Inject constructor(
    private val entertainmentRepository: EntertainmentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntertainmentUiState())
    val uiState: StateFlow<EntertainmentUiState> = _uiState.asStateFlow()

    init {
        loadDevices()
    }

    fun loadDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.devices.sony.isEmpty() && it.devices.shield.isEmpty()) }

            entertainmentRepository.getDevices()
                .onSuccess { devices ->
                    _uiState.update {
                        it.copy(
                            devices = devices,
                            isLoading = false,
                            error = null
                        )
                    }
                    // Load states for all devices
                    loadAllDeviceStates(devices)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load devices: ${error.message}"
                        )
                    }
                }
        }
    }

    private suspend fun loadAllDeviceStates(devices: com.homecontrol.sensors.data.model.EntertainmentDevices) {
        // Load Sony states
        devices.sony.forEach { device ->
            loadSonyState(device.name)
        }
        // Load Shield states
        devices.shield.forEach { device ->
            loadShieldState(device.name)
        }
        // Load Xbox states
        devices.xbox.forEach { device ->
            loadXboxState(device.name)
        }
        // Load PS5 states
        devices.ps5.forEach { device ->
            loadPS5State(device.name)
        }
    }

    private suspend fun loadSonyState(name: String) {
        entertainmentRepository.getSonyState(name)
            .onSuccess { state ->
                _uiState.update {
                    it.copy(sonyStates = it.sonyStates + (name to state))
                }
            }
    }

    private suspend fun loadShieldState(name: String) {
        entertainmentRepository.getShieldState(name)
            .onSuccess { state ->
                _uiState.update {
                    it.copy(shieldStates = it.shieldStates + (name to state))
                }
            }
    }

    private suspend fun loadXboxState(name: String) {
        entertainmentRepository.getXboxState(name)
            .onSuccess { state ->
                _uiState.update {
                    it.copy(xboxStates = it.xboxStates + (name to state))
                }
            }
    }

    private suspend fun loadPS5State(name: String) {
        entertainmentRepository.getPS5State(name)
            .onSuccess { state ->
                _uiState.update {
                    it.copy(ps5States = it.ps5States + (name to state))
                }
            }
    }

    fun toggleExpanded(type: DeviceType, name: String) {
        _uiState.update {
            val current = it.expandedDevice
            if (current?.first == type && current.second == name) {
                it.copy(expandedDevice = null)
            } else {
                it.copy(expandedDevice = type to name)
            }
        }
    }

    fun toggleSonyPower(name: String) {
        viewModelScope.launch {
            val currentState = _uiState.value.sonyStates[name] ?: return@launch
            val newPower = !currentState.power

            entertainmentRepository.sonyPower(name, newPower)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            sonyStates = it.sonyStates + (name to currentState.copy(power = newPower))
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to toggle power: ${error.message}")
                    }
                }
        }
    }

    fun setSonyVolume(name: String, volume: Int) {
        viewModelScope.launch {
            entertainmentRepository.sonyVolume(name, volume)
                .onSuccess {
                    val currentState = _uiState.value.sonyStates[name] ?: return@onSuccess
                    _uiState.update {
                        it.copy(
                            sonyStates = it.sonyStates + (name to currentState.copy(volume = volume))
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to set volume: ${error.message}")
                    }
                }
        }
    }

    fun toggleShieldPower(name: String) {
        viewModelScope.launch {
            val currentState = _uiState.value.shieldStates[name] ?: return@launch
            val newPower = !currentState.power

            entertainmentRepository.shieldPower(name, newPower)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            shieldStates = it.shieldStates + (name to currentState.copy(power = newPower))
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to toggle power: ${error.message}")
                    }
                }
        }
    }

    fun toggleXboxPower(name: String) {
        viewModelScope.launch {
            val currentState = _uiState.value.xboxStates[name] ?: return@launch
            val newPower = !currentState.power

            entertainmentRepository.xboxPower(name, newPower)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            xboxStates = it.xboxStates + (name to currentState.copy(power = newPower))
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to toggle power: ${error.message}")
                    }
                }
        }
    }

    fun togglePS5Power(name: String) {
        viewModelScope.launch {
            val currentState = _uiState.value.ps5States[name] ?: return@launch
            val newPower = !currentState.power

            entertainmentRepository.ps5Power(name, newPower)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            ps5States = it.ps5States + (name to currentState.copy(power = newPower))
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to toggle power: ${error.message}")
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadDevices()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
