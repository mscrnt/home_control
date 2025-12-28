package com.homecontrol.sensors.ui.screens.home

import com.homecontrol.sensors.data.model.Entity
import com.homecontrol.sensors.data.model.EntityGroup

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val groups: List<EntityGroup> = emptyList(),
    val selectedEntity: Entity? = null
)
