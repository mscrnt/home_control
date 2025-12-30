package com.homecontrol.sensors.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homecontrol.sensors.ui.components.EntityModal
import com.homecontrol.sensors.ui.components.ErrorState
import com.homecontrol.sensors.ui.components.GroupCard
import com.homecontrol.sensors.ui.components.LoadingIndicator

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error in snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading && uiState.groups.isEmpty() -> {
                LoadingIndicator()
            }
            uiState.error != null && uiState.groups.isEmpty() -> {
                ErrorState(
                    message = uiState.error ?: "Unknown error",
                    onRetry = { viewModel.loadEntities() }
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = uiState.groups,
                        key = { it.name }
                    ) { group ->
                        GroupCard(
                            group = group,
                            onEntityToggle = { entity -> viewModel.toggleEntity(entity) },
                            onEntityClick = { entity -> viewModel.selectEntity(entity) }
                        )
                    }
                }
            }
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Entity modal
        uiState.selectedEntity?.let { entity ->
            EntityModal(
                entity = entity,
                onDismiss = { viewModel.selectEntity(null) },
                onTemperatureChange = { temp ->
                    viewModel.setClimateTemperature(entity.id, temp)
                },
                onModeChange = { mode ->
                    viewModel.setClimateMode(entity.id, mode)
                },
                onFanModeChange = { fanMode ->
                    viewModel.setClimateFanMode(entity.id, fanMode)
                }
            )
        }
    }
}
