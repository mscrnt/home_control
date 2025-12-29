package com.homecontrol.sensors.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.homecontrol.sensors.R
import com.homecontrol.sensors.data.model.SpotifyPlayback
import com.homecontrol.sensors.data.model.SpotifyPlayRequest
import com.homecontrol.sensors.data.repository.SpotifyRepository
import com.homecontrol.sensors.ui.theme.HomeControlColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private val SpotifyGreen = Color(0xFF1DB954)

@HiltViewModel
class MiniSpotifyPlayerViewModel @Inject constructor(
    private val repository: SpotifyRepository
) : ViewModel() {

    private val _playback = MutableStateFlow<SpotifyPlayback?>(null)
    val playback: StateFlow<SpotifyPlayback?> = _playback.asStateFlow()

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                repository.getPlayback()
                    .onSuccess { _playback.value = it }
                delay(2000) // Poll every 2 seconds
            }
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            val current = _playback.value
            if (current?.isPlaying == true) {
                repository.pause()
            } else {
                repository.play(SpotifyPlayRequest())
            }
            delay(100)
            repository.getPlayback().onSuccess { _playback.value = it }
        }
    }

    fun next() {
        viewModelScope.launch {
            repository.next()
            delay(100)
            repository.getPlayback().onSuccess { _playback.value = it }
        }
    }

    fun previous() {
        viewModelScope.launch {
            repository.previous()
            delay(100)
            repository.getPlayback().onSuccess { _playback.value = it }
        }
    }

    fun setVolume(volume: Int) {
        viewModelScope.launch {
            repository.setVolume(volume)
        }
    }
}

@Composable
fun MiniSpotifyPlayer(
    modifier: Modifier = Modifier,
    viewModel: MiniSpotifyPlayerViewModel = hiltViewModel()
) {
    val playback by viewModel.playback.collectAsState()
    val track = playback?.item
    val albumArt = track?.album?.images?.firstOrNull()?.url
    val volume = playback?.device?.volumePercent ?: 50

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HomeControlColors.cardBorder())
            .padding(12.dp)
    ) {
        // Header with Spotify logo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_spotify),
                contentDescription = "Spotify",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.labelMedium,
                color = SpotifyGreen,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (track != null) {
            // Track info row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Album art
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(HomeControlColors.cardBackgroundSolid())
                ) {
                    if (albumArt != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(albumArt)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Album art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Track and artist name
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artists.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.previous() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(SpotifyGreen)
                ) {
                    Icon(
                        imageVector = if (playback?.isPlaying == true)
                            Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playback?.isPlaying == true) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.next() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Volume slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { viewModel.setVolume(it.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = SpotifyGreen,
                        activeTrackColor = SpotifyGreen,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                )
            }
        } else {
            // No playback
            Text(
                text = "No active playback",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
