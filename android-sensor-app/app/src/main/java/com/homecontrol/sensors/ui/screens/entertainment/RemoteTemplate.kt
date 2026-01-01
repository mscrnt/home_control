package com.homecontrol.sensors.ui.screens.entertainment

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homecontrol.sensors.R

// D-Pad direction
enum class DPadDirection {
    UP, DOWN, LEFT, RIGHT, CENTER
}

// Navigation button configuration
data class NavButtonConfig(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val rotation: Float = 0f
)

// Media button configuration
data class MediaButtonConfig(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val isPrimary: Boolean = false
)

/**
 * A reusable remote control template with a consistent two-column layout.
 *
 * Left column (1/3 width):
 *   - Top 2/3: Power toggle + D-pad
 *   - Bottom 1/3: Navigation + Media buttons
 *
 * Right column (2/3 width):
 *   - Custom content for each device
 *
 * @param deviceName The name of the device being controlled
 * @param accentColor The accent color for the remote controls
 * @param isPowerOn Whether the device is powered on (for power toggle position)
 * @param onPowerToggle Callback for power toggle
 * @param onDPadPress Callback for D-pad button presses
 * @param navButtons List of navigation buttons (Back, Home, Menu, etc.)
 * @param mediaButtons List of media control buttons
 * @param rightContent Composable content for the right column
 */
@Composable
fun RemoteTemplate(
    deviceName: String,
    accentColor: Color,
    isPowerOn: Boolean = false,
    onPowerToggle: () -> Unit = {},
    onDPadPress: (DPadDirection) -> Unit,
    navButtons: List<NavButtonConfig> = emptyList(),
    mediaButtons: List<MediaButtonConfig> = emptyList(),
    modifier: Modifier = Modifier,
    rightContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left column - 1/3 width
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Device name
            Text(
                text = deviceName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Top section (2/3 height) - Power toggle + D-pad
            Column(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // Power toggle with Off/On labels
                PowerToggleWithLabels(
                    isOn = isPowerOn,
                    accentColor = accentColor,
                    onToggle = onPowerToggle
                )

                Spacer(modifier = Modifier.height(24.dp))

                // D-Pad - larger with more spacing
                DPad(
                    accentColor = accentColor,
                    onPress = onDPadPress,
                    size = 200.dp,
                    buttonSpacing = 1.15f
                )
            }

            // Bottom section (1/3 height) - Navigation + Media buttons
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Navigation buttons row
                if (navButtons.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navButtons.forEach { button ->
                            SmallIconButton(
                                icon = button.icon,
                                label = button.label,
                                onClick = button.onClick,
                                accentColor = accentColor,
                                rotation = button.rotation
                            )
                        }
                    }
                }

                // Media buttons row
                if (mediaButtons.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        mediaButtons.forEach { button ->
                            if (button.isPrimary) {
                                PrimaryMediaButton(
                                    icon = button.icon,
                                    onClick = button.onClick,
                                    accentColor = accentColor
                                )
                            } else {
                                SmallMediaButton(
                                    icon = button.icon,
                                    onClick = button.onClick,
                                    accentColor = accentColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // Right column - 2/3 width
        Card(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                rightContent?.invoke()
            }
        }
    }
}

@Composable
fun PowerToggleWithLabels(
    isOn: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Off label
        Text(
            text = "Off",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (!isOn) FontWeight.Bold else FontWeight.Normal,
            color = if (!isOn) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        // Power toggle
        PowerToggle(
            isOn = isOn,
            accentColor = accentColor,
            onToggle = onToggle
        )

        // On label
        Text(
            text = "On",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isOn) FontWeight.Bold else FontWeight.Normal,
            color = if (isOn) accentColor
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun PowerToggle(
    isOn: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val offsetX by animateDpAsState(
        targetValue = if (isOn) 24.dp else (-24).dp,
        label = "power_toggle_offset"
    )

    Box(
        modifier = modifier
            .width(100.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (isOn) accentColor.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        // Power icon that slides
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isOn) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⏻",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}

@Composable
fun DPad(
    accentColor: Color,
    onPress: (DPadDirection) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    buttonSpacing: Float = 1f
) {
    val buttonSize = size / 3.5f
    val buttonOffset = buttonSize * buttonSpacing

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Background shape
        Card(
            modifier = Modifier.size(size),
            shape = RoundedCornerShape(size / 4),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {}

        // Up button
        DPadButton(
            onClick = { onPress(DPadDirection.UP) },
            accentColor = accentColor,
            rotation = 90f,
            modifier = Modifier
                .size(buttonSize)
                .offset(y = -buttonOffset)
        )

        // Down button
        DPadButton(
            onClick = { onPress(DPadDirection.DOWN) },
            accentColor = accentColor,
            rotation = -90f,
            modifier = Modifier
                .size(buttonSize)
                .offset(y = buttonOffset)
        )

        // Left button
        DPadButton(
            onClick = { onPress(DPadDirection.LEFT) },
            accentColor = accentColor,
            rotation = 0f,
            modifier = Modifier
                .size(buttonSize)
                .offset(x = -buttonOffset)
        )

        // Right button
        DPadButton(
            onClick = { onPress(DPadDirection.RIGHT) },
            accentColor = accentColor,
            rotation = 180f,
            modifier = Modifier
                .size(buttonSize)
                .offset(x = buttonOffset)
        )

        // Center/OK button
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(accentColor)
                .clickable { onPress(DPadDirection.CENTER) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "OK",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun DPadButton(
    onClick: () -> Unit,
    accentColor: Color,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_circle),
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier
                .size(48.dp)
                .rotate(rotation)
        )
    }
}

@Composable
private fun SmallIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    accentColor: Color,
    rotation: Float = 0f
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f
        )
    }
}

@Composable
private fun SmallMediaButton(
    icon: ImageVector,
    onClick: () -> Unit,
    accentColor: Color
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PrimaryMediaButton(
    icon: ImageVector,
    onClick: () -> Unit,
    accentColor: Color
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(accentColor)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

// Helper function to create standard navigation buttons
fun standardNavButtons(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onMenu: () -> Unit,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
    homeIcon: ImageVector = Icons.Default.Home,
    menuIcon: ImageVector = Icons.Default.Menu
): List<NavButtonConfig> = listOf(
    NavButtonConfig(backIcon, "Back", onBack),
    NavButtonConfig(homeIcon, "Home", onHome),
    NavButtonConfig(menuIcon, "Menu", onMenu)
)

// Helper function to create standard media buttons
fun standardMediaButtons(
    onRewind: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onPlayPause: () -> Unit,
    onNext: (() -> Unit)? = null,
    onFastForward: (() -> Unit)? = null,
    isPlaying: Boolean = false
): List<MediaButtonConfig> = buildList {
    onRewind?.let { add(MediaButtonConfig(Icons.Default.FastRewind, "Rewind", it)) }
    onPrevious?.let { add(MediaButtonConfig(Icons.Default.SkipPrevious, "Previous", it)) }
    add(MediaButtonConfig(
        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        label = if (isPlaying) "Pause" else "Play",
        onClick = onPlayPause,
        isPrimary = true
    ))
    onNext?.let { add(MediaButtonConfig(Icons.Default.SkipNext, "Next", it)) }
    onFastForward?.let { add(MediaButtonConfig(Icons.Default.FastForward, "Fast Forward", it)) }
}
