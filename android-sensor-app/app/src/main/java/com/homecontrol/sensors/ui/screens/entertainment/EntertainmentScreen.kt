package com.homecontrol.sensors.ui.screens.entertainment

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homecontrol.sensors.R
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
                EntertainmentContent()
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun EntertainmentContent() {
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Activities",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )

            activities.forEach { activity ->
                ActivityCard(
                    activity = activity,
                    onClick = { /* TODO: Implement activity launch */ }
                )
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (DeviceTab.entries[selectedTab]) {
                        DeviceTab.ACTIVITY -> ActivityRemotePlaceholder()
                        DeviceTab.TV -> DevicePlaceholder("TV Controls")
                        DeviceTab.SHIELD -> DevicePlaceholder("Shield Controls")
                        DeviceTab.SOUNDBAR -> DevicePlaceholder("Soundbar Controls")
                        DeviceTab.XBOX -> DevicePlaceholder("Xbox Controls")
                        DeviceTab.PS5 -> DevicePlaceholder("PS5 Controls")
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
