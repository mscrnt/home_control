package com.homecontrol.sensors.ui.screens.hue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homecontrol.sensors.data.model.HueLight
import com.homecontrol.sensors.data.model.HueRoom
import com.homecontrol.sensors.data.model.HueScene
import com.homecontrol.sensors.data.model.SyncBox
import com.homecontrol.sensors.data.model.SyncBoxStatus
import com.homecontrol.sensors.ui.components.ErrorState
import com.homecontrol.sensors.ui.components.LightSlider
import com.homecontrol.sensors.ui.components.LoadingIndicator
import com.homecontrol.sensors.ui.theme.HomeControlColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HueScreen(
    viewModel: HueViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val pullToRefreshState = rememberPullToRefreshState()

    // Handle pull to refresh
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
        }
    }

    // Update refresh state when loading completes
    LaunchedEffect(uiState.isRefreshing) {
        if (!uiState.isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading && uiState.rooms.isEmpty() -> {
                LoadingIndicator()
            }
            uiState.error != null && uiState.rooms.isEmpty() -> {
                ErrorState(
                    message = uiState.error ?: "Unknown error",
                    onRetry = { viewModel.loadData() }
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullToRefreshState.nestedScrollConnection)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Rooms
                        items(
                            items = uiState.rooms,
                            key = { it.id }
                        ) { room ->
                            RoomCard(
                                room = room,
                                isExpanded = uiState.expandedRoomId == room.id,
                                onToggle = { viewModel.toggleRoom(room.id) },
                                onExpand = { viewModel.expandRoom(room.id) },
                                onLightToggle = { lightId -> viewModel.toggleLight(lightId) },
                                onLightBrightnessChange = { lightId, brightness ->
                                    viewModel.setLightBrightness(lightId, brightness)
                                },
                                onRoomBrightnessChange = { brightness ->
                                    viewModel.setRoomBrightness(room.id, brightness)
                                },
                                onSceneActivate = { sceneId -> viewModel.activateScene(sceneId) }
                            )
                        }

                        // Sync Boxes
                        if (uiState.syncBoxes.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Sync Boxes",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(
                                items = uiState.syncBoxes,
                                key = { it.index }
                            ) { syncBox ->
                                SyncBoxCard(
                                    syncBox = syncBox,
                                    status = uiState.syncBoxStatuses[syncBox.index],
                                    onToggleSync = { sync ->
                                        viewModel.toggleSyncBoxSync(syncBox.index, sync)
                                    },
                                    onModeChange = { mode ->
                                        viewModel.setSyncBoxMode(syncBox.index, mode)
                                    },
                                    onBrightnessChange = { brightness ->
                                        viewModel.setSyncBoxBrightness(syncBox.index, brightness)
                                    },
                                    onInputChange = { input ->
                                        viewModel.setSyncBoxInput(syncBox.index, input)
                                    }
                                )
                            }
                        }
                    }

                    PullToRefreshContainer(
                        state = pullToRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun RoomCard(
    room: HueRoom,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
    onLightToggle: (String) -> Unit,
    onLightBrightnessChange: (String, Int) -> Unit,
    onRoomBrightnessChange: (Int) -> Unit,
    onSceneActivate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var brightnessValue by remember(room.brightness) { mutableFloatStateOf(room.brightness.toFloat()) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackground()
        ),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpand() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (room.on) HomeControlColors.hueLightOn() else HomeControlColors.hueLightOff()
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${room.lights.count { it.on }} of ${room.lights.size} lights on",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = room.on,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = HomeControlColors.hueLightOn(),
                        checkedTrackColor = HomeControlColors.hueLightOn().copy(alpha = 0.5f)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Room brightness slider
            if (room.on) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Room Brightness",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${brightnessValue.roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = brightnessValue,
                        onValueChange = { brightnessValue = it },
                        onValueChangeFinished = { onRoomBrightnessChange(brightnessValue.roundToInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = HomeControlColors.hueLightOn(),
                            activeTrackColor = HomeControlColors.hueLightOn()
                        )
                    )
                }
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    // Scenes
                    room.scenes?.let { scenes ->
                        if (scenes.isNotEmpty()) {
                            Text(
                                text = "Scenes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(scenes) { scene ->
                                    AssistChip(
                                        onClick = { onSceneActivate(scene.id) },
                                        label = { Text(scene.name) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Individual lights
                    Text(
                        text = "Lights",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    room.lights.forEach { light ->
                        LightRow(
                            light = light,
                            onToggle = { onLightToggle(light.id) },
                            onBrightnessChange = { brightness ->
                                onLightBrightnessChange(light.id, brightness)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LightRow(
    light: HueLight,
    onToggle: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var brightnessValue by remember(light.brightness) { mutableFloatStateOf(light.brightness.toFloat()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = light.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                color = if (light.reachable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (!light.reachable) {
                Text(
                    text = "Unreachable",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeControlColors.stateUnavailable()
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Switch(
                checked = light.on,
                onCheckedChange = { onToggle() },
                enabled = light.reachable,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = HomeControlColors.hueLightOn(),
                    checkedTrackColor = HomeControlColors.hueLightOn().copy(alpha = 0.5f)
                )
            )
        }
        if (light.on && light.reachable) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = brightnessValue,
                    onValueChange = { brightnessValue = it },
                    onValueChangeFinished = { onBrightnessChange(brightnessValue.roundToInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = HomeControlColors.hueLightOn(),
                        activeTrackColor = HomeControlColors.hueLightOn()
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${brightnessValue.roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SyncBoxCard(
    syncBox: SyncBox,
    status: SyncBoxStatus?,
    onToggleSync: (Boolean) -> Unit,
    onModeChange: (String) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onInputChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var brightnessValue by remember(status?.brightness ?: 0) {
        mutableFloatStateOf((status?.brightness ?: 0).toFloat())
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackground()
        ),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (status?.syncActive == true) {
                        HomeControlColors.hueLightOn()
                    } else {
                        HomeControlColors.hueLightOff()
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = syncBox.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = status?.let {
                            if (it.syncActive) "Syncing - ${it.mode}" else "Off"
                        } ?: "Loading...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = status?.syncActive == true,
                    onCheckedChange = { onToggleSync(it) },
                    enabled = status != null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = HomeControlColors.hueLightOn(),
                        checkedTrackColor = HomeControlColors.hueLightOn().copy(alpha = 0.5f)
                    )
                )
            }

            if (status != null && status.syncActive) {
                Spacer(modifier = Modifier.height(16.dp))

                // Mode selection
                Text(
                    text = "Mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("video", "music", "game").forEach { mode ->
                        FilterChip(
                            selected = status.mode == mode,
                            onClick = { onModeChange(mode) },
                            label = { Text(mode.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Brightness
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Brightness",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${brightnessValue.roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = brightnessValue,
                    onValueChange = { brightnessValue = it },
                    onValueChangeFinished = { onBrightnessChange(brightnessValue.roundToInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = HomeControlColors.hueLightOn(),
                        activeTrackColor = HomeControlColors.hueLightOn()
                    )
                )

                // Input selection
                status.inputs?.let { inputs ->
                    if (inputs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Input",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(inputs) { input ->
                                FilterChip(
                                    selected = status.input == input.id,
                                    onClick = { onInputChange(input.id) },
                                    label = { Text(input.name) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Tv,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
