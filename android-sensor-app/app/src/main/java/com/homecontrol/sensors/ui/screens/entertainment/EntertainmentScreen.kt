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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SettingsInputHdmi
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.homecontrol.sensors.R
import com.homecontrol.sensors.data.model.PS5State
import com.homecontrol.sensors.data.model.SonySoundSetting
import com.homecontrol.sensors.data.model.SonyState
import com.homecontrol.sensors.data.model.XboxState
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

// Helper function to turn off picture on Sony TV (audio continues)
private suspend fun sonyPictureOff(baseUrl: String, deviceName: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val encodedName = java.net.URLEncoder.encode(deviceName, "UTF-8")
        val url = URL("$baseUrl/api/entertainment/sony/$encodedName/picture-off")
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        val responseCode = connection.responseCode
        connection.disconnect()
        responseCode in 200..299
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

// Helper function to turn picture back on for Sony TV
private suspend fun sonyPictureOn(baseUrl: String, deviceName: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val encodedName = java.net.URLEncoder.encode(deviceName, "UTF-8")
        val url = URL("$baseUrl/api/entertainment/sony/$encodedName/picture-on")
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
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
            // Header to align with tab row - match TabRow height (48dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Activities",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            text = "Power Off",
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
                            viewModel = viewModel
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.bodyLarge,
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
                    modifier = Modifier.size(22.dp)
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

// Soundbar input definitions
private data class SoundbarInput(
    val uri: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val soundbarInputs = listOf(
    SoundbarInput("extInput:tv", "TV", Icons.Default.Tv),
    SoundbarInput("extInput:btAudio", "Bluetooth", Icons.Default.Bluetooth),
    SoundbarInput("extInput:hdmi?port=1", "HDMI", Icons.Default.SettingsInputHdmi)
)

// Sound mode display names
private val soundModeNames = mapOf(
    "clearAudio" to "ClearAudio+",
    "3dsurround" to "3D Surround",
    "movie2" to "Movie",
    "music" to "Music",
    "sports" to "Sports",
    "game" to "Game",
    "standard" to "Standard"
)

// Voice mode display names
private val voiceModeNames = mapOf(
    "type1" to "Off",
    "type2" to "Level 1",
    "type3" to "Level 2"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundbarTab(
    uiState: EntertainmentUiState,
    viewModel: EntertainmentViewModel
) {
    // Find soundbar device specifically
    val soundbarDevice = uiState.devices.sony.find {
        uiState.sonyStates[it.name]?.type == "soundbar" || it.name.lowercase().contains("soundbar")
    } ?: uiState.devices.sony.firstOrNull()

    val sonyState = soundbarDevice?.let { uiState.sonyStates[it.name] }
    val soundSettings = soundbarDevice?.let { uiState.sonySoundSettings[it.name] } ?: emptyList()

    // Get current subwoofer level from settings
    val subwooferLevel = soundSettings.find { it.target == "subwooferLevel" }
    val currentSubLevel = subwooferLevel?.currentValue?.toIntOrNull() ?: 6

    if (soundbarDevice == null) {
        DevicePlaceholder("Soundbar")
        return
    }

    RemoteTemplate(
        deviceName = soundbarDevice.name,
        accentColor = SoundbarBlue,
        isPowerOn = sonyState?.power == true,
        onPowerToggle = { viewModel.toggleSonyPower(soundbarDevice.name) },
        onDPadPress = { /* Soundbar doesn't use D-pad navigation */ },
        rockerButtons = listOf(
            RockerButtonConfig(
                label = "Vol",
                onMinus = { viewModel.setSonyVolume(soundbarDevice.name, ((sonyState?.volume ?: 0) - 2).coerceAtLeast(0)) },
                onPlus = { viewModel.setSonyVolume(soundbarDevice.name, ((sonyState?.volume ?: 0) + 2).coerceAtMost(50)) }
            ),
            RockerButtonConfig(
                label = "S/W",
                onMinus = { viewModel.setSonySoundSetting(soundbarDevice.name, "subwooferLevel", (currentSubLevel - 1).coerceAtLeast(0).toString()) },
                onPlus = { viewModel.setSonySoundSetting(soundbarDevice.name, "subwooferLevel", (currentSubLevel + 1).coerceAtMost(12).toString()) }
            )
        ),
        navButtons = standardNavButtons(
            onBack = { viewModel.sendSonyCommand(soundbarDevice.name, "Return") },
            onHome = { viewModel.sendSonyCommand(soundbarDevice.name, "Home") },
            onMenu = { viewModel.sendSonyCommand(soundbarDevice.name, "Options") }
        ),
        mediaButtons = standardMediaButtons(
            onPlayPause = { viewModel.sendSonyCommand(soundbarDevice.name, "Play") }
        ),
        rightContent = {
            SoundbarRightPanel(
                sonyState = sonyState,
                soundSettings = soundSettings,
                deviceName = soundbarDevice.name,
                viewModel = viewModel
            )
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundbarRightPanel(
    sonyState: SonyState?,
    soundSettings: List<SonySoundSetting>,
    deviceName: String,
    viewModel: EntertainmentViewModel
) {
    // Extract settings by target
    val soundField = soundSettings.find { it.target == "soundField" }
    val nightMode = soundSettings.find { it.target == "nightMode" }
    val voiceMode = soundSettings.find { it.target == "voice" }
    val subwooferLevel = soundSettings.find { it.target == "subwooferLevel" }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Input Sources Section
        SoundbarSectionCard(title = "Input") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                soundbarInputs.forEach { input ->
                    val isSelected = sonyState?.input == input.uri
                    SoundbarInputButton(
                        label = input.label,
                        icon = input.icon,
                        isSelected = isSelected,
                        onClick = { viewModel.setSonyInput(deviceName, input.uri) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Sound Mode Section
        if (soundField != null) {
            SoundbarSectionCard(title = "Sound Mode") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    soundField.candidate.filter { it.isAvailable && it.value.isNotEmpty() }.forEach { candidate ->
                        val isSelected = soundField.currentValue == candidate.value
                        SoundModeChip(
                            label = soundModeNames[candidate.value] ?: candidate.title.ifEmpty { candidate.value },
                            isSelected = isSelected,
                            onClick = { viewModel.setSonySoundSetting(deviceName, "soundField", candidate.value) }
                        )
                    }
                }
            }
        }

        // Quick Settings Section (Night Mode & Voice)
        SoundbarSectionCard(title = "Settings") {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Night Mode Toggle
                if (nightMode != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NightsStay,
                                contentDescription = null,
                                tint = if (nightMode.currentValue == "on") SoundbarBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Night Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Switch(
                            checked = nightMode.currentValue == "on",
                            onCheckedChange = { checked ->
                                viewModel.setSonySoundSetting(deviceName, "nightMode", if (checked) "on" else "off")
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SoundbarBlue,
                                checkedTrackColor = SoundbarBlue.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                // Voice Enhancement
                if (voiceMode != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = if (voiceMode.currentValue != "type1") SoundbarBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Voice",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            voiceMode.candidate.filter { it.isAvailable && it.value.isNotEmpty() }.forEach { candidate ->
                                val isSelected = voiceMode.currentValue == candidate.value
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setSonySoundSetting(deviceName, "voice", candidate.value) },
                                    label = {
                                        Text(
                                            text = voiceModeNames[candidate.value] ?: candidate.title,
                                            style = MaterialTheme.typography.labelSmall
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

                // Subwoofer Level
                if (subwooferLevel != null) {
                    val sliderCandidate = subwooferLevel.candidate.firstOrNull { it.min != null }
                    val min = sliderCandidate?.min ?: 0
                    val max = sliderCandidate?.max ?: 12
                    val currentValue = subwooferLevel.currentValue.toIntOrNull() ?: 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speaker,
                                contentDescription = null,
                                tint = SoundbarBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Subwoofer",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val newValue = (currentValue - 1).coerceAtLeast(min)
                                    viewModel.setSonySoundSetting(deviceName, "subwooferLevel", newValue.toString())
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(Icons.Default.Remove, "Decrease", tint = SoundbarBlue, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = currentValue.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SoundbarBlue
                            )
                            IconButton(
                                onClick = {
                                    val newValue = (currentValue + 1).coerceAtMost(max)
                                    viewModel.setSonySoundSetting(deviceName, "subwooferLevel", newValue.toString())
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(Icons.Default.Add, "Increase", tint = SoundbarBlue, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Volume Section
        SoundbarSectionCard(title = "Volume") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.setSonyVolume(deviceName, ((sonyState?.volume ?: 0) - 2).coerceAtLeast(0)) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.VolumeDown, "Volume Down", tint = SoundbarBlue)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "${sonyState?.volume ?: 0}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (sonyState?.muted == true)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else SoundbarBlue
                    )
                    if (sonyState?.muted == true) {
                        Text(
                            text = "Muted",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Red
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.toggleSonyMute(deviceName) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (sonyState?.muted == true) Color.Red.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        if (sonyState?.muted == true) Icons.Default.VolumeOff else Icons.Default.VolumeMute,
                        "Mute",
                        tint = if (sonyState?.muted == true) Color.Red else SoundbarBlue
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { viewModel.setSonyVolume(deviceName, ((sonyState?.volume ?: 0) + 2).coerceAtMost(50)) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.VolumeUp, "Volume Up", tint = SoundbarBlue)
                }
            }
        }
    }
}

@Composable
private fun SoundbarSectionCard(
    title: String?,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            content()
        }
    }
}

@Composable
private fun SoundbarInputButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) SoundbarBlue else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SoundModeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SoundbarBlue,
            selectedLabelColor = Color.White
        )
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
    // Find TV device (not soundbar) - look for device with "tv" in type or name
    val tvDevice = uiState.devices.sony.find {
        it.name.contains("tv", ignoreCase = true)
    } ?: uiState.devices.sony.firstOrNull()

    val sonyState = tvDevice?.let { uiState.sonyStates[it.name] }

    if (tvDevice == null) {
        DevicePlaceholder("TV")
        return
    }

    // D-Pad key mappings
    val dpadKeyMap = mapOf(
        DPadDirection.UP to "up",
        DPadDirection.DOWN to "down",
        DPadDirection.LEFT to "left",
        DPadDirection.RIGHT to "right",
        DPadDirection.CENTER to "enter"
    )

    RemoteTemplate(
        deviceName = tvDevice.name,
        accentColor = TVColor,
        isPowerOn = sonyState?.power == true,
        onPowerToggle = { viewModel.toggleSonyPower(tvDevice.name) },
        onDPadPress = { direction ->
            dpadKeyMap[direction]?.let { key ->
                viewModel.sendSonyCommand(tvDevice.name, key)
            }
        },
        navButtons = standardNavButtons(
            onBack = { viewModel.sendSonyCommand(tvDevice.name, "back") },
            onHome = { viewModel.sendSonyCommand(tvDevice.name, "home") },
            onMenu = { viewModel.sendSonyCommand(tvDevice.name, "options") }
        ),
        mediaButtons = standardMediaButtons(
            onRewind = { viewModel.sendSonyCommand(tvDevice.name, "rewind") },
            onPlayPause = { viewModel.sendSonyCommand(tvDevice.name, "play") },
            onFastForward = { viewModel.sendSonyCommand(tvDevice.name, "forward") }
        ),
        rightContent = {
            TVRightPanel(
                sonyState = sonyState,
                deviceName = tvDevice.name,
                viewModel = viewModel
            )
        }
    )
}

// HDMI input labels (what device is plugged into each port)
private val hdmiLabels = mapOf(
    1 to "Xbox",
    2 to "PS5",
    3 to "Switch",
    4 to "Shield"
)

@Composable
private fun TVRightPanel(
    sonyState: com.homecontrol.sensors.data.model.SonyState?,
    deviceName: String,
    viewModel: EntertainmentViewModel
) {
    val coroutineScope = rememberCoroutineScope()
    var isPictureOff by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()

    // Check if audio is routed to external audio system
    val soundSettings = uiState.sonySoundSettings[deviceName] ?: emptyList()
    val outputTerminal = soundSettings.find { it.target == "outputTerminal" }
    val isExternalAudio = outputTerminal?.currentValue == "audioSystem"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // HDMI Inputs Section Card
        TVSectionCard(title = "Inputs") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..4).forEach { port ->
                    TVHdmiButton(
                        port = port,
                        label = hdmiLabels[port] ?: "HDMI $port",
                        onClick = { viewModel.sendSonyCommand(deviceName, "hdmi$port") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Streaming Apps Section Card
        TVSectionCard(title = "Apps") {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // First row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TVAppChip(
                        label = "Netflix",
                        color = Color(0xFFE50914),
                        onClick = { viewModel.sendSonyCommand(deviceName, "netflix") },
                        modifier = Modifier.weight(1f)
                    )
                    TVAppChip(
                        label = "YouTube",
                        color = Color(0xFFFF0000),
                        onClick = { viewModel.sendSonyCommand(deviceName, "youtube") },
                        modifier = Modifier.weight(1f)
                    )
                    TVAppChip(
                        label = "Prime",
                        color = Color(0xFF00A8E1),
                        onClick = { viewModel.sendSonyCommand(deviceName, "primevideo") },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Second row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TVAppChip(
                        label = "Disney+",
                        color = Color(0xFF113CCF),
                        onClick = { viewModel.sendSonyCommand(deviceName, "disney") },
                        modifier = Modifier.weight(1f)
                    )
                    TVAppChip(
                        label = "Apple TV",
                        color = Color(0xFF555555),
                        onClick = { viewModel.sendSonyCommand(deviceName, "appletv") },
                        modifier = Modifier.weight(1f)
                    )
                    TVAppChip(
                        label = "Hulu",
                        color = Color(0xFF1CE783),
                        onClick = { viewModel.launchSonyApp(deviceName, "com.sony.dtv.com.hulu.livingroomplus.com.hulu.livingroomplus.WKFactivity") },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Third row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TVAppChip(
                        label = "HBO",
                        color = Color(0xFF5822B4),
                        onClick = { viewModel.launchSonyApp(deviceName, "com.sony.dtv.com.wbd.stream.com.wbd.beam.BeamActivity") },
                        modifier = Modifier.weight(1f)
                    )
                    TVAppChip(
                        label = "Spotify",
                        color = Color(0xFF1DB954),
                        onClick = { viewModel.launchSonyApp(deviceName, "com.sony.dtv.com.spotify.tv.android.com.spotify.tv.android.SpotifyTVActivity") },
                        modifier = Modifier.weight(1f)
                    )
                    TVAppChip(
                        label = "Plex",
                        color = Color(0xFFE5A00D),
                        onClick = { viewModel.launchSonyApp(deviceName, "com.sony.dtv.com.plexapp.android.com.plexapp.plex.activities.SplashActivity") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Audio & Display Section Card
        TVSectionCard(title = if (isExternalAudio) "Audio & Display" else "Volume & Display") {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Audio Section
                if (isExternalAudio) {
                    // Soundbar indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = SoundbarBlue.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speaker,
                            contentDescription = "External Audio",
                            modifier = Modifier.size(22.dp),
                            tint = SoundbarBlue
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Audio via Soundbar",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = SoundbarBlue
                        )
                    }
                } else {
                    // Volume controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val currentVolume = sonyState?.volume ?: 0
                                viewModel.setSonyVolume(deviceName, (currentVolume - 5).coerceAtLeast(0))
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.VolumeDown, "Volume Down", tint = TVColor)
                        }

                        // Volume display
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "${sonyState?.volume ?: 0}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (sonyState?.muted == true)
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else TVColor
                            )
                            if (sonyState?.muted == true) {
                                Text(
                                    text = "Muted",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Red
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.toggleSonyMute(deviceName) },
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

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                val currentVolume = sonyState?.volume ?: 0
                                viewModel.setSonyVolume(deviceName, (currentVolume + 5).coerceAtMost(100))
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.VolumeUp, "Volume Up", tint = TVColor)
                        }
                    }
                }

                // Display controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Screen toggle
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                if (isPictureOff) {
                                    sonyPictureOn(uiState.serverUrl.trimEnd('/'), deviceName)
                                } else {
                                    sonyPictureOff(uiState.serverUrl.trimEnd('/'), deviceName)
                                }
                                isPictureOff = !isPictureOff
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isPictureOff) Color.Gray else TVColor
                        ),
                        border = BorderStroke(1.dp, if (isPictureOff) Color.Gray else TVColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (isPictureOff) "Screen Off" else "Screen On",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // Action Menu
                    OutlinedButton(
                        onClick = { viewModel.sendSonyCommand(deviceName, "actionMenu") },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TVColor),
                        border = BorderStroke(1.dp, TVColor.copy(alpha = 0.5f))
                    ) {
                        Text("Action", style = MaterialTheme.typography.labelMedium)
                    }

                    // Settings
                    OutlinedButton(
                        onClick = { viewModel.launchSonyApp(deviceName, "com.sony.dtv.com.android.tv.settings.com.android.tv.settings.MainSettings") },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TVColor),
                        border = BorderStroke(1.dp, TVColor.copy(alpha = 0.5f))
                    ) {
                        Text("Settings", style = MaterialTheme.typography.labelMedium)
                    }

                    // Guide
                    OutlinedButton(
                        onClick = { viewModel.sendSonyCommand(deviceName, "guide") },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TVColor),
                        border = BorderStroke(1.dp, TVColor.copy(alpha = 0.5f))
                    ) {
                        Text("Guide", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TVSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            content()
        }
    }
}

@Composable
private fun TVHdmiButton(
    port: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(4.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = TVColor
        ),
        border = BorderStroke(1.dp, TVColor.copy(alpha = 0.4f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "HDMI $port",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun TVAppChip(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
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
private fun XboxControllerPanel(
    xboxState: XboxState?,
    onButtonPress: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Now Playing section with game art
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Game art image
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(XboxColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (xboxState?.imageUrl != null) {
                        AsyncImage(
                            model = xboxState.imageUrl,
                            contentDescription = "Game art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_xbox),
                            contentDescription = "Xbox",
                            modifier = Modifier.size(48.dp),
                            tint = Color.Unspecified
                        )
                    }
                }

                // Game info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = xboxState?.currentTitle ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (xboxState?.currentTitle != null) XboxColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 2
                    )
                    // Genre
                    if (xboxState?.genre != null) {
                        Text(
                            text = xboxState.genre,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                    // Developer/Publisher
                    if (xboxState?.developer != null || xboxState?.publisher != null) {
                        Text(
                            text = xboxState.developer ?: xboxState.publisher ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                    // Progress bar
                    if (xboxState?.progress != null && xboxState.progress > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth((xboxState.progress / 100f).toFloat().coerceIn(0f, 1f))
                                        .background(XboxColor)
                                )
                            }
                            Text(
                                text = "${xboxState.progress.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = XboxColor
                            )
                        }
                    }
                    // Gamerscore
                    if (xboxState?.gamerscore != null && xboxState.gamerscore > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "G",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                            Text(
                                text = "${xboxState.gamerscore}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Face buttons - A B X Y in Xbox layout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Face Buttons",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            // Y button (top)
            XboxFaceButton(
                label = "Y",
                color = Color(0xFFFFB900),
                onClick = { onButtonPress("y") }
            )
            // X and B buttons (middle)
            Row(
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                XboxFaceButton(
                    label = "X",
                    color = Color(0xFF0078D4),
                    onClick = { onButtonPress("x") }
                )
                XboxFaceButton(
                    label = "B",
                    color = Color(0xFFE81123),
                    onClick = { onButtonPress("b") }
                )
            }
            // A button (bottom)
            XboxFaceButton(
                label = "A",
                color = XboxColor,
                onClick = { onButtonPress("a") }
            )
        }

        // Shoulder/Trigger buttons
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Shoulders & Triggers",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                XboxShoulderButton(
                    label = "LT",
                    onClick = { onButtonPress("left_trigger") }
                )
                XboxShoulderButton(
                    label = "RT",
                    onClick = { onButtonPress("right_trigger") }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                XboxShoulderButton(
                    label = "LB",
                    onClick = { onButtonPress("left_shoulder") }
                )
                XboxShoulderButton(
                    label = "RB",
                    onClick = { onButtonPress("right_shoulder") }
                )
            }
        }

        // Utility buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            XboxUtilityButton(
                label = "View",
                onClick = { onButtonPress("view") }
            )
            XboxUtilityButton(
                label = "Menu",
                onClick = { onButtonPress("menu") }
            )
        }
    }
}

@Composable
private fun XboxFaceButton(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = color.copy(alpha = 0.9f),
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun XboxShoulderButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(60.dp)
            .height(36.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF3A3A3A),
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun XboxUtilityButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF2A2A2A)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
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
            val button = when (direction) {
                DPadDirection.UP -> "dpad_up"
                DPadDirection.DOWN -> "dpad_down"
                DPadDirection.LEFT -> "dpad_left"
                DPadDirection.RIGHT -> "dpad_right"
                DPadDirection.CENTER -> "a"
            }
            viewModel.sendXboxButton(xboxDevice.name, button)
        },
        navButtons = standardNavButtons(
            onBack = { viewModel.sendXboxButton(xboxDevice.name, "b") },
            onHome = { viewModel.sendXboxButton(xboxDevice.name, "nexus") },
            onMenu = { viewModel.sendXboxButton(xboxDevice.name, "menu") }
        ),
        mediaButtons = standardMediaButtons(
            onPlayPause = { viewModel.sendXboxMedia(xboxDevice.name, "play_pause") }
        ),
        rightContent = {
            XboxControllerPanel(
                xboxState = xboxState,
                onButtonPress = { button -> viewModel.sendXboxButton(xboxDevice.name, button) }
            )
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
            PS5ContentPanel(ps5State = ps5State)
        }
    )
}

@Composable
private fun PS5ContentPanel(ps5State: PS5State?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Large game art or PS5 logo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(PS5Color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            if (ps5State?.imageUrl != null && ps5State.power == true) {
                // Show game art when playing
                AsyncImage(
                    model = ps5State.imageUrl,
                    contentDescription = "Game art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Gradient overlay at bottom for text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Now Playing",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = ps5State.currentTitle ?: "Unknown",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2
                        )
                    }
                }
            } else {
                // Show PS5 logo when standby or no game art
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_playstation),
                        contentDescription = "PlayStation",
                        modifier = Modifier.size(120.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when {
                            ps5State?.power != true -> "Standby"
                            ps5State.currentTitle != null -> ps5State.currentTitle
                            else -> "Home"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (ps5State?.power == true) PS5Color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    // Online status
                    if (ps5State?.onlineStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (ps5State.onlineStatus) {
                                            "online" -> Color(0xFF4CAF50)
                                            "busy" -> Color(0xFFFF9800)
                                            else -> Color.Gray
                                        }
                                    )
                            )
                            Text(
                                text = ps5State.onlineStatus.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            if (ps5State.onlineId != null) {
                                Text(
                                    text = "• ${ps5State.onlineId}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Trophy section
        if (ps5State?.power == true && (ps5State.platinumTrophies ?: 0) + (ps5State.goldTrophies ?: 0) +
            (ps5State.silverTrophies ?: 0) + (ps5State.bronzeTrophies ?: 0) > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "🏆",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Trophies",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (ps5State.trophyLevel != null && ps5State.trophyLevel > 0) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "Level ${ps5State.trophyLevel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = PS5Color
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Platinum
                        TrophyCount(
                            emoji = "🏆",
                            count = ps5State.platinumTrophies ?: 0,
                            color = Color(0xFFE5E4E2)
                        )
                        // Gold
                        TrophyCount(
                            emoji = "🥇",
                            count = ps5State.goldTrophies ?: 0,
                            color = Color(0xFFFFD700)
                        )
                        // Silver
                        TrophyCount(
                            emoji = "🥈",
                            count = ps5State.silverTrophies ?: 0,
                            color = Color(0xFFC0C0C0)
                        )
                        // Bronze
                        TrophyCount(
                            emoji = "🥉",
                            count = ps5State.bronzeTrophies ?: 0,
                            color = Color(0xFFCD7F32)
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun TrophyCount(
    emoji: String,
    count: Int,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
