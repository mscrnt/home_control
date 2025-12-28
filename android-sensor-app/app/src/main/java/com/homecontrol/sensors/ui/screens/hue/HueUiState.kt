package com.homecontrol.sensors.ui.screens.hue

import com.homecontrol.sensors.data.model.HueRoom
import com.homecontrol.sensors.data.model.SyncBox
import com.homecontrol.sensors.data.model.SyncBoxStatus

data class HueUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val rooms: List<HueRoom> = emptyList(),
    val expandedRoomId: String? = null,
    val syncBoxes: List<SyncBox> = emptyList(),
    val syncBoxStatuses: Map<Int, SyncBoxStatus> = emptyMap()
)
