package com.homecontrol.sensors.ui.screens.home

import com.homecontrol.sensors.data.model.ClimateEntity
import com.homecontrol.sensors.data.model.FanEntity
import com.homecontrol.sensors.data.model.FilteredAutomation

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val automations: List<FilteredAutomation> = emptyList(),
    val fans: List<FanEntity> = emptyList(),
    val climates: List<ClimateEntity> = emptyList()
)
