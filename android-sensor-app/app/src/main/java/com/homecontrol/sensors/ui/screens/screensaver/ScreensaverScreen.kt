package com.homecontrol.sensors.ui.screens.screensaver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.homecontrol.sensors.data.model.CalendarEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun ScreensaverScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScreensaverViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        // Background photo with crossfade
        PhotoSlideshow(
            currentPhotoUrl = uiState.currentPhotoUrl,
            nextPhotoUrl = uiState.nextPhotoUrl,
            isTransitioning = uiState.isTransitioning
        )

        // Gradient overlay for better text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f)
                        )
                    )
                )
        )

        // Events and holidays - top left
        if (uiState.todayEvents.isNotEmpty() || uiState.todayHolidays.isNotEmpty()) {
            EventsOverlay(
                events = uiState.todayEvents,
                holidays = uiState.todayHolidays,
                use24HourFormat = uiState.use24HourFormat,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }

        // Weather - top right
        uiState.weather?.let { weather ->
            WeatherOverlay(
                temperature = weather.current.temp,
                condition = weather.current.condition,
                serverUrl = uiState.serverUrl,
                icon = weather.current.icon,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }

        // Bottom area - Clock on right, Spotify player on left
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Spotify player - bottom left
            uiState.playback?.let { playback ->
                if (playback.item != null) {
                    SpotifyOverlay(
                        playback = playback,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous,
                        modifier = Modifier.widthIn(max = 400.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
            } ?: Spacer(modifier = Modifier.width(1.dp))

            // Clock - bottom right
            ClockOverlay(
                time = uiState.currentTime,
                date = uiState.currentDate
            )
        }
    }
}

@Composable
private fun PhotoSlideshow(
    currentPhotoUrl: String?,
    nextPhotoUrl: String?,
    isTransitioning: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Current photo with blurred background
        currentPhotoUrl?.let { url ->
            // Blurred background layer (fills entire screen)
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(50.dp),
                contentScale = ContentScale.Crop
            )

            // Main photo (preserves aspect ratio)
            AsyncImage(
                model = url,
                contentDescription = "Slideshow photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Next photo (fades in during transition)
        AnimatedVisibility(
            visible = isTransitioning && nextPhotoUrl != null,
            enter = fadeIn(animationSpec = tween(1000)),
            exit = fadeOut(animationSpec = tween(0))
        ) {
            nextPhotoUrl?.let { url ->
                // Blurred background for next photo
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(50.dp),
                    contentScale = ContentScale.Crop
                )

                // Main next photo (preserves aspect ratio)
                AsyncImage(
                    model = url,
                    contentDescription = "Next slideshow photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

// Text style with black outline for visibility on any background
private val textOutlineShadow = Shadow(
    color = Color.Black,
    offset = Offset(2f, 2f),
    blurRadius = 4f
)

@Composable
private fun ClockOverlay(
    time: String,
    date: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = time,
            style = TextStyle(
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                letterSpacing = (-2).sp,
                shadow = textOutlineShadow
            )
        )
        Text(
            text = date,
            style = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.9f),
                shadow = textOutlineShadow
            )
        )
    }
}

@Composable
private fun WeatherOverlay(
    temperature: Double,
    condition: String,
    serverUrl: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Weather icon
        AsyncImage(
            model = "${serverUrl}icon/$icon",
            contentDescription = condition,
            modifier = Modifier.size(56.dp),
            contentScale = ContentScale.Fit
        )

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${temperature.roundToInt()}°",
                style = TextStyle(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    shadow = textOutlineShadow
                )
            )
            Text(
                text = condition,
                style = TextStyle(
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    shadow = textOutlineShadow
                )
            )
        }
    }
}

@Composable
private fun SpotifyOverlay(
    playback: com.homecontrol.sensors.data.model.SpotifyPlayback,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playback.item ?: return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume clicks to prevent dismissing screensaver */ }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Album art
        val albumArt = track.album.images.firstOrNull()?.url
        AsyncImage(
            model = albumArt,
            contentDescription = "Album art",
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        // Track info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artists.joinToString(", ") { it.name },
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playback.isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun EventsOverlay(
    events: List<CalendarEvent>,
    holidays: List<com.homecontrol.sensors.ui.screens.calendar.Holiday> = emptyList(),
    use24HourFormat: Boolean,
    modifier: Modifier = Modifier
) {
    val timeFormatter12 = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val timeFormatter24 = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val isoParser = remember { DateTimeFormatter.ISO_DATE_TIME }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 18.dp, vertical = 12.dp),  // 50% more horizontal padding
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Today's Events",
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                shadow = textOutlineShadow,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
            )
        )

        // Display holidays first
        holidays.forEach { holiday ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Holiday",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFFFFD700).copy(alpha = 0.9f),  // Gold color for holidays
                        shadow = textOutlineShadow
                    ),
                    modifier = Modifier.widthIn(min = 60.dp)
                )
                Text(
                    text = holiday.name,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFD700),  // Gold color for holidays
                        shadow = textOutlineShadow
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        events.take(10 - holidays.size.coerceAtMost(3)).forEach { event ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Time or "All Day"
                val timeText = if (event.allDay) {
                    "All Day"
                } else {
                    try {
                        val startDateTime = LocalDateTime.parse(event.start, isoParser)
                        val formatter = if (use24HourFormat) timeFormatter24 else timeFormatter12
                        startDateTime.format(formatter)
                    } catch (e: Exception) {
                        event.start.substringAfter("T").substringBefore(":")
                            .let { if (it.length <= 5) it else "" }
                    }
                }

                Text(
                    text = timeText,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.7f),
                        shadow = textOutlineShadow
                    ),
                    modifier = Modifier.widthIn(min = 60.dp)
                )

                // Event title with optional color indicator
                val eventColor = event.color?.let { colorHex ->
                    try {
                        Color(android.graphics.Color.parseColor(colorHex))
                    } catch (e: Exception) { null }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Color dot if event has a color
                    eventColor?.let { color ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color, CircleShape)
                        )
                    }

                    Text(
                        text = event.title,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White,
                            shadow = textOutlineShadow
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        val totalItems = holidays.size + events.size
        val displayedEvents = 10 - holidays.size.coerceAtMost(3)
        if (events.size > displayedEvents) {
            Text(
                text = "+${events.size - displayedEvents} more",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.5f),
                    shadow = textOutlineShadow
                )
            )
        }
    }
}
