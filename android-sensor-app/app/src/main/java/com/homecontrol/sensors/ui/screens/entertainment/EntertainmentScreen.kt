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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

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
    val color: Color = EntertainmentOrange,
    val sofabatonOnUrl: String? = null,
    val sofabatonOffUrl: String? = null,
    val hdmiInput: String? = null,  // input1, input2, input3, input4
    val shieldApp: String? = null   // Package name of app to launch on Shield
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

// Sofabaton API base
private const val SOFABATON_NODE = "Mqg2Q8nJ8MXVHXuFT9MzVe"
private fun sofabatonUrl(id: String, type: Int) =
    "https://app1.sofabaton.com/app/keypress?node_id=$SOFABATON_NODE&id=$id&type=$type"

// Activities list
private val activities = listOf(
    Activity(
        name = "TV",
        icon = ActivityIcon.Vector(Icons.Default.Tv),
        sofabatonOnUrl = sofabatonUrl("Mqg2Q8n101", 1),
        sofabatonOffUrl = sofabatonUrl("Mqg2Q8n101", 0),
        hdmiInput = "input4"  // Shield is on HDMI 4
    ),
    Activity(
        name = "Xbox",
        icon = ActivityIcon.Drawable(R.drawable.ic_xbox),
        color = Color(0xFF107C10),
        sofabatonOnUrl = sofabatonUrl("Mqg2Q8n102", 1),
        sofabatonOffUrl = sofabatonUrl("Mqg2Q8n102", 0),
        hdmiInput = "input1"  // Xbox is on HDMI 1
    ),
    Activity(
        name = "PS5",
        icon = ActivityIcon.Drawable(R.drawable.ic_playstation),
        color = Color(0xFF003791),
        sofabatonOnUrl = sofabatonUrl("Mqg2Q8n103", 1),
        sofabatonOffUrl = sofabatonUrl("Mqg2Q8n103", 0),
        hdmiInput = "input2"  // PS5 is on HDMI 2
    ),
    Activity(
        name = "Switch",
        icon = ActivityIcon.Drawable(R.drawable.ic_nintendo_switch),
        color = Color(0xFFE60012),
        sofabatonOnUrl = sofabatonUrl("Mqg2Q8n104", 1),
        sofabatonOffUrl = sofabatonUrl("Mqg2Q8n104", 0),
        hdmiInput = "input3"  // Switch is on HDMI 3
    ),
    Activity("Airplay", ActivityIcon.Vector(Icons.Default.Airplay), Color(0xFF007AFF)),
    Activity(
        name = "Spotify",
        icon = ActivityIcon.Drawable(R.drawable.ic_spotify),
        color = Color(0xFF1DB954),
        sofabatonOnUrl = sofabatonUrl("Mqg2Q8n101", 1),  // Uses TV command
        sofabatonOffUrl = sofabatonUrl("Mqg2Q8n101", 0),
        hdmiInput = "input4",
        shieldApp = "com.spotify.tv.android"
    ),
    Activity(
        name = "YouTube",
        icon = ActivityIcon.Drawable(R.drawable.ic_youtube),
        color = Color(0xFFFF0000),
        sofabatonOnUrl = sofabatonUrl("Mqg2Q8n101", 1),
        sofabatonOffUrl = sofabatonUrl("Mqg2Q8n101", 0),
        hdmiInput = "input4",
        shieldApp = "com.google.android.youtube.tv"
    ),
    Activity(
        name = "Twitch",
        icon = ActivityIcon.Drawable(R.drawable.ic_twitch),
        color = Color(0xFF9146FF),
        sofabatonOnUrl = sofabatonUrl("Mqg2Q8n101", 1),
        sofabatonOffUrl = sofabatonUrl("Mqg2Q8n101", 0),
        hdmiInput = "input4",
        shieldApp = "tv.twitch.android.app"
    ),
    Activity(
        name = "HBO",
        icon = ActivityIcon.Drawable(R.drawable.ic_hbo),
        color = Color(0xFF991EEB),
        sofabatonOnUrl = sofabatonUrl("Mqg2Q8n101", 1),
        sofabatonOffUrl = sofabatonUrl("Mqg2Q8n101", 0),
        hdmiInput = "input4",
        shieldApp = "com.hbo.hbomax"
    ),
    Activity(
        name = "Plex",
        icon = ActivityIcon.Drawable(R.drawable.ic_plex),
        color = Color(0xFFE5A00D),
        sofabatonOnUrl = sofabatonUrl("Mqg2Q8n101", 1),
        sofabatonOffUrl = sofabatonUrl("Mqg2Q8n101", 0),
        hdmiInput = "input4",
        shieldApp = "com.plexapp.android"
    ),
    Activity(
        name = "Crunchyroll",
        icon = ActivityIcon.Drawable(R.drawable.ic_crunchyroll),
        color = Color(0xFFF47521),
        sofabatonOnUrl = sofabatonUrl("Mqg2Q8n101", 1),
        sofabatonOffUrl = sofabatonUrl("Mqg2Q8n101", 0),
        hdmiInput = "input4",
        shieldApp = "com.crunchyroll.crunchyroid"
    )
)

// Helper function to call Sofabaton API
private suspend fun callSofabatonApi(url: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.getInputStream().close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

// Helper function to set Hue Sync Box HDMI input (index 0 = first sync box)
private suspend fun setSyncBoxHdmiInput(baseUrl: String, input: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val url = URL("$baseUrl/api/syncbox/0/input")
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.outputStream.write("""{"hdmiSource":"$input"}""".toByteArray())
        val responseCode = connection.responseCode
        connection.disconnect()
        responseCode in 200..299
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

// Helper function to launch an app on Shield (uses first Shield device)
private suspend fun launchShieldApp(baseUrl: String, shieldName: String, packageName: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val encodedName = java.net.URLEncoder.encode(shieldName, "UTF-8")
        val url = URL("$baseUrl/api/entertainment/shield/$encodedName/app")
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.outputStream.write("""{"action":"launch","package":"$packageName"}""".toByteArray())
        val responseCode = connection.responseCode
        connection.disconnect()
        responseCode in 200..299
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

@Composable
fun EntertainmentScreen(
    viewModel: EntertainmentViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Refresh data when screen appears
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

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
    val scope = rememberCoroutineScope()
    // Track the currently active activity (only one at a time)
    var currentActivity by remember { mutableStateOf<String?>(null) }

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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Activity cards in matching card container
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HomeControlColors.cardBackground()
                ),
                border = BorderStroke(1.dp, HomeControlColors.cardBorder())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    activities.forEach { activity ->
                        val isActive = currentActivity == activity.name
                        val hasPowerControl = activity.sofabatonOnUrl != null

                        ActivityCard(
                            activity = activity,
                            isActive = isActive,
                            onClick = if (hasPowerControl) {
                                {
                                    scope.launch {
                                        if (isActive) {
                                            // Turn off current activity
                                            activity.sofabatonOffUrl?.let { url -> callSofabatonApi(url) }
                                            currentActivity = null
                                        } else {
                                            // Turn on new activity (Sofabaton handles turning off the previous one)
                                            activity.sofabatonOnUrl?.let { url -> callSofabatonApi(url) }
                                            // Set HDMI input if applicable
                                            activity.hdmiInput?.let { input ->
                                                setSyncBoxHdmiInput(uiState.serverUrl.trimEnd('/'), input)
                                            }
                                            // Launch Shield app if applicable
                                            activity.shieldApp?.let { pkg ->
                                                uiState.devices.shield.firstOrNull()?.let { shieldDevice ->
                                                    launchShieldApp(uiState.serverUrl.trimEnd('/'), shieldDevice.name, pkg)
                                                }
                                            }
                                            currentActivity = activity.name
                                        }
                                    }
                                }
                            } else { {} },
                            showPowerIcon = hasPowerControl
                        )
                    }

                    // Power Off All button
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                // Turn off all activities with power control
                                activities.filter { act -> act.sofabatonOffUrl != null }.forEach { act ->
                                    act.sofabatonOffUrl?.let { url -> callSofabatonApi(url) }
                                }
                                currentActivity = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Power Off All",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
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
    isActive: Boolean = false,
    onClick: () -> Unit,
    showPowerIcon: Boolean = false
) {
    // Use a consistent green for active state that works in both light/dark themes
    val activeColor = Color(0xFF4CAF50)  // Material Green 500

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) activeColor.copy(alpha = 0.15f) else HomeControlColors.cardBackground()
        ),
        border = BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) activeColor else HomeControlColors.cardBorder()
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side - icon and text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                ActivityIconContent(
                    icon = activity.icon,
                    tint = activity.color,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) activity.color else MaterialTheme.colorScheme.onSurface
                )
            }

            // Right side - power indicator icon (just shows state, whole card is clickable)
            if (showPowerIcon) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = if (isActive) "Active" else "Inactive",
                    tint = if (isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                    modifier = Modifier.size(18.dp)
                )
            }
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
    val shieldApps = shieldDevice?.let { uiState.shieldApps[it.name] } ?: emptyList()

    if (shieldDevice == null) {
        DevicePlaceholder("Shield")
        return
    }

    // Key mappings for ADB
    val keyMap = mapOf(
        DPadDirection.UP to "dpad_up",
        DPadDirection.DOWN to "dpad_down",
        DPadDirection.LEFT to "dpad_left",
        DPadDirection.RIGHT to "dpad_right",
        DPadDirection.CENTER to "enter"
    )

    RemoteTemplate(
        deviceName = shieldDevice.name,
        accentColor = ShieldColor,
        isPowerOn = shieldState?.power == true,
        onPowerToggle = { viewModel.toggleShieldPower(shieldDevice.name) },
        onDPadPress = { direction ->
            keyMap[direction]?.let { key ->
                viewModel.sendShieldKey(shieldDevice.name, key)
            }
        },
        navButtons = standardNavButtons(
            onBack = { viewModel.sendShieldKey(shieldDevice.name, "back") },
            onHome = { viewModel.sendShieldKey(shieldDevice.name, "home") },
            onMenu = { viewModel.sendShieldKey(shieldDevice.name, "menu") }
        ),
        mediaButtons = standardMediaButtons(
            onRewind = { viewModel.sendShieldKey(shieldDevice.name, "rewind") },
            onPlayPause = { viewModel.sendShieldKey(shieldDevice.name, "play_pause") },
            onFastForward = { viewModel.sendShieldKey(shieldDevice.name, "fast_forward") }
        ),
        rightContent = {
            ShieldRightPanel(
                shieldState = shieldState,
                shieldApps = shieldApps,
                onAppClick = { app ->
                    viewModel.launchShieldApp(shieldDevice.name, app.packageName)
                }
            )
        }
    )
}

// Curated list of apps to show on Shield (package names) - sorted alphabetically
private val curatedShieldApps = setOf(
    "com.apple.atve.androidtv.appletv",     // Apple TV
    "com.crunchyroll.crunchyroid",          // Crunchyroll
    "com.hbo.hbomax",                       // HBO Max
    "com.hbo.hbonow",                       // HBO Now
    "com.hulu.livingroomplus",              // Hulu
    "com.netflix.ninja",                    // Netflix
    "net.openvpn.openvpn",                  // OpenVPN
    "com.peacocktv.peacockandroid",         // Peacock
    "com.plexapp.android",                  // Plex
    "com.amazon.amazonvideo.livingroom",    // Prime Video
    "com.retroarch",                        // RetroArch
    "com.spotify.tv.android",               // Spotify
    "com.valvesoftware.steamlink",          // Steam Link
    "tv.twitch.android.app",                // Twitch
    "com.gamepass",                         // Xbox Game Pass
    "com.microsoft.xcloud",                 // Xbox Cloud Gaming (alt package)
    "com.google.android.youtube.tv",        // YouTube
    "com.google.android.youtube.tvunplugged" // YouTube TV
)

// Map package names to drawable resource IDs - sorted alphabetically
private val shieldAppDrawables: Map<String, Int> = mapOf(
    "com.apple.atve.androidtv.appletv" to R.drawable.ic_apple_tv,
    "com.crunchyroll.crunchyroid" to R.drawable.ic_crunchyroll,
    "com.hbo.hbomax" to R.drawable.ic_hbo,
    "com.hbo.hbonow" to R.drawable.ic_hbo,
    "com.hulu.livingroomplus" to R.drawable.ic_hulu,
    "com.netflix.ninja" to R.drawable.ic_netflix,
    "net.openvpn.openvpn" to R.drawable.ic_openvpn,
    "com.peacocktv.peacockandroid" to R.drawable.ic_peacock,
    "com.plexapp.android" to R.drawable.ic_plex,
    "com.amazon.amazonvideo.livingroom" to R.drawable.ic_prime_video,
    "com.retroarch" to R.drawable.ic_retroarch,
    "com.spotify.tv.android" to R.drawable.ic_spotify,
    "com.valvesoftware.steamlink" to R.drawable.ic_steam,
    "tv.twitch.android.app" to R.drawable.ic_twitch,
    "com.gamepass" to R.drawable.ic_xbox_game_pass,
    "com.microsoft.xcloud" to R.drawable.ic_xbox_game_pass,
    "com.google.android.youtube.tv" to R.drawable.ic_youtube,
    "com.google.android.youtube.tvunplugged" to R.drawable.ic_youtube_tv
)

@Composable
private fun ShieldRightPanel(
    shieldState: com.homecontrol.sensors.data.model.ShieldState?,
    shieldApps: List<com.homecontrol.sensors.data.model.ShieldApp>,
    onAppClick: (com.homecontrol.sensors.data.model.ShieldApp) -> Unit
) {
    // Filter to only show curated apps
    val filteredApps = shieldApps.filter { it.packageName in curatedShieldApps }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Power state indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (shieldState?.powerState) {
                                    "awake" -> ShieldColor
                                    "dreaming" -> Color(0xFFFFA726)
                                    else -> Color.Gray
                                }
                            )
                    )
                    Text(
                        text = when (shieldState?.powerState) {
                            "awake" -> "Awake"
                            "dreaming" -> "Screensaver"
                            "asleep" -> "Asleep"
                            else -> if (shieldState?.online == true) "Online" else "Offline"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Current app
                shieldState?.currentApp?.let { app ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Now Running",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = app,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ShieldColor,
                        textAlign = TextAlign.Center
                    )
                }

                // Volume (if available)
                if (shieldState?.volume != null && shieldState.volume > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (shieldState.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Volume",
                            tint = if (shieldState.muted) Color.Red else ShieldColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${shieldState.volume}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (shieldState.muted)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Apps section
        if (filteredApps.isNotEmpty()) {
            Text(
                text = "Apps",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Apps grid - 4 columns
            val chunkedApps = filteredApps.chunked(4)
            chunkedApps.forEach { rowApps ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowApps.forEach { app ->
                        ShieldAppCard(
                            app = app,
                            onClick = { onAppClick(app) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill empty slots if row is not complete
                    repeat(4 - rowApps.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else if (shieldApps.isEmpty()) {
            Text(
                text = "Loading apps...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ShieldAppCard(
    app: com.homecontrol.sensors.data.model.ShieldApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val drawableResId = shieldAppDrawables[app.packageName]

    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App icon: drawable resource or first letter fallback
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ShieldColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (drawableResId != null) {
                    Icon(
                        painter = painterResource(id = drawableResId),
                        contentDescription = app.name,
                        modifier = Modifier.size(28.dp),
                        tint = Color.Unspecified
                    )
                } else {
                    Text(
                        text = (app.name ?: app.packageName.substringAfterLast("."))
                            .take(1)
                            .uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ShieldColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = app.name ?: app.packageName.substringAfterLast("."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
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
