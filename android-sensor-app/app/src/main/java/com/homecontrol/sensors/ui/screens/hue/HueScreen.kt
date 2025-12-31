package com.homecontrol.sensors.ui.screens.hue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.annotation.DrawableRes
import com.homecontrol.sensors.R
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
                val entertainmentRooms = uiState.rooms.filter { it.type == "Entertainment" }
                val hasEntertainment = uiState.syncBoxes.isNotEmpty()

                // Calculate total tabs
                val totalTabs = rooms.size + if (hasEntertainment) 1 else 0

                // Ensure selectedTabIndex is valid
                val selectedTabIndex = uiState.selectedTabIndex.coerceIn(0, maxOf(0, totalTabs - 1))

                // Find which lights are currently syncing by checking Entertainment areas
                // with streamingActive = true (same approach as web code)
                val syncingLightIds = entertainmentRooms
                    .filter { it.streamingActive }
                    .flatMap { it.lights }
                    .map { it.id }
                    .toSet()


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
                                syncingLightIds = syncingLightIds,
                                onToggleRoom = { viewModel.toggleRoom(room.id) },
                                onRoomBrightnessChange = { brightness ->
                                    viewModel.setRoomBrightness(room.id, brightness)
                                },
                                onSceneActivate = { sceneId -> viewModel.activateScene(sceneId) },
                                onLightToggle = { lightId -> viewModel.toggleLight(lightId) },
                                onLightBrightnessChange = { lightId, brightness ->
                                    viewModel.setLightBrightness(lightId, brightness)
                                },
                                onStopSync = {
                                    // Stop all active sync boxes
                                    uiState.syncBoxStatuses.forEach { (index, status) ->
                                        if (status.syncActive) {
                                            viewModel.toggleSyncBoxSync(index, false)
                                        }
                                    }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(16.dp))
                .background(HomeControlColors.cardBackground())
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Room tabs
            rooms.forEachIndexed { index, room ->
                HueTab(
                    icon = getRoomIcon(room.roomClass, room.name),
                    label = room.name,
                    isSelected = selectedTabIndex == index,
                    isEntertainment = false,
                    onClick = { onTabSelected(index) }
                )
            }

            // Entertainment Areas tab
            if (hasEntertainment) {
                HueTab(
                    icon = RoomIcon.Emoji("🎬"),
                    label = "Entertainment",
                    isSelected = selectedTabIndex == rooms.size,
                    isEntertainment = true,
                    onClick = { onTabSelected(rooms.size) }
                )
            }
        }
    }
}

@Composable
private fun HueTab(
    icon: RoomIcon,
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
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (icon) {
                is RoomIcon.Emoji -> Text(
                    text = icon.emoji,
                    fontSize = 18.sp
                )
                is RoomIcon.Drawable -> androidx.compose.foundation.Image(
                    painter = painterResource(id = icon.resId),
                    contentDescription = label,
                    modifier = Modifier.size(18.dp)
                    // No colorFilter - let drawable icons show their natural colors
                )
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
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
    syncingLightIds: Set<String>,
    onToggleRoom: () -> Unit,
    onRoomBrightnessChange: (Int) -> Unit,
    onSceneActivate: (String) -> Unit,
    onLightToggle: (String) -> Unit,
    onLightBrightnessChange: (String, Int) -> Unit,
    onStopSync: () -> Unit = {}
) {
    var brightnessValue by remember(room.brightness) { mutableFloatStateOf(room.brightness.coerceIn(0, 100).toFloat()) }
    val roomIcon = getRoomIcon(room.roomClass, room.name)

    // Check if any lights in this room are syncing
    val roomHasSyncingLights = room.lights.any { it.id in syncingLightIds }
    val allLightsSyncing = room.lights.isNotEmpty() && room.lights.all { it.id in syncingLightIds }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sync warning banner
        if (roomHasSyncingLights) {
            SyncWarningBanner(onStopSync = onStopSync)
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
        // Left column - Power button and brightness in a card container
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = HomeControlColors.cardBackground()),
            border = BorderStroke(1.dp, HomeControlColors.cardBorder())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Power button - disable if all lights in room are syncing
                PowerButton(
                    isOn = room.on,
                    roomName = room.name,
                    roomIcon = roomIcon,
                    isSyncing = allLightsSyncing,
                    onClick = onToggleRoom
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Brightness slider
                BrightnessControl(
                    label = "Brightness",
                    value = brightnessValue,
                    onValueChange = { brightnessValue = it },
                    onValueChangeFinished = { onRoomBrightnessChange(brightnessValue.roundToInt()) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
                syncingLightIds = syncingLightIds,
                onLightToggle = onLightToggle,
                onLightBrightnessChange = onLightBrightnessChange
            )
        }
        }
    }
}

@Composable
private fun SyncWarningBanner(
    onStopSync: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = HuePurple.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, HuePurple.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Hue Sync is active",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = HuePurple
                )
                Text(
                    text = "Stop syncing to control the lights",
                    style = MaterialTheme.typography.bodySmall,
                    color = HuePurple.copy(alpha = 0.8f)
                )
            }
            Button(
                onClick = onStopSync,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HuePurple
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Stop Sync")
            }
        }
    }
}

@Composable
private fun PowerButton(
    isOn: Boolean,
    roomName: String,
    roomIcon: RoomIcon,
    isSyncing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Room icon above the room name (large size)
        when (roomIcon) {
            is RoomIcon.Emoji -> Text(
                text = roomIcon.emoji,
                fontSize = 120.sp
            )
            is RoomIcon.Drawable -> androidx.compose.foundation.Image(
                painter = painterResource(id = roomIcon.resId),
                contentDescription = roomName,
                modifier = Modifier.size(120.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Room name above the button
        Text(
            text = roomName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Power button - disabled when syncing
        val buttonColor = when {
            isSyncing -> HuePurple
            isOn -> HueOrange
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
        val buttonModifier = if (isSyncing) {
            Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(buttonColor)
        } else {
            Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable { onClick() }
        }

        Box(
            modifier = buttonModifier,
            contentAlignment = Alignment.Center
        ) {
            if (isSyncing) {
                // Show syncing icon/emoji
                Text(
                    text = "🎬",
                    fontSize = 60.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = "Power",
                    modifier = Modifier.size(100.dp),
                    tint = if (isOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // State below the button
        val stateText = when {
            isSyncing -> "Syncing"
            isOn -> "On"
            else -> "Off"
        }
        val stateColor = when {
            isSyncing -> HuePurple
            isOn -> HueOrange
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            text = stateText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = stateColor
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
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
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
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${value.roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ScenesSection(
    sceneCount: Int,
    scenes: List<HueScene>,
    onSceneActivate: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val activeScene = scenes.find { it.active }

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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.ic_color_palette),
                        contentDescription = "Scenes",
                        modifier = Modifier.size(20.dp)
                    )
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

            // Dropdown selector
            Box {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { expanded = true },
                    shape = RoundedCornerShape(10.dp),
                    color = if (activeScene != null) HueOrange.copy(alpha = 0.15f) else HomeControlColors.cardBackground(),
                    border = BorderStroke(
                        width = if (activeScene != null) 2.dp else 1.dp,
                        color = if (activeScene != null) HueOrange else HomeControlColors.cardBorder()
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (activeScene != null) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Active",
                                    tint = HueOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = activeScene?.name ?: "Select a scene",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (activeScene != null) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (activeScene != null) HueOrange else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(HomeControlColors.cardBackground())
                        .heightIn(max = 300.dp)
                ) {
                    scenes.forEach { scene ->
                        val isActive = scene.active
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Active",
                                            tint = HueOrange,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(20.dp))
                                    }
                                    Text(
                                        text = scene.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isActive) HueOrange else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            onClick = {
                                onSceneActivate(scene.id)
                                expanded = false
                            },
                            modifier = Modifier
                                .background(
                                    if (isActive) HueOrange.copy(alpha = 0.1f) else Color.Transparent
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LightsSection(
    lights: List<HueLight>,
    syncingLightIds: Set<String>,
    onLightToggle: (String) -> Unit,
    onLightBrightnessChange: (String, Int) -> Unit
) {
    val syncingCount = lights.count { it.id in syncingLightIds }
    val hasSyncingLights = syncingCount > 0

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
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.ic_hue_lightbulb),
                        contentDescription = "Lights",
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Lights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val statusText = if (hasSyncingLights) {
                    "$syncingCount syncing, ${lights.count { it.on && it.id !in syncingLightIds }}/${lights.size - syncingCount} on"
                } else {
                    "${lights.count { it.on }}/${lights.size} on"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasSyncingLights) HuePurple else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Divider line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HomeControlColors.cardBorder())
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic height based on number of lights - more compact cards
            val rows = (lights.size + 2) / 3 // 3 columns, rounded up
            val gridHeight = (rows * 70).coerceIn(70, 280).dp

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(gridHeight)
            ) {
                items(lights) { light ->
                    val isLightSyncing = light.id in syncingLightIds
                    LightCard(
                        light = light,
                        isSyncing = isLightSyncing,
                        onClick = { if (!isLightSyncing) onLightToggle(light.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LightCard(
    light: HueLight,
    isSyncing: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSyncing -> HuePurple.copy(alpha = 0.12f)
        light.on -> HueOrange.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    val cardModifier = when {
        isSyncing -> Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 8.dp)
        light.on -> Modifier
            .fillMaxWidth()
            .border(1.dp, HueOrange.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp)
        else -> Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp)
    }

    Row(
        modifier = cardModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Show sync icon when syncing, otherwise show lightbulb
        if (isSyncing) {
            Text(
                text = "🎬",
                fontSize = 20.sp
            )
        } else {
            androidx.compose.foundation.Image(
                painter = painterResource(
                    id = if (light.on) R.drawable.ic_lightbulb_on else R.drawable.ic_lightbulb_off
                ),
                contentDescription = if (light.on) "Light on" else "Light off",
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            val nameColor = when {
                isSyncing -> HuePurple
                light.on -> HueOrange
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                text = light.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSyncing || light.on) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = nameColor
            )
            val statusText = when {
                isSyncing -> "Syncing"
                light.on -> "${light.brightness.coerceIn(0, 100)}%"
                else -> "Off"
            }
            val statusColor = when {
                isSyncing -> HuePurple.copy(alpha = 0.8f)
                light.on -> HueOrange.copy(alpha = 0.8f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🎬", fontSize = 48.sp)
                Text(
                    text = "No Sync Boxes configured",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val currentBox = syncBoxes.getOrNull(selectedIndex) ?: return
    val currentStatus = syncBoxStatuses[currentBox.index]
    val isSyncing = currentStatus?.syncActive == true

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Left column - Sync Box selector and toggle
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Sync Box selector tabs
            if (syncBoxes.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    syncBoxes.forEachIndexed { index, box ->
                        val boxStatus = syncBoxStatuses[box.index]
                        val boxSyncing = boxStatus?.syncActive == true
                        SyncBoxChip(
                            name = box.name,
                            isSelected = index == selectedIndex,
                            isSyncing = boxSyncing,
                            onClick = { selectedSyncBoxIndex = index },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Big sync button
            Surface(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .clickable { onToggleSync(currentBox.index, !isSyncing) },
                shape = CircleShape,
                color = if (isSyncing) HuePurple else HomeControlColors.cardBackground(),
                shadowElevation = if (isSyncing) 16.dp else 4.dp,
                border = BorderStroke(
                    width = 3.dp,
                    color = if (isSyncing) HuePurple else HomeControlColors.cardBorder()
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isSyncing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (isSyncing) "Stop" else "Start",
                            modifier = Modifier.size(72.dp),
                            tint = if (isSyncing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Text(
                text = if (isSyncing) "Syncing - Tap to Stop" else "Tap to Start Sync",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSyncing) HuePurple else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Current mode indicator when syncing
            if (isSyncing) {
                val currentMode = currentStatus?.execution?.mode ?: "video"
                val modeIcon = when (currentMode) {
                    "video" -> Icons.Filled.Movie
                    "music" -> Icons.Filled.MusicNote
                    "game" -> Icons.Filled.SportsEsports
                    else -> Icons.Filled.Movie
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = HuePurple.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, HuePurple.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = modeIcon,
                            contentDescription = null,
                            tint = HuePurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = currentMode.replaceFirstChar { it.uppercase() } + " Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = HuePurple
                        )
                    }
                }
            }
        }

        // Right column - Controls
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sync Mode
            SyncModeCard(
                status = currentStatus,
                onModeSelect = { onModeChange(currentBox.index, it) }
            )

            // HDMI Input
            HdmiInputCard(
                status = currentStatus,
                onInputSelect = { onInputChange(currentBox.index, it) }
            )

            // Entertainment Area (if available)
            val groups = currentStatus?.hue?.groups
            if (!groups.isNullOrEmpty()) {
                EntertainmentAreaCard(
                    status = currentStatus,
                    onAreaSelect = { /* TODO */ }
                )
            }
        }
    }
}

@Composable
private fun SyncBoxChip(
    name: String,
    isSelected: Boolean,
    isSyncing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isSyncing && isSelected -> HuePurple.copy(alpha = 0.2f)
        isSelected -> HomeControlColors.cardBackground()
        else -> Color.Transparent
    }
    val borderColor = when {
        isSyncing -> HuePurple
        isSelected -> HueOrange
        else -> HomeControlColors.cardBorder()
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isSyncing) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Syncing",
                    style = MaterialTheme.typography.labelSmall,
                    color = HuePurple,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EntertainmentAreaCard(
    status: SyncBoxStatus?,
    onAreaSelect: (String) -> Unit
) {
    val groups = status?.hue?.groups ?: emptyMap()
    val currentGroupId = status?.execution?.hueTarget ?: ""
    var expanded by remember { mutableStateOf(false) }
    val currentGroup = groups[currentGroupId]

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🎬", fontSize = 18.sp)
                Text(
                    text = "Entertainment Area",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Dropdown selector
            Box {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { expanded = true },
                    shape = RoundedCornerShape(10.dp),
                    color = HuePurple.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, HuePurple.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = currentGroup?.name ?: "Select area",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (currentGroup != null) {
                                Text(
                                    text = "${currentGroup.numLights} lights",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    groups.forEach { (id, group) ->
                        val isSelected = id == currentGroupId
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = group.name,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    Text(
                                        text = "${group.numLights} lights",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onAreaSelect(id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HdmiInputCard(
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
    var expanded by remember { mutableStateOf(false) }
    val currentInputObj = inputs.find { it.id == currentInput }

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "📺", fontSize = 18.sp)
                Text(
                    text = "HDMI Input",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Dropdown selector
            Box {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { expanded = true },
                    shape = RoundedCornerShape(10.dp),
                    color = HuePurple.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, HuePurple.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentInputObj?.name ?: "Select input",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    inputs.forEach { input ->
                        val isSelected = input.id == currentInput
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = HuePurple,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(18.dp))
                                    }
                                    Text(
                                        text = input.name,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) HuePurple else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            onClick = {
                                onInputSelect(input.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncModeCard(
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🎨", fontSize = 18.sp)
                Text(
                    text = "Sync Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modes.forEach { (mode, label, icon) ->
                    val isSelected = mode == currentMode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onModeSelect(mode) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) HuePurple.copy(alpha = 0.2f) else HomeControlColors.cardBackground(),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) HuePurple else HomeControlColors.cardBorder()
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(28.dp),
                                tint = if (isSelected) HuePurple else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) HuePurple else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}


// Sealed class for room icons - can be emoji or drawable resource
private sealed class RoomIcon {
    data class Emoji(val emoji: String) : RoomIcon()
    data class Drawable(@DrawableRes val resId: Int) : RoomIcon()
}

// Helper function to get room icon based on room class and name
private fun getRoomIcon(roomClass: String?, roomName: String): RoomIcon {
    // Check both roomClass and roomName for matches
    val classNorm = roomClass?.lowercase() ?: ""
    val nameNorm = roomName.lowercase()

    fun matches(keyword: String) = classNorm.contains(keyword) || nameNorm.contains(keyword)

    return when {
        matches("living") -> RoomIcon.Emoji("🛋️")
        matches("bedroom") -> RoomIcon.Emoji("🛏️")
        matches("office") -> RoomIcon.Drawable(R.drawable.ic_desk)
        matches("kitchen") -> RoomIcon.Emoji("🍳")
        matches("bathroom") || matches("bath") -> RoomIcon.Emoji("🚿")
        matches("hallway") || matches("hall") -> RoomIcon.Emoji("🚪")
        matches("garage") -> RoomIcon.Emoji("🚗")
        matches("balcony") || matches("patio") || matches("terrace") -> RoomIcon.Drawable(R.drawable.ic_patio)
        matches("bar") -> RoomIcon.Drawable(R.drawable.ic_bar)
        matches("tv") || matches("media") -> RoomIcon.Emoji("📺")
        matches("entertainment") -> RoomIcon.Emoji("🎬")
        matches("dining") -> RoomIcon.Emoji("🍽️")
        matches("nursery") || matches("kid") -> RoomIcon.Emoji("🧸")
        matches("gym") || matches("fitness") -> RoomIcon.Emoji("🏋️")
        matches("pool") -> RoomIcon.Emoji("🏊")
        matches("laundry") -> RoomIcon.Emoji("🧺")
        matches("closet") || matches("storage") -> RoomIcon.Emoji("👕")
        matches("attic") -> RoomIcon.Emoji("🏚️")
        matches("basement") -> RoomIcon.Emoji("🪜")
        classNorm == "zone" || nameNorm == "zone" -> RoomIcon.Emoji("📍")
        classNorm == "room" || nameNorm == "room" -> RoomIcon.Emoji("🏠")
        else -> RoomIcon.Emoji("💡")
    }
}
