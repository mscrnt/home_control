package com.homecontrol.sensors.ui.screens.entertainment

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homecontrol.sensors.R
import com.homecontrol.sensors.data.model.SonySoundSetting
import com.homecontrol.sensors.ui.components.LoadingIndicator
import com.homecontrol.sensors.ui.theme.HomeControlColors

// Entertainment accent color
private val EntertainmentOrange = Color(0xFFffa726)

// Sealed class for icon types
sealed class ActivityIcon {
    data class Vector(val imageVector: ImageVector) : ActivityIcon()
    data class Drawable(@DrawableRes val resId: Int) : ActivityIcon()
}

// Activity data class
data class Activity(
    val name: String,
    val icon: ActivityIcon,
    val color: Color = EntertainmentOrange
)

// Device tabs
enum class DeviceTab(val label: String, val icon: ActivityIcon? = null) {
    ACTIVITY("Activity", ActivityIcon.Vector(Icons.Default.PlayCircle)),
    TV("TV", ActivityIcon.Vector(Icons.Default.Tv)),
    SHIELD("Shield"),
    SOUNDBAR("Soundbar"),
    XBOX("Xbox", ActivityIcon.Drawable(R.drawable.ic_xbox)),
    PS5("PS5", ActivityIcon.Drawable(R.drawable.ic_playstation))
}

// Activities list
private val activities = listOf(
    Activity("Watch TV", ActivityIcon.Vector(Icons.Default.Tv)),
    Activity("Play Xbox", ActivityIcon.Drawable(R.drawable.ic_xbox), Color(0xFF107C10)),
    Activity("Play PS5", ActivityIcon.Drawable(R.drawable.ic_playstation), Color(0xFF003791)),
    Activity("Play Switch", ActivityIcon.Drawable(R.drawable.ic_nintendo_switch), Color(0xFFE60012)),
    Activity("Airplay", ActivityIcon.Vector(Icons.Default.Airplay), Color(0xFF007AFF)),
    Activity("Spotify", ActivityIcon.Drawable(R.drawable.ic_spotify), Color(0xFF1DB954)),
    Activity("YouTube", ActivityIcon.Drawable(R.drawable.ic_youtube), Color(0xFFFF0000)),
    Activity("HBO", ActivityIcon.Drawable(R.drawable.ic_hbo), Color(0xFF991EEB)),
    Activity("Plex", ActivityIcon.Drawable(R.drawable.ic_plex), Color(0xFFE5A00D)),
    Activity("Crunchyroll", ActivityIcon.Drawable(R.drawable.ic_crunchyroll), Color(0xFFF47521))
)

@Composable
fun EntertainmentScreen(
    viewModel: EntertainmentViewModel = hiltViewModel(),
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
            uiState.isLoading && uiState.devices.sony.isEmpty() &&
                uiState.devices.shield.isEmpty() -> {
                LoadingIndicator()
            }
            else -> {
                EntertainmentContent(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun EntertainmentContent(
    uiState: EntertainmentUiState,
    viewModel: EntertainmentViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left column - Activities (reduced width ~1/6)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Header to align with tab row
            Text(
                text = "Activities",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Activity cards in scrollable column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                activities.forEach { activity ->
                    ActivityCard(
                        activity = activity,
                        onClick = { /* TODO: Implement activity launch */ }
                    )
                }
            }
        }

        // Right column - Device tabs (5/6 width)
        Column(
            modifier = Modifier
                .weight(5f)
                .fillMaxHeight()
        ) {
            // Centered tab row
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DeviceTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = tab.label,
                                    fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab content
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HomeControlColors.cardBackground()
                ),
                border = BorderStroke(1.dp, HomeControlColors.cardBorder())
            ) {
                when (DeviceTab.entries[selectedTab]) {
                    DeviceTab.ACTIVITY -> {
                        ActivityTab()
                    }
                    DeviceTab.TV -> {
                        TVTab(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                    DeviceTab.SHIELD -> {
                        ShieldTab(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                    DeviceTab.SOUNDBAR -> {
                        SoundbarTab(
                            uiState = uiState,
                            onVolumeChange = { volume ->
                                uiState.devices.sony.firstOrNull()?.let { device ->
                                    viewModel.setSonyVolume(device.name, volume)
                                }
                            },
                            onMuteToggle = {
                                uiState.devices.sony.firstOrNull()?.let { device ->
                                    viewModel.toggleSonyMute(device.name)
                                }
                            },
                            onSoundSettingChange = { target, value ->
                                uiState.devices.sony.firstOrNull()?.let { device ->
                                    viewModel.setSonySoundSetting(device.name, target, value)
                                }
                            }
                        )
                    }
                    DeviceTab.XBOX -> {
                        XboxTab(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                    DeviceTab.PS5 -> {
                        PS5Tab(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(
    activity: Activity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackground()
        ),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            ActivityIconContent(
                icon = activity.icon,
                tint = activity.color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = activity.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ActivityIconContent(
    icon: ActivityIcon,
    tint: Color,
    modifier: Modifier = Modifier
) {
    when (icon) {
        is ActivityIcon.Vector -> {
            Icon(
                imageVector = icon.imageVector,
                contentDescription = null,
                tint = tint,
                modifier = modifier
            )
        }
        is ActivityIcon.Drawable -> {
            Icon(
                painter = painterResource(id = icon.resId),
                contentDescription = null,
                tint = Color.Unspecified,  // Use original colors from drawable
                modifier = modifier
            )
        }
    }
}

@Composable
private fun ActivityRemotePlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = EntertainmentOrange.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Universal Remote",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select an activity to display controls",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DevicePlaceholder(name: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Tv,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Coming soon",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

// Sony soundbar accent color
private val SoundbarBlue = Color(0xFF2196F3)

@Composable
private fun SoundbarTab(
    uiState: EntertainmentUiState,
    onVolumeChange: (Int) -> Unit,
    onMuteToggle: () -> Unit,
    onSoundSettingChange: (String, String) -> Unit
) {
    val sonyDevice = uiState.devices.sony.firstOrNull()
    val sonyState = sonyDevice?.let { uiState.sonyStates[it.name] }
    val soundSettings = sonyDevice?.let { uiState.sonySoundSettings[it.name] } ?: emptyList()

    if (sonyDevice == null) {
        DevicePlaceholder("Soundbar")
        return
    }

    RemoteTemplate(
        deviceName = sonyDevice.name,
        accentColor = SoundbarBlue,
        isPowerOn = sonyState?.power == true,
        onPowerToggle = { /* TODO: Toggle soundbar power */ },
        onDPadPress = { direction ->
            // TODO: Send soundbar navigation commands
        },
        navButtons = standardNavButtons(
            onBack = { /* TODO */ },
            onHome = { /* TODO */ },
            onMenu = { /* TODO */ }
        ),
        mediaButtons = standardMediaButtons(
            onPlayPause = { /* TODO */ }
        ),
        rightContent = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Volume display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${sonyState?.volume ?: 0}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (sonyState?.muted == true)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else SoundbarBlue
                    )
                    Text(
                        text = if (sonyState?.muted == true) "Muted" else "Volume",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Volume buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = { onVolumeChange((sonyState?.volume ?: 0) - 5) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeDown,
                            contentDescription = "Volume Down",
                            tint = SoundbarBlue
                        )
                    }
                    IconButton(
                        onClick = onMuteToggle,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (sonyState?.muted == true) Color.Red.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = if (sonyState?.muted == true) Icons.Default.VolumeOff else Icons.Default.VolumeMute,
                            contentDescription = "Mute",
                            tint = if (sonyState?.muted == true) Color.Red else SoundbarBlue
                        )
                    }
                    IconButton(
                        onClick = { onVolumeChange((sonyState?.volume ?: 0) + 5) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Volume Up",
                            tint = SoundbarBlue
                        )
                    }
                }

                // Sound settings
                if (soundSettings.isNotEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Sound Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        soundSettings.forEach { setting ->
                            SoundSettingCompact(
                                setting = setting,
                                onValueChange = { value ->
                                    onSoundSettingChange(setting.target, value)
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun SoundSettingCompact(
    setting: SonySoundSetting,
    onValueChange: (String) -> Unit
) {
    val title = setting.title ?: setting.target.replace("_", " ")
        .replaceFirstChar { it.uppercase() }

    // Check if this is a boolean toggle (on/off)
    val isToggle = setting.candidate.size == 2 &&
        setting.candidate.any { it.value == "on" || it.value == "off" }

    if (isToggle) {
        // Toggle switch
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = setting.currentValue == "on",
                onCheckedChange = { checked ->
                    onValueChange(if (checked) "on" else "off")
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SoundbarBlue,
                    checkedTrackColor = SoundbarBlue.copy(alpha = 0.5f)
                ),
                modifier = Modifier.height(24.dp)
            )
        }
    } else {
        // Show current value as chip
        FilterChip(
            selected = true,
            onClick = {
                // Cycle to next value
                val currentIndex = setting.candidate.indexOfFirst { it.value == setting.currentValue }
                val nextIndex = (currentIndex + 1) % setting.candidate.size
                onValueChange(setting.candidate[nextIndex].value)
            },
            label = {
                val currentCandidate = setting.candidate.find { it.value == setting.currentValue }
                Text(
                    text = currentCandidate?.title?.ifEmpty { setting.currentValue } ?: setting.currentValue,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = SoundbarBlue,
                selectedLabelColor = Color.White
            )
        )
    }
}

@Composable
private fun SoundSettingControl(
    setting: SonySoundSetting,
    onValueChange: (String) -> Unit
) {
    val title = setting.title ?: setting.target.replace("_", " ")
        .replaceFirstChar { it.uppercase() }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Check if this is a boolean toggle (on/off)
        val isToggle = setting.candidate.size == 2 &&
            setting.candidate.any { it.value == "on" || it.value == "off" }

        // Check if this is a numeric slider
        val isSlider = setting.candidate.any { it.min != null && it.max != null }

        when {
            isToggle -> {
                // Toggle switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = setting.currentValue == "on",
                        onCheckedChange = { checked ->
                            onValueChange(if (checked) "on" else "off")
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SoundbarBlue,
                            checkedTrackColor = SoundbarBlue.copy(alpha = 0.5f)
                        )
                    )
                }
            }
            isSlider -> {
                // Numeric slider
                val sliderCandidate = setting.candidate.first { it.min != null }
                val min = sliderCandidate.min ?: 0
                val max = sliderCandidate.max ?: 100
                val step = sliderCandidate.step ?: 1
                val currentValue = setting.currentValue.toIntOrNull() ?: min

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = currentValue.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = SoundbarBlue
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = currentValue.toFloat(),
                        onValueChange = { onValueChange(it.toInt().toString()) },
                        valueRange = min.toFloat()..max.toFloat(),
                        steps = ((max - min) / step) - 1,
                        colors = SliderDefaults.colors(
                            thumbColor = SoundbarBlue,
                            activeTrackColor = SoundbarBlue
                        )
                    )
                }
            }
            else -> {
                // Chip selector for other options
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        setting.candidate.filter { it.isAvailable }.forEach { candidate ->
                            FilterChip(
                                selected = setting.currentValue == candidate.value,
                                onClick = { onValueChange(candidate.value) },
                                label = {
                                    Text(
                                        text = candidate.title.ifEmpty { candidate.value },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SoundbarBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// Device-specific accent colors
private val TVColor = Color(0xFF2196F3)    // Blue
private val ShieldColor = Color(0xFF76B900) // NVIDIA Green
private val XboxColor = Color(0xFF107C10)   // Xbox Green
private val PS5Color = Color(0xFF003791)    // PlayStation Blue

@Composable
private fun ActivityTab() {
    RemoteTemplate(
        deviceName = "Activity Remote",
        accentColor = EntertainmentOrange,
        isPowerOn = true,
        onPowerToggle = { /* TODO */ },
        onDPadPress = { direction ->
            // TODO: Send D-pad command to currently active device
        },
        navButtons = standardNavButtons(
            onBack = { /* TODO */ },
            onHome = { /* TODO */ },
            onMenu = { /* TODO */ }
        ),
        mediaButtons = standardMediaButtons(
            onRewind = { /* TODO */ },
            onPlayPause = { /* TODO */ },
            onFastForward = { /* TODO */ }
        ),
        rightContent = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "Select an activity to control",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    )
}

@Composable
private fun TVTab(
    uiState: EntertainmentUiState,
    viewModel: EntertainmentViewModel
) {
    val sonyDevice = uiState.devices.sony.firstOrNull()
    val sonyState = sonyDevice?.let { uiState.sonyStates[it.name] }

    if (sonyDevice == null) {
        DevicePlaceholder("TV")
        return
    }

    RemoteTemplate(
        deviceName = sonyDevice.name,
        accentColor = TVColor,
        isPowerOn = sonyState?.power == true,
        onPowerToggle = { viewModel.toggleSonyPower(sonyDevice.name) },
        onDPadPress = { direction ->
            // TODO: Send IR/CEC commands for D-pad navigation
        },
        navButtons = standardNavButtons(
            onBack = { /* TODO */ },
            onHome = { /* TODO */ },
            onMenu = { /* TODO */ }
        ),
        mediaButtons = standardMediaButtons(
            onPlayPause = { /* TODO */ }
        ),
        rightContent = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Volume display
                Text(
                    text = "Volume: ${sonyState?.volume ?: 0}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (sonyState?.muted == true)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else TVColor
                )

                // Volume buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = {
                            val currentVolume = sonyState?.volume ?: 0
                            viewModel.setSonyVolume(sonyDevice.name, (currentVolume - 5).coerceAtLeast(0))
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.VolumeDown, "Volume Down", tint = TVColor)
                    }
                    IconButton(
                        onClick = { viewModel.toggleSonyMute(sonyDevice.name) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (sonyState?.muted == true) Color.Red.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            if (sonyState?.muted == true) Icons.Default.VolumeOff else Icons.Default.VolumeMute,
                            "Mute",
                            tint = if (sonyState?.muted == true) Color.Red else TVColor
                        )
                    }
                    IconButton(
                        onClick = {
                            val currentVolume = sonyState?.volume ?: 0
                            viewModel.setSonyVolume(sonyDevice.name, (currentVolume + 5).coerceAtMost(100))
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.VolumeUp, "Volume Up", tint = TVColor)
                    }
                }

                // Input selector
                if (sonyState?.inputs?.isNotEmpty() == true) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Input",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = sonyState.input ?: "Unknown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TVColor
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun ShieldTab(
    uiState: EntertainmentUiState,
    viewModel: EntertainmentViewModel
) {
    val shieldDevice = uiState.devices.shield.firstOrNull()
    val shieldState = shieldDevice?.let { uiState.shieldStates[it.name] }

    if (shieldDevice == null) {
        DevicePlaceholder("Shield")
        return
    }

    RemoteTemplate(
        deviceName = shieldDevice.name,
        accentColor = ShieldColor,
        isPowerOn = shieldState?.power == true,
        onPowerToggle = { viewModel.toggleShieldPower(shieldDevice.name) },
        onDPadPress = { direction ->
            // TODO: Send ADB key events for D-pad navigation
        },
        navButtons = standardNavButtons(
            onBack = { /* TODO: Send back key event */ },
            onHome = { /* TODO: Send home key event */ },
            onMenu = { /* TODO: Send menu key event */ }
        ),
        mediaButtons = standardMediaButtons(
            onRewind = { /* TODO: Send rewind key event */ },
            onPlayPause = { /* TODO: Send play/pause key event */ },
            onFastForward = { /* TODO: Send fast forward key event */ }
        ),
        rightContent = {
            // Show current app if available
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                shieldState?.currentApp?.let { app ->
                    Text(
                        text = "Now Running",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = app,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ShieldColor,
                        textAlign = TextAlign.Center
                    )
                } ?: Text(
                    text = "Shield is ${if (shieldState?.power == true) "on" else "off"}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    )
}

@Composable
private fun XboxTab(
    uiState: EntertainmentUiState,
    viewModel: EntertainmentViewModel
) {
    val xboxDevice = uiState.devices.xbox.firstOrNull()
    val xboxState = xboxDevice?.let { uiState.xboxStates[it.name] }

    if (xboxDevice == null) {
        DevicePlaceholder("Xbox")
        return
    }

    RemoteTemplate(
        deviceName = xboxDevice.name,
        accentColor = XboxColor,
        isPowerOn = xboxState?.power == true,
        onPowerToggle = { viewModel.toggleXboxPower(xboxDevice.name) },
        onDPadPress = { direction ->
            // TODO: Send Xbox controller commands
        },
        navButtons = standardNavButtons(
            onBack = { /* TODO: Send B button */ },
            onHome = { /* TODO: Send Xbox button */ },
            onMenu = { /* TODO: Send menu button */ }
        ),
        mediaButtons = standardMediaButtons(
            onPlayPause = { /* TODO */ }
        ),
        rightContent = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_xbox),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (xboxState?.power == true) "Xbox is on" else "Xbox is off",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    )
}

@Composable
private fun PS5Tab(
    uiState: EntertainmentUiState,
    viewModel: EntertainmentViewModel
) {
    val ps5Device = uiState.devices.ps5.firstOrNull()
    val ps5State = ps5Device?.let { uiState.ps5States[it.name] }

    if (ps5Device == null) {
        DevicePlaceholder("PS5")
        return
    }

    RemoteTemplate(
        deviceName = ps5Device.name,
        accentColor = PS5Color,
        isPowerOn = ps5State?.power == true,
        onPowerToggle = { viewModel.togglePS5Power(ps5Device.name) },
        onDPadPress = { direction ->
            // TODO: Send PS5 controller commands
        },
        navButtons = standardNavButtons(
            onBack = { /* TODO: Send circle button */ },
            onHome = { /* TODO: Send PS button */ },
            onMenu = { /* TODO: Send options button */ }
        ),
        mediaButtons = standardMediaButtons(
            onPlayPause = { /* TODO */ }
        ),
        rightContent = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_playstation),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (ps5State?.power == true) "PS5 is on" else "PS5 is off",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    )
}
