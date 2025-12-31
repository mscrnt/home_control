package com.homecontrol.sensors.ui.screens.hue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homecontrol.sensors.data.model.HueLight
import com.homecontrol.sensors.data.model.HueRoom
import com.homecontrol.sensors.data.model.HueScene
import com.homecontrol.sensors.data.model.SyncBox
import com.homecontrol.sensors.data.model.SyncBoxStatus
import com.homecontrol.sensors.ui.components.ErrorState
import com.homecontrol.sensors.ui.components.LoadingIndicator
import com.homecontrol.sensors.ui.theme.HomeControlColors
import kotlin.math.roundToInt

// Hue brand colors
private val HueOrange = Color(0xFFffa726)
private val HuePurple = Color(0xFFa855f7)
private val SyncGreen = Color(0xFF2ecc71)
private val SyncRed = Color(0xFFe74c3c)

@Composable
fun HueScreen(
    viewModel: HueViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
                val rooms = uiState.rooms.filter { it.type == "Room" || it.type == "Zone" }
                val hasEntertainment = uiState.syncBoxes.isNotEmpty()

                // Calculate total tabs
                val totalTabs = rooms.size + if (hasEntertainment) 1 else 0

                // Ensure selectedTabIndex is valid
                val selectedTabIndex = uiState.selectedTabIndex.coerceIn(0, maxOf(0, totalTabs - 1))

                Column(modifier = Modifier.fillMaxSize()) {
                    // Tab bar (fixed at top, outside pull-to-refresh)
                    HueTabBar(
                        rooms = rooms,
                        hasEntertainment = hasEntertainment,
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = { viewModel.selectTab(it) }
                    )

                    // Content
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        if (selectedTabIndex < rooms.size) {
                            // Room content
                            val room = rooms[selectedTabIndex]
                            RoomContent(
                                room = room,
                                onToggleRoom = { viewModel.toggleRoom(room.id) },
                                onRoomBrightnessChange = { brightness ->
                                    viewModel.setRoomBrightness(room.id, brightness)
                                },
                                onSceneActivate = { sceneId -> viewModel.activateScene(sceneId) },
                                onLightToggle = { lightId -> viewModel.toggleLight(lightId) },
                                onLightBrightnessChange = { lightId, brightness ->
                                    viewModel.setLightBrightness(lightId, brightness)
                                }
                            )
                        } else if (hasEntertainment) {
                            // Entertainment Areas content
                            EntertainmentContent(
                                syncBoxes = uiState.syncBoxes,
                                syncBoxStatuses = uiState.syncBoxStatuses,
                                onToggleSync = { index, sync ->
                                    viewModel.toggleSyncBoxSync(index, sync)
                                },
                                onModeChange = { index, mode ->
                                    viewModel.setSyncBoxMode(index, mode)
                                },
                                onBrightnessChange = { index, brightness ->
                                    viewModel.setSyncBoxBrightness(index, brightness)
                                },
                                onInputChange = { index, input ->
                                    viewModel.setSyncBoxInput(index, input)
                                }
                            )
                        }
                    }
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
private fun HueTabBar(
    rooms: List<HueRoom>,
    hasEntertainment: Boolean,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(HomeControlColors.cardBackground())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Room tabs
        rooms.forEachIndexed { index, room ->
            HueTab(
                icon = getRoomIcon(room.roomClass ?: room.type),
                label = room.name,
                isSelected = selectedTabIndex == index,
                isEntertainment = false,
                onClick = { onTabSelected(index) }
            )
        }

        // Entertainment Areas tab
        if (hasEntertainment) {
            HueTab(
                icon = "🎬",
                label = "Entertainment Areas",
                isSelected = selectedTabIndex == rooms.size,
                isEntertainment = true,
                onClick = { onTabSelected(rooms.size) }
            )
        }
    }
}

@Composable
private fun HueTab(
    icon: String,
    label: String,
    isSelected: Boolean,
    isEntertainment: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected && isEntertainment -> HuePurple
        isSelected -> HueOrange
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> Color.White
        isEntertainment -> HuePurple.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) null else BorderStroke(2.dp, Color.Transparent)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RoomContent(
    room: HueRoom,
    onToggleRoom: () -> Unit,
    onRoomBrightnessChange: (Int) -> Unit,
    onSceneActivate: (String) -> Unit,
    onLightToggle: (String) -> Unit,
    onLightBrightnessChange: (String, Int) -> Unit
) {
    var brightnessValue by remember(room.brightness) { mutableFloatStateOf(room.brightness.toFloat()) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Left column - Power button and brightness
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Power button
            PowerButton(
                isOn = room.on,
                roomName = room.name,
                onClick = onToggleRoom,
                modifier = Modifier.weight(1f)
            )

            // Brightness slider
            BrightnessControl(
                label = "Brightness",
                value = brightnessValue,
                onValueChange = { brightnessValue = it },
                onValueChangeFinished = { onRoomBrightnessChange(brightnessValue.roundToInt()) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Right column - Scenes and Lights
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Scenes button
            room.scenes?.let { scenes ->
                if (scenes.isNotEmpty()) {
                    ScenesSection(
                        sceneCount = scenes.size,
                        scenes = scenes,
                        onSceneActivate = onSceneActivate
                    )
                }
            }

            // Lights section
            LightsSection(
                lights = room.lights,
                onLightToggle = onLightToggle,
                onLightBrightnessChange = onLightBrightnessChange
            )
        }
    }
}

@Composable
private fun PowerButton(
    isOn: Boolean,
    roomName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier
                .size(280.dp)
                .clip(CircleShape)
                .clickable { onClick() },
            shape = CircleShape,
            color = if (isOn) HueOrange else HomeControlColors.cardBackground(),
            shadowElevation = if (isOn) 16.dp else 4.dp,
            tonalElevation = if (isOn) 8.dp else 0.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = "Power",
                    modifier = Modifier.size(140.dp),
                    tint = if (isOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isOn) "Turn off $roomName" else "Turn on $roomName",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BrightnessControl(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HomeControlColors.cardBackground()),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF0f3460),
                                    HueOrange
                                )
                            )
                        )
                ) {
                    Slider(
                        value = value,
                        onValueChange = onValueChange,
                        onValueChangeFinished = onValueChangeFinished,
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        )
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "${value.roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScenesSection(
    sceneCount: Int,
    scenes: List<HueScene>,
    onSceneActivate: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HomeControlColors.cardBackground()),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "🎬", fontSize = 20.sp)
                    Text(
                        text = "Scenes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = HueOrange
                ) {
                    Text(
                        text = "$sceneCount",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                scenes.forEach { scene ->
                    val isActive = scene.active
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSceneActivate(scene.id) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActive) HueOrange.copy(alpha = 0.2f) else HomeControlColors.cardBackground(),
                        border = BorderStroke(
                            width = if (isActive) 2.dp else 1.dp,
                            color = if (isActive) HueOrange else HomeControlColors.cardBorder()
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isActive) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HueOrange
                                )
                            }
                            Text(
                                text = scene.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isActive) HueOrange else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LightsSection(
    lights: List<HueLight>,
    onLightToggle: (String) -> Unit,
    onLightBrightnessChange: (String, Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HomeControlColors.cardBackground()),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "LIGHTS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(300.dp)
            ) {
                items(lights) { light ->
                    LightCard(
                        light = light,
                        onClick = { onLightToggle(light.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LightCard(
    light: HueLight,
    onClick: () -> Unit
) {
    val backgroundColor = if (light.on) {
        HueOrange.copy(alpha = 0.15f)
    } else {
        HomeControlColors.cardBackground()
    }
    val borderColor = if (light.on) {
        HueOrange.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "💡",
                fontSize = 32.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = light.name,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (light.on) "${light.brightness}%" else "Off",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ===== Entertainment Areas Content =====

@Composable
private fun EntertainmentContent(
    syncBoxes: List<SyncBox>,
    syncBoxStatuses: Map<Int, SyncBoxStatus>,
    onToggleSync: (Int, Boolean) -> Unit,
    onModeChange: (Int, String) -> Unit,
    onBrightnessChange: (Int, Int) -> Unit,
    onInputChange: (Int, String) -> Unit
) {
    var selectedSyncBoxIndex by remember { mutableIntStateOf(0) }
    val selectedIndex = selectedSyncBoxIndex.coerceIn(0, maxOf(0, syncBoxes.size - 1))

    if (syncBoxes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No Sync Boxes configured",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val currentBox = syncBoxes.getOrNull(selectedIndex) ?: return
    val currentStatus = syncBoxStatuses[currentBox.index]
    val isSyncing = currentStatus?.syncActive == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sync Box Header with selector and toggle
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = HomeControlColors.cardBackground()),
            border = BorderStroke(1.dp, HomeControlColors.cardBorder())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sync box selector
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        syncBoxes.forEachIndexed { index, box ->
                            val boxStatus = syncBoxStatuses[box.index]
                            val boxSyncing = boxStatus?.syncActive == true

                            SyncBoxSelector(
                                name = box.name,
                                isSelected = index == selectedIndex,
                                isSyncing = boxSyncing,
                                onClick = { selectedSyncBoxIndex = index }
                            )
                        }
                    }

                    // Start/Stop Sync button
                    Button(
                        onClick = { onToggleSync(currentBox.index, !isSyncing) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSyncing) SyncRed else SyncGreen
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isSyncing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSyncing) "Stop Sync" else "Start Sync",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Split layout for entertainment area and controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left column - Entertainment Areas
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EntertainmentAreaGrid(
                    status = currentStatus,
                    onAreaSelect = { /* TODO: Add area selection */ }
                )
            }

            // Right column - HDMI Input and Sync Mode
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // HDMI Input
                HdmiInputGrid(
                    status = currentStatus,
                    onInputSelect = { onInputChange(currentBox.index, it) }
                )

                // Sync Mode
                SyncModeGrid(
                    status = currentStatus,
                    onModeSelect = { onModeChange(currentBox.index, it) }
                )
            }
        }
    }
}

@Composable
private fun SyncBoxSelector(
    name: String,
    isSelected: Boolean,
    isSyncing: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isSyncing -> HuePurple
        isSelected -> HueOrange
        else -> Color.Transparent
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) HomeControlColors.cardBackground() else Color.Transparent,
        border = BorderStroke(3.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (isSyncing) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = HuePurple.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Syncing",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = HuePurple
                    )
                }
            }
        }
    }
}

@Composable
private fun EntertainmentAreaGrid(
    status: SyncBoxStatus?,
    onAreaSelect: (String) -> Unit
) {
    val groups = status?.hue?.groups ?: emptyMap()
    val currentGroupId = status?.execution?.hueTarget ?: ""

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HomeControlColors.cardBackground()),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "ENTERTAINMENT AREA",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(200.dp)
            ) {
                items(groups.entries.toList()) { (id, group) ->
                    SyncBoxTile(
                        icon = "🎬",
                        name = group.name,
                        info = "${group.numLights} lights",
                        isSelected = id == currentGroupId,
                        onClick = { onAreaSelect(id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HdmiInputGrid(
    status: SyncBoxStatus?,
    onInputSelect: (String) -> Unit
) {
    val currentInput = status?.execution?.hdmiSource ?: "input1"
    val inputs = status?.inputs ?: listOf(
        com.homecontrol.sensors.data.model.SyncBoxInput("input1", "HDMI 1", ""),
        com.homecontrol.sensors.data.model.SyncBoxInput("input2", "HDMI 2", ""),
        com.homecontrol.sensors.data.model.SyncBoxInput("input3", "HDMI 3", ""),
        com.homecontrol.sensors.data.model.SyncBoxInput("input4", "HDMI 4", "")
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HomeControlColors.cardBackground()),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "HDMI INPUT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(180.dp)
            ) {
                items(inputs) { input ->
                    SyncBoxTile(
                        icon = "📺",
                        name = input.name,
                        info = input.id.replace("input", "HDMI "),
                        isSelected = input.id == currentInput,
                        onClick = { onInputSelect(input.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncModeGrid(
    status: SyncBoxStatus?,
    onModeSelect: (String) -> Unit
) {
    val currentMode = status?.execution?.mode ?: "video"
    val modes = listOf(
        Triple("video", "Video", Icons.Filled.Movie),
        Triple("music", "Music", Icons.Filled.MusicNote),
        Triple("game", "Game", Icons.Filled.SportsEsports)
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HomeControlColors.cardBackground()),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "SYNC MODE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modes.forEach { (mode, label, icon) ->
                    SyncModeTile(
                        icon = icon,
                        label = label,
                        isSelected = mode == currentMode,
                        onClick = { onModeSelect(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncBoxTile(
    icon: String,
    name: String,
    info: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        HuePurple.copy(alpha = 0.2f)
    } else {
        HomeControlColors.cardBackground()
    }
    val borderColor = if (isSelected) HuePurple else Color.Transparent

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = info,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SyncModeTile(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        HuePurple.copy(alpha = 0.2f)
    } else {
        HomeControlColors.cardBackground()
    }
    val borderColor = if (isSelected) HuePurple else Color.Transparent

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) HuePurple else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Helper function to get room icon based on room class
private fun getRoomIcon(roomClass: String?): String {
    return when (roomClass) {
        "Living room" -> "🛋️"
        "Bedroom" -> "🛏️"
        "Office" -> "💻"
        "Kitchen" -> "🍳"
        "Bathroom" -> "🚿"
        "Hallway" -> "🚪"
        "Garage" -> "🚗"
        "Balcony" -> "🌅"
        "TV" -> "📺"
        "Entertainment" -> "🎬"
        "Other" -> "💡"
        "Room" -> "🏠"
        "Zone" -> "📍"
        else -> "💡"
    }
}
