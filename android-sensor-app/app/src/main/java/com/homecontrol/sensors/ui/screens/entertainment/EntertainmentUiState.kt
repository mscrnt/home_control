package com.homecontrol.sensors.ui.screens.entertainment

import com.homecontrol.sensors.data.model.EntertainmentDevices
import com.homecontrol.sensors.data.model.PS5State
import com.homecontrol.sensors.data.model.ShieldApp
import com.homecontrol.sensors.data.model.ShieldState
import com.homecontrol.sensors.data.model.SonySoundSetting
import com.homecontrol.sensors.data.model.SonyState
import com.homecontrol.sensors.data.model.XboxState

enum class DeviceType {
    SONY, SHIELD, XBOX, PS5
}

data class EntertainmentUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val devices: EntertainmentDevices = EntertainmentDevices(),
    val sonyStates: Map<String, SonyState> = emptyMap(),
    val shieldStates: Map<String, ShieldState> = emptyMap(),
    val shieldApps: Map<String, List<ShieldApp>> = emptyMap(),
    val xboxStates: Map<String, XboxState> = emptyMap(),
    val ps5States: Map<String, PS5State> = emptyMap(),
    val sonySoundSettings: Map<String, List<SonySoundSetting>> = emptyMap(),
    val expandedDevice: Pair<DeviceType, String>? = null,
    val isRefreshing: Boolean = false
)
