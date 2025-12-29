package com.homecontrol.sensors

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homecontrol.sensors.ui.screens.home.HomeScreen
import com.homecontrol.sensors.ui.screens.hue.HueScreen
import com.homecontrol.sensors.ui.screens.calendar.CalendarScreen
import com.homecontrol.sensors.ui.screens.settings.SettingsScreen
import com.homecontrol.sensors.ui.theme.HomeControlColors
import com.homecontrol.sensors.ui.theme.HomeControlTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NativeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HomeControlTheme {
                MainContent()
            }
        }
    }
}

// Modal types for smart home screens
sealed class SmartHomeModal {
    object Home : SmartHomeModal()
    object Lights : SmartHomeModal()
    object Music : SmartHomeModal()
    object Cameras : SmartHomeModal()
    object Media : SmartHomeModal()
    object Settings : SmartHomeModal()
}

data class DrawerNavItem(
    val modal: SmartHomeModal,
    val label: String,
    val icon: ImageVector
)

val smartHomeNavItems = listOf(
    DrawerNavItem(
        modal = SmartHomeModal.Home,
        label = "Home",
        icon = Icons.Filled.Home
    ),
    DrawerNavItem(
        modal = SmartHomeModal.Lights,
        label = "Lights",
        icon = Icons.Filled.Lightbulb
    ),
    DrawerNavItem(
        modal = SmartHomeModal.Music,
        label = "Music",
        icon = Icons.Filled.MusicNote
    ),
    DrawerNavItem(
        modal = SmartHomeModal.Cameras,
        label = "Cameras",
        icon = Icons.Filled.Videocam
    ),
    DrawerNavItem(
        modal = SmartHomeModal.Media,
        label = "Media",
        icon = Icons.Filled.Tv
    ),
    DrawerNavItem(
        modal = SmartHomeModal.Settings,
        label = "Settings",
        icon = Icons.Filled.Settings
    )
)

@Composable
fun MainContent() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Track which modal is currently open (null = none)
    var activeModal by remember { mutableStateOf<SmartHomeModal?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content - Calendar with drawer
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false, // Disable swipe to not interfere with calendar
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(300.dp),
                    drawerContainerColor = HomeControlColors.cardBackgroundSolid()
                ) {
                    SmartHomeDrawerContent(
                        onItemClick = { modal ->
                            scope.launch {
                                drawerState.close()
                                activeModal = modal
                            }
                        },
                        onCalendarClick = {
                            scope.launch {
                                drawerState.close()
                                activeModal = null
                            }
                        },
                        onClose = {
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        ) {
            // Calendar is always the base
            CalendarScreen(
                onOpenDrawer = {
                    scope.launch { drawerState.open() }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Full-screen modal overlay
        AnimatedVisibility(
            visible = activeModal != null,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 }
        ) {
            FullScreenModal(
                title = when (activeModal) {
                    SmartHomeModal.Home -> "Home"
                    SmartHomeModal.Lights -> "hue"
                    SmartHomeModal.Music -> "Spotify"
                    SmartHomeModal.Cameras -> "Cameras"
                    SmartHomeModal.Media -> "Media"
                    SmartHomeModal.Settings -> "Settings"
                    null -> ""
                },
                onClose = { activeModal = null }
            ) {
                when (activeModal) {
                    SmartHomeModal.Home -> HomeScreen()
                    SmartHomeModal.Lights -> HueScreen()
                    SmartHomeModal.Music -> PlaceholderContent("Spotify")
                    SmartHomeModal.Cameras -> PlaceholderContent("Cameras")
                    SmartHomeModal.Media -> PlaceholderContent("Media")
                    SmartHomeModal.Settings -> SettingsScreen()
                    null -> {}
                }
            }
        }
    }
}

@Composable
private fun FullScreenModal(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeControlColors.cardBackgroundSolid())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with title and close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Empty spacer for balance
                Spacer(modifier = Modifier.size(48.dp))

                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PlaceholderContent(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SmartHomeDrawerContent(
    onItemClick: (SmartHomeModal) -> Unit,
    onCalendarClick: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Smart Home",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = HomeControlColors.cardBorder())
        Spacer(modifier = Modifier.height(16.dp))

        // Calendar button (to go back)
        DrawerItem(
            icon = Icons.Filled.CalendarMonth,
            label = "Calendar",
            selected = false,
            onClick = onCalendarClick
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = HomeControlColors.cardBorder())
        Spacer(modifier = Modifier.height(8.dp))

        // Smart home items
        smartHomeNavItems.forEach { item ->
            DrawerItem(
                icon = item.icon,
                label = item.label,
                selected = false,
                onClick = { onItemClick(item.modal) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Version info
        Text(
            text = "Home Control v1.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }

    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor
        )
    }
}
