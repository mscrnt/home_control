package com.homecontrol.sensors.ui.screens.entertainment

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// Volume/Channel rocker button configuration
data class RockerButtonConfig(
    val label: String,
    val onMinus: () -> Unit,
    val onPlus: () -> Unit
)

/**
 * A reusable remote control template with a consistent two-column layout.
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
    rockerButtons: List<RockerButtonConfig> = emptyList(),
    modifier: Modifier = Modifier,
    rightContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Left column - Remote control panel (narrower)
        Surface(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E2530),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Power toggle - minimal design
                MinimalPowerToggle(
                    isOn = isPowerOn,
                    accentColor = accentColor,
                    onToggle = onPowerToggle
                )

                // D-Pad with refined design
                ProfessionalDPad(
                    accentColor = accentColor,
                    onPress = onDPadPress,
                    size = 190.dp
                )

                // Rocker buttons with refined styling
                if (rockerButtons.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rockerButtons.forEach { rocker ->
                            ProfessionalRocker(
                                label = rocker.label,
                                onMinus = rocker.onMinus,
                                onPlus = rocker.onPlus,
                                accentColor = accentColor
                            )
                        }
                    }
                }

                // Navigation buttons - refined icons
                if (navButtons.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navButtons.forEach { button ->
                            ProfessionalNavButton(
                                icon = button.icon,
                                label = button.label,
                                onClick = button.onClick,
                                accentColor = accentColor,
                                rotation = button.rotation
                            )
                        }
                    }
                }

                // Media buttons - clean design
                if (mediaButtons.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        mediaButtons.forEach { button ->
                            if (button.isPrimary) {
                                ProfessionalPrimaryButton(
                                    icon = button.icon,
                                    onClick = button.onClick,
                                    accentColor = accentColor
                                )
                            } else {
                                ProfessionalMediaButton(
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

        // Right column - Device-specific content
        Card(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E2530).copy(alpha = 0.7f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                rightContent?.invoke()
            }
        }
    }
}

@Composable
private fun MinimalPowerToggle(
    isOn: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val offsetX by animateDpAsState(
        targetValue = if (isOn) 20.dp else (-20).dp,
        label = "power_toggle"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "OFF",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (!isOn) FontWeight.Bold else FontWeight.Normal,
            color = if (!isOn) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f),
            fontSize = 10.sp
        )

        Box(
            modifier = Modifier
                .width(72.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isOn) accentColor.copy(alpha = 0.3f)
                    else Color(0xFF2A3441)
                )
                .border(
                    width = 1.dp,
                    color = if (isOn) accentColor.copy(alpha = 0.5f) else Color(0xFF3A4451),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .offset(x = offsetX)
                    .size(26.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        if (isOn) accentColor
                        else Color(0xFF4A5461)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⏻",
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }

        Text(
            text = "ON",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isOn) FontWeight.Bold else FontWeight.Normal,
            color = if (isOn) accentColor else Color.White.copy(alpha = 0.3f),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ProfessionalDPad(
    accentColor: Color,
    onPress: (DPadDirection) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp
) {
    val buttonSize = size / 4f
    val buttonOffset = buttonSize * 1.15f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer ring background
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 3.5f))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2A3441),
                            Color(0xFF1A2331)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF3A4451),
                    shape = RoundedCornerShape(size / 3.5f)
                )
        )

        // Up button
        DPadDirectionButton(
            onClick = { onPress(DPadDirection.UP) },
            accentColor = accentColor,
            rotation = 90f,
            modifier = Modifier
                .size(buttonSize)
                .offset(y = -buttonOffset)
        )

        // Down button
        DPadDirectionButton(
            onClick = { onPress(DPadDirection.DOWN) },
            accentColor = accentColor,
            rotation = -90f,
            modifier = Modifier
                .size(buttonSize)
                .offset(y = buttonOffset)
        )

        // Left button
        DPadDirectionButton(
            onClick = { onPress(DPadDirection.LEFT) },
            accentColor = accentColor,
            rotation = 0f,
            modifier = Modifier
                .size(buttonSize)
                .offset(x = -buttonOffset)
        )

        // Right button
        DPadDirectionButton(
            onClick = { onPress(DPadDirection.RIGHT) },
            accentColor = accentColor,
            rotation = 180f,
            modifier = Modifier
                .size(buttonSize)
                .offset(x = buttonOffset)
        )

        // Center OK button
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        Box(
            modifier = Modifier
                .size(buttonSize * 1.1f)
                .shadow(if (isPressed) 2.dp else 6.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = if (isPressed) listOf(accentColor.copy(alpha = 0.8f), accentColor)
                        else listOf(accentColor, accentColor.copy(alpha = 0.8f))
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onPress(DPadDirection.CENTER) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "OK",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun DPadDirectionButton(
    onClick: () -> Unit,
    accentColor: Color,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_circle),
            contentDescription = null,
            tint = if (isPressed) accentColor else accentColor.copy(alpha = 0.7f),
            modifier = Modifier
                .size(36.dp)
                .rotate(rotation)
        )
    }
}

@Composable
private fun ProfessionalRocker(
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Pill-shaped rocker
        Surface(
            modifier = Modifier
                .height(32.dp)
                .width(76.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF2A3441),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minus
                RockerHalf(
                    text = "−",
                    onClick = onMinus,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(Color(0xFF3A4451))
                )

                // Plus
                RockerHalf(
                    text = "+",
                    onClick = onPlus,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RockerHalf(
    text: String,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (isPressed) accentColor.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = if (isPressed) accentColor else accentColor.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ProfessionalNavButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    accentColor: Color,
    rotation: Float = 0f
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = if (isPressed) accentColor.copy(alpha = 0.2f) else Color(0xFF2A3441),
            tonalElevation = 2.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isPressed) accentColor else accentColor.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotation)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 9.sp
        )
    }
}

@Composable
private fun ProfessionalMediaButton(
    icon: ImageVector,
    onClick: () -> Unit,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = if (isPressed) accentColor.copy(alpha = 0.2f) else Color(0xFF2A3441)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPressed) accentColor else accentColor.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ProfessionalPrimaryButton(
    icon: ImageVector,
    onClick: () -> Unit,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        modifier = Modifier
            .size(40.dp)
            .shadow(if (isPressed) 2.dp else 4.dp, CircleShape),
        shape = CircleShape,
        color = accentColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
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
