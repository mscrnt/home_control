package com.homecontrol.sensors.ui.screens.spotify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.homecontrol.sensors.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.homecontrol.sensors.data.model.*
import com.homecontrol.sensors.ui.theme.HomeControlColors

// Spotify brand color
private val SpotifyGreen = Color(0xFF1DB954)

@Composable
fun SpotifyScreen(
    modifier: Modifier = Modifier,
    viewModel: SpotifyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = {}) // Consume clicks to prevent passing through to calendar
    ) {
        if (uiState.isLoading && uiState.playback == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SpotifyGreen)
            }
        } else {
            // Main split layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Left side - Now Playing Panel
                NowPlayingPanel(
                    playback = uiState.playback,
                    serverUrl = uiState.serverUrl,
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onSeek = viewModel::seek,
                    onVolumeChange = viewModel::setVolume,
                    onShuffleToggle = viewModel::toggleShuffle,
                    onRepeatCycle = viewModel::cycleRepeat,
                    onDeviceClick = viewModel::showDevicePicker,
                    onAlbumClick = { uiState.playback?.item?.album?.id?.let { viewModel.openAlbumDetail(it) } },
                    onArtistClick = { uiState.playback?.item?.artists?.firstOrNull()?.id?.let { viewModel.openArtistDetail(it) } },
                    modifier = Modifier
                        .weight(0.30f)
                        .fillMaxHeight()
                )

                // Right side - Browse/Search Panel
                BrowsePanel(
                    uiState = uiState,
                    onTabChange = viewModel::setActiveTab,
                    onLibraryFilterChange = viewModel::setLibraryFilter,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onClearSearch = viewModel::clearSearch,
                    onPlayTrack = viewModel::playTrack,
                    onPlayAlbum = viewModel::playAlbum,
                    onPlayPlaylist = viewModel::playPlaylist,
                    onPlayContext = viewModel::playContext,
                    onAlbumClick = viewModel::openAlbumDetail,
                    onArtistClick = viewModel::openArtistDetail,
                    onPlaylistClick = viewModel::openPlaylistDetail,
                    onLikedSongsClick = viewModel::openLikedSongs,
                    onSectionClick = viewModel::openSectionView,
                    modifier = Modifier
                        .weight(0.70f)
                        .fillMaxHeight()
                )
            }

            // Detail view overlay
            AnimatedVisibility(
                visible = uiState.detailView != null,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut()
            ) {
                DetailViewContent(
                    uiState = uiState,
                    onBack = viewModel::closeDetailView,
                    onPlayTrack = viewModel::playTrack,
                    onPlayAlbum = viewModel::playAlbum,
                    onPlayPlaylist = viewModel::playPlaylist,
                    onPlayContext = viewModel::playContext,
                    onShuffleContext = viewModel::shuffleContext,
                    onAlbumClick = viewModel::openAlbumDetail,
                    onArtistClick = viewModel::openArtistDetail,
                    onPlaylistClick = viewModel::openPlaylistDetail,
                    onToggleAlbumSaved = viewModel::toggleAlbumSaved,
                    onToggleFollowArtist = viewModel::toggleFollowArtist,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Device picker dialog
        if (uiState.showDevicePicker) {
            DevicePickerDialog(
                devices = uiState.devices,
                onDeviceSelected = viewModel::transferToDevice,
                onDismiss = viewModel::hideDevicePicker
            )
        }
    }
}

@Composable
private fun NowPlayingPanel(
    playback: SpotifyPlayback?,
    serverUrl: String,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatCycle: () -> Unit,
    onDeviceClick: () -> Unit,
    onAlbumClick: () -> Unit,
    onArtistClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playback?.item
    val albumArt = track?.album?.images?.firstOrNull()?.url
    val isPlaying = track != null

    Column(
        modifier = modifier
            .background(HomeControlColors.cardBackgroundSolid().copy(alpha = 0.5f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Album art or Spotify logo when nothing playing
        Box(
            modifier = Modifier
                .weight(1f)
                .sizeIn(maxWidth = 280.dp, maxHeight = 280.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(HomeControlColors.cardBorder())
                .clickable(enabled = track != null, onClick = onAlbumClick),
            contentAlignment = Alignment.Center
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
                // Spotify logo when nothing is playing
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_spotify),
                        contentDescription = "Spotify",
                        modifier = Modifier.size(80.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No active playback",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Start playing on any device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Track info
        if (isPlaying) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = track?.name ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = track?.artists?.joinToString(", ") { it.name } ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = track != null, onClick = onArtistClick)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Progress bar
        if (track != null) {
            ProgressBar(
                progressMs = playback.progressMs,
                durationMs = track.durationMs,
                onSeek = onSeek
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Playback controls
        PlaybackControls(
            isPlaying = playback?.isPlaying ?: false,
            shuffleState = playback?.shuffleState ?: false,
            repeatState = playback?.repeatState ?: "off",
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onShuffleToggle = onShuffleToggle,
            onRepeatCycle = onRepeatCycle
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Volume and device
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Volume control
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = (playback?.device?.volumePercent ?: 50) / 100f,
                    onValueChange = { onVolumeChange((it * 100).toInt()) },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = SpotifyGreen,
                        activeTrackColor = SpotifyGreen
                    )
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Device picker button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onDeviceClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = getDeviceIcon(playback?.device?.type),
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = playback?.device?.name ?: "No device",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyGreen
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(
    progressMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit
) {
    var sliderPosition by remember(progressMs) { mutableFloatStateOf(progressMs.toFloat()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderPosition / durationMs.coerceAtLeast(1),
            onValueChange = { sliderPosition = it * durationMs },
            onValueChangeFinished = { onSeek(sliderPosition.toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = SpotifyGreen,
                activeTrackColor = SpotifyGreen,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(sliderPosition.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    shuffleState: Boolean,
    repeatState: String,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatCycle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle
        IconButton(onClick = onShuffleToggle) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (shuffleState) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Previous
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Play/Pause
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(64.dp)
                .background(SpotifyGreen, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.Black,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Next
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Repeat
        IconButton(onClick = onRepeatCycle) {
            Icon(
                imageVector = if (repeatState == "track") Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = "Repeat",
                tint = if (repeatState != "off") SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BrowsePanel(
    uiState: SpotifyUiState,
    onTabChange: (SpotifyTab) -> Unit,
    onLibraryFilterChange: (LibraryFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onPlayTrack: (SpotifyTrack, String?) -> Unit,
    onPlayAlbum: (String, String?) -> Unit,
    onPlayPlaylist: (String, String?) -> Unit,
    onPlayContext: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String, String) -> Unit,
    onLikedSongsClick: () -> Unit,
    onSectionClick: (SectionType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        // Search bar - context aware based on active tab
        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = onSearchQueryChange,
            onClear = onClearSearch,
            placeholder = if (uiState.activeTab == SpotifyTab.LIBRARY) {
                "Search in Your Library"
            } else {
                "Search songs, artists, albums..."
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.searchQuery.isNotBlank()) {
            // Different search behavior based on tab
            if (uiState.activeTab == SpotifyTab.LIBRARY) {
                // Local library filtering
                LibrarySearchResults(
                    query = uiState.searchQuery,
                    playlists = uiState.playlists,
                    albums = uiState.libraryAlbums,
                    artists = uiState.libraryArtists,
                    tracks = uiState.libraryTracks,
                    onPlayTrack = { onPlayTrack(it, null) },
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    onPlaylistClick = onPlaylistClick
                )
            } else {
                // Global Spotify search results
                SearchResults(
                    results = uiState.searchResults,
                    isSearching = uiState.isSearching,
                    onPlayTrack = { onPlayTrack(it, null) },
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    onPlaylistClick = onPlaylistClick
                )
            }
        } else {
            // Tab bar
            TabBar(
                activeTab = uiState.activeTab,
                onTabChange = onTabChange
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (uiState.activeTab) {
                SpotifyTab.HOME -> HomeContent(
                    recentlyPlayed = uiState.recentlyPlayed,
                    topArtists = uiState.topArtists,
                    playlists = uiState.playlists,
                    topTracks = uiState.topTracks,
                    onPlayTrack = { onPlayTrack(it, null) },
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    onPlaylistClick = onPlaylistClick,
                    onSectionClick = onSectionClick
                )

                SpotifyTab.LIBRARY -> LibraryContent(
                    filter = uiState.libraryFilter,
                    onFilterChange = onLibraryFilterChange,
                    playlists = uiState.playlists,
                    albums = uiState.libraryAlbums,
                    artists = uiState.libraryArtists,
                    tracks = uiState.libraryTracks,
                    onPlayTrack = { onPlayTrack(it, null) },
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    onPlaylistClick = onPlaylistClick,
                    onLikedSongsClick = onLikedSongsClick
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String = "Search songs, artists, albums...",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                cursorBrush = SolidColor(SpotifyGreen),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TabBar(
    activeTab: SpotifyTab,
    onTabChange: (SpotifyTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabChip(
            text = "Home",
            selected = activeTab == SpotifyTab.HOME,
            onClick = { onTabChange(SpotifyTab.HOME) }
        )
        TabChip(
            text = "Library",
            selected = activeTab == SpotifyTab.LIBRARY,
            onClick = { onTabChange(SpotifyTab.LIBRARY) }
        )
    }
}

@Composable
private fun TabChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) SpotifyGreen
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeContent(
    recentlyPlayed: List<SpotifyPlayHistoryItem>,
    topArtists: List<SpotifyArtist>,
    playlists: List<SpotifyPlaylist>,
    topTracks: List<SpotifyTrack>,
    onPlayTrack: (SpotifyTrack) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String, String) -> Unit,
    onSectionClick: (SectionType) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Recently Played
        if (recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeader(
                    text = "Recently Played",
                    showAll = recentlyPlayed.size > 10,
                    onShowAllClick = { onSectionClick(SectionType.RECENTLY_PLAYED) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyPlayed.take(10)) { item ->
                        AlbumCard(
                            imageUrl = item.track.album.images.firstOrNull()?.url,
                            title = item.track.name,
                            subtitle = item.track.artists.joinToString(", ") { it.name },
                            onClick = { onAlbumClick(item.track.album.id) }
                        )
                    }
                }
            }
        }

        // Top Artists
        if (topArtists.isNotEmpty()) {
            item {
                SectionHeader(
                    text = "Top Artists",
                    showAll = topArtists.size > 10,
                    onShowAllClick = { onSectionClick(SectionType.TOP_ARTISTS) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(topArtists.take(10)) { artist ->
                        ArtistCard(
                            imageUrl = artist.images.firstOrNull()?.url,
                            name = artist.name,
                            onClick = { onArtistClick(artist.id) }
                        )
                    }
                }
            }
        }

        // Playlists
        if (playlists.isNotEmpty()) {
            item {
                SectionHeader(
                    text = "Your Playlists",
                    showAll = playlists.size > 10,
                    onShowAllClick = { onSectionClick(SectionType.YOUR_PLAYLISTS) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlists.take(10)) { playlist ->
                        PlaylistCard(
                            imageUrl = playlist.images.firstOrNull()?.url,
                            name = playlist.name,
                            onClick = { onPlaylistClick(playlist.id, playlist.name) }
                        )
                    }
                }
            }
        }

        // Jump Back In (Top Tracks)
        if (topTracks.isNotEmpty()) {
            item {
                SectionHeader(
                    text = "Jump Back In",
                    showAll = topTracks.size > 10,
                    onShowAllClick = { onSectionClick(SectionType.JUMP_BACK_IN) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(topTracks.take(10)) { track ->
                        AlbumCard(
                            imageUrl = track.album.images.firstOrNull()?.url,
                            title = track.name,
                            subtitle = track.artists.joinToString(", ") { it.name },
                            onClick = { onPlayTrack(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryContent(
    filter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    playlists: List<SpotifyPlaylist>,
    albums: List<SpotifyAlbum>,
    artists: List<SpotifyArtist>,
    tracks: List<SpotifyTrack>,
    onPlayTrack: (SpotifyTrack) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String, String) -> Unit,
    onLikedSongsClick: () -> Unit
) {
    Column {
        // Filter chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    text = "All",
                    selected = filter == LibraryFilter.ALL,
                    onClick = { onFilterChange(LibraryFilter.ALL) }
                )
            }
            item {
                FilterChip(
                    text = "Playlists",
                    selected = filter == LibraryFilter.PLAYLISTS,
                    onClick = { onFilterChange(LibraryFilter.PLAYLISTS) }
                )
            }
            item {
                FilterChip(
                    text = "Albums",
                    selected = filter == LibraryFilter.ALBUMS,
                    onClick = { onFilterChange(LibraryFilter.ALBUMS) }
                )
            }
            item {
                FilterChip(
                    text = "Artists",
                    selected = filter == LibraryFilter.ARTISTS,
                    onClick = { onFilterChange(LibraryFilter.ARTISTS) }
                )
            }
            item {
                FilterChip(
                    text = "Liked Songs",
                    selected = filter == LibraryFilter.LIKED_SONGS,
                    onClick = { onFilterChange(LibraryFilter.LIKED_SONGS) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content based on filter
        when (filter) {
            LibraryFilter.ALL -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Liked Songs card
                    item {
                        LikedSongsCard(
                            trackCount = tracks.size,
                            onClick = onLikedSongsClick
                        )
                    }

                    items(playlists) { playlist ->
                        LibraryGridItem(
                            imageUrl = playlist.images.firstOrNull()?.url,
                            title = playlist.name,
                            subtitle = "Playlist",
                            onClick = { onPlaylistClick(playlist.id, playlist.name) }
                        )
                    }

                    items(albums) { album ->
                        LibraryGridItem(
                            imageUrl = album.images.firstOrNull()?.url,
                            title = album.name,
                            subtitle = album.artists.joinToString(", ") { it.name },
                            onClick = { onAlbumClick(album.id) }
                        )
                    }

                    items(artists) { artist ->
                        LibraryGridItem(
                            imageUrl = artist.images.firstOrNull()?.url,
                            title = artist.name,
                            subtitle = "Artist",
                            isCircular = true,
                            onClick = { onArtistClick(artist.id) }
                        )
                    }
                }
            }

            LibraryFilter.PLAYLISTS -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlists) { playlist ->
                        LibraryGridItem(
                            imageUrl = playlist.images.firstOrNull()?.url,
                            title = playlist.name,
                            subtitle = "${playlist.tracks?.total ?: 0} songs",
                            onClick = { onPlaylistClick(playlist.id, playlist.name) }
                        )
                    }
                }
            }

            LibraryFilter.ALBUMS -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(albums) { album ->
                        LibraryGridItem(
                            imageUrl = album.images.firstOrNull()?.url,
                            title = album.name,
                            subtitle = album.artists.joinToString(", ") { it.name },
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                }
            }

            LibraryFilter.ARTISTS -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(artists) { artist ->
                        LibraryGridItem(
                            imageUrl = artist.images.firstOrNull()?.url,
                            title = artist.name,
                            subtitle = "Artist",
                            isCircular = true,
                            onClick = { onArtistClick(artist.id) }
                        )
                    }
                }
            }

            LibraryFilter.LIKED_SONGS -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(tracks) { track ->
                        TrackListItem(
                            track = track,
                            onClick = { onPlayTrack(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (selected) Modifier.background(SpotifyGreen)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SearchResults(
    results: SpotifySearchResponse?,
    isSearching: Boolean,
    onPlayTrack: (SpotifyTrack) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String, String) -> Unit
) {
    if (isSearching) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = SpotifyGreen)
        }
        return
    }

    if (results == null) return

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Tracks
        results.tracks?.items?.takeIf { it.isNotEmpty() }?.let { trackItems ->
            item {
                SectionHeader("Songs")
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(trackItems.take(5).mapNotNull { it.track }) { track ->
                TrackListItem(
                    track = track,
                    onClick = { onPlayTrack(track) }
                )
            }
        }

        // Artists
        results.artists?.items?.takeIf { it.isNotEmpty() }?.let { artists ->
            item {
                SectionHeader("Artists")
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(artists.take(10)) { artist ->
                        ArtistCard(
                            imageUrl = artist.images.firstOrNull()?.url,
                            name = artist.name,
                            onClick = { onArtistClick(artist.id) }
                        )
                    }
                }
            }
        }

        // Albums
        results.albums?.items?.takeIf { it.isNotEmpty() }?.let { albums ->
            item {
                SectionHeader("Albums")
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(albums.take(10)) { album ->
                        AlbumCard(
                            imageUrl = album.images.firstOrNull()?.url,
                            title = album.name,
                            subtitle = album.artists.joinToString(", ") { it.name },
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                }
            }
        }

        // Playlists
        results.playlists?.items?.takeIf { it.isNotEmpty() }?.let { playlists ->
            item {
                SectionHeader("Playlists")
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlists.take(10)) { playlist ->
                        PlaylistCard(
                            imageUrl = playlist.images.firstOrNull()?.url,
                            name = playlist.name,
                            onClick = { onPlaylistClick(playlist.id, playlist.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchResults(
    query: String,
    playlists: List<SpotifyPlaylist>,
    albums: List<SpotifyAlbum>,
    artists: List<SpotifyArtist>,
    tracks: List<SpotifyTrack>,
    onPlayTrack: (SpotifyTrack) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String, String) -> Unit
) {
    val queryLower = query.lowercase()

    // Filter library content locally
    val filteredPlaylists = playlists.filter {
        it.name.lowercase().contains(queryLower)
    }
    val filteredAlbums = albums.filter {
        it.name.lowercase().contains(queryLower) ||
        it.artists.any { artist -> artist.name.lowercase().contains(queryLower) }
    }
    val filteredArtists = artists.filter {
        it.name.lowercase().contains(queryLower)
    }
    val filteredTracks = tracks.filter {
        it.name.lowercase().contains(queryLower) ||
        it.artists.any { artist -> artist.name.lowercase().contains(queryLower) } ||
        it.album.name.lowercase().contains(queryLower)
    }

    val hasResults = filteredPlaylists.isNotEmpty() || filteredAlbums.isNotEmpty() ||
                     filteredArtists.isNotEmpty() || filteredTracks.isNotEmpty()

    if (!hasResults) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No results in your library",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Tracks
        if (filteredTracks.isNotEmpty()) {
            item {
                SectionHeader("Songs")
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(filteredTracks.take(10)) { track ->
                TrackListItem(
                    track = track,
                    onClick = { onPlayTrack(track) }
                )
            }
        }

        // Artists
        if (filteredArtists.isNotEmpty()) {
            item {
                SectionHeader("Artists")
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredArtists.take(10)) { artist ->
                        ArtistCard(
                            imageUrl = artist.images.firstOrNull()?.url,
                            name = artist.name,
                            onClick = { onArtistClick(artist.id) }
                        )
                    }
                }
            }
        }

        // Albums
        if (filteredAlbums.isNotEmpty()) {
            item {
                SectionHeader("Albums")
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredAlbums.take(10)) { album ->
                        AlbumCard(
                            imageUrl = album.images.firstOrNull()?.url,
                            title = album.name,
                            subtitle = album.artists.joinToString(", ") { it.name },
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                }
            }
        }

        // Playlists
        if (filteredPlaylists.isNotEmpty()) {
            item {
                SectionHeader("Playlists")
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredPlaylists.take(10)) { playlist ->
                        PlaylistCard(
                            imageUrl = playlist.images.firstOrNull()?.url,
                            name = playlist.name,
                            onClick = { onPlaylistClick(playlist.id, playlist.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    showAll: Boolean = false,
    onShowAllClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (showAll && onShowAllClick != null) {
            Text(
                text = "Show all",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onShowAllClick)
            )
        }
    }
}

@Composable
private fun AlbumCard(
    imageUrl: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HomeControlColors.cardBorder()),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistCard(
    imageUrl: String?,
    name: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(HomeControlColors.cardBorder()),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlaylistCard(
    imageUrl: String?,
    name: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HomeControlColors.cardBorder()),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LibraryGridItem(
    imageUrl: String?,
    title: String,
    subtitle: String,
    isCircular: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = if (isCircular) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(if (isCircular) CircleShape else RoundedCornerShape(8.dp))
                .background(HomeControlColors.cardBorder()),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = if (isCircular) Icons.Default.Person else Icons.Default.Album,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isCircular) TextAlign.Center else TextAlign.Start,
            modifier = if (isCircular) Modifier.fillMaxWidth() else Modifier
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isCircular) TextAlign.Center else TextAlign.Start,
            modifier = if (isCircular) Modifier.fillMaxWidth() else Modifier
        )
    }
}

@Composable
private fun LikedSongsCard(
    trackCount: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4B2FFF),
                            Color(0xFF8B6CFF),
                            Color(0xFFB8A4FF)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Liked Songs",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "$trackCount songs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrackListItem(
    track: SpotifyTrack,
    isPlaying: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art with playing indicator overlay
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HomeControlColors.cardBorder()),
            contentAlignment = Alignment.Center
        ) {
            val imageUrl = track.album.images.firstOrNull()?.url
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            // Playing indicator overlay
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Playing",
                        tint = SpotifyGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlaying) SpotifyGreen else MaterialTheme.colorScheme.onSurface,
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

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailViewContent(
    uiState: SpotifyUiState,
    onBack: () -> Unit,
    onPlayTrack: (SpotifyTrack, String?) -> Unit,
    onPlayAlbum: (String, String?) -> Unit,
    onPlayPlaylist: (String, String?) -> Unit,
    onPlayContext: (String) -> Unit,
    onShuffleContext: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String, String) -> Unit,
    onToggleAlbumSaved: () -> Unit,
    onToggleFollowArtist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(HomeControlColors.cardBackgroundSolid())
    ) {
        when (val view = uiState.detailView) {
            is DetailView.Album -> AlbumDetailView(
                album = uiState.detailAlbum,
                isSaved = uiState.isAlbumSaved,
                currentlyPlayingTrackId = uiState.playback?.item?.id,
                onBack = onBack,
                onPlayTrack = { track -> onPlayTrack(track, uiState.detailAlbum?.uri) },
                onPlayAlbum = { onPlayAlbum(view.id, null) },
                onShuffleAlbum = { uiState.detailAlbum?.uri?.let { uri -> onShuffleContext(uri) } },
                onArtistClick = onArtistClick,
                onToggleSaved = onToggleAlbumSaved
            )

            is DetailView.Artist -> ArtistDetailView(
                artist = uiState.detailArtist,
                albums = uiState.detailArtistAlbums,
                topTracks = uiState.detailArtistTopTracks,
                isFollowing = uiState.isFollowingArtist,
                currentlyPlayingTrackId = uiState.playback?.item?.id,
                onBack = onBack,
                onPlayTrack = { track -> onPlayTrack(track, null) },
                onPlayArtist = { uiState.detailArtist?.uri?.let { uri -> onPlayContext(uri) } },
                onShuffleArtist = { uiState.detailArtist?.uri?.let { uri -> onShuffleContext(uri) } },
                onAlbumClick = onAlbumClick,
                onToggleFollow = onToggleFollowArtist
            )

            is DetailView.Playlist -> PlaylistDetailView(
                name = view.name,
                tracks = uiState.playlistTracks,
                onBack = onBack,
                onPlayTrack = { track -> onPlayTrack(track, "spotify:playlist:${view.id}") },
                onPlayAll = { onPlayPlaylist(view.id, null) }
            )

            DetailView.LikedSongs -> LikedSongsDetailView(
                tracks = uiState.libraryTracks,
                onBack = onBack,
                onPlayTrack = { track -> onPlayTrack(track, null) }
            )

            is DetailView.Section -> SectionDetailView(
                title = view.title,
                sectionType = view.sectionType,
                recentlyPlayed = uiState.recentlyPlayed,
                topArtists = uiState.topArtists,
                playlists = uiState.playlists,
                topTracks = uiState.topTracks,
                onBack = onBack,
                onPlayTrack = { track -> onPlayTrack(track, null) },
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onPlaylistClick = onPlaylistClick
            )

            null -> {}
        }
    }
}

@Composable
private fun AlbumDetailView(
    album: SpotifyAlbum?,
    isSaved: Boolean,
    currentlyPlayingTrackId: String?,
    onBack: () -> Unit,
    onPlayTrack: (SpotifyTrack) -> Unit,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit,
    onArtistClick: (String) -> Unit,
    onToggleSaved: () -> Unit
) {
    if (album == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = SpotifyGreen)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Back",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Two-column layout: Album info on left, track list on right
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Left column - Album art and info
            Column(
                modifier = Modifier.width(240.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Album art
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HomeControlColors.cardBorder())
                ) {
                    val imageUrl = album.images.firstOrNull()?.url
                    if (imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Album type label
                Text(
                    text = (album.albumType?.replaceFirstChar { it.uppercase() } ?: "Album").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Album name
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Artist name (clickable)
                Text(
                    text = album.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable {
                        album.artists.firstOrNull()?.let { onArtistClick(it.id) }
                    }
                )

                // Year, songs, duration
                val totalDurationMs = album.tracks?.items?.sumOf { it.durationMs } ?: 0
                val totalMinutes = totalDurationMs / 60000
                Text(
                    text = "${album.releaseDate?.take(4) ?: ""} • ${album.totalTracks} songs • $totalMinutes min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Play and Shuffle buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Play button
                    Button(
                        onClick = onPlayAlbum,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyGreen,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play", fontWeight = FontWeight.Bold)
                    }

                    // Shuffle button
                    OutlinedButton(
                        onClick = onShuffleAlbum,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Shuffle")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save/Like button
                IconButton(
                    onClick = onToggleSaved,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isSaved) "Remove from library" else "Add to library",
                        tint = if (isSaved) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(32.dp))

            // Right column - Track list
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                album.tracks?.items?.let { tracks ->
                    itemsIndexed(tracks) { index, track ->
                        AlbumTrackItem(
                            trackNumber = index + 1,
                            track = track,
                            isPlaying = track.id == currentlyPlayingTrackId,
                            onClick = { onPlayTrack(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumTrackItem(
    trackNumber: Int,
    track: SpotifyTrack,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number or playing indicator
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Playing",
                    tint = SpotifyGreen,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = trackNumber.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Track name
        Text(
            text = track.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isPlaying) SpotifyGreen else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Duration
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArtistDetailView(
    artist: SpotifyArtist?,
    albums: List<SpotifyAlbum>,
    topTracks: List<SpotifyTrack>,
    isFollowing: Boolean,
    currentlyPlayingTrackId: String?,
    onBack: () -> Unit,
    onPlayTrack: (SpotifyTrack) -> Unit,
    onPlayArtist: () -> Unit,
    onShuffleArtist: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onToggleFollow: () -> Unit
) {
    if (artist == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = SpotifyGreen)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Back",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Two-column layout: Artist info on left (fixed), Popular + Discography on right (scrollable)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Left column - Artist image and info (does not scroll)
            Column(
                modifier = Modifier.width(200.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Artist image (circular)
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(HomeControlColors.cardBorder())
                ) {
                    val imageUrl = artist.images.firstOrNull()?.url
                    if (imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Artist label
                Text(
                    text = "ARTIST",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Artist name
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Genres
                if (artist.genres.isNotEmpty()) {
                    Text(
                        text = artist.genres.take(3).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Followers
                artist.followers?.let {
                    Text(
                        text = "${formatNumber(it.total)} followers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Play and Shuffle buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Play button
                    Button(
                        onClick = onPlayArtist,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyGreen,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play", fontWeight = FontWeight.Bold)
                    }

                    // Shuffle button
                    OutlinedButton(
                        onClick = onShuffleArtist,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Shuffle")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Follow button
                Button(
                    onClick = onToggleFollow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowing) Color.Transparent else SpotifyGreen,
                        contentColor = if (isFollowing) MaterialTheme.colorScheme.onSurface else Color.Black
                    ),
                    border = if (isFollowing) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(if (isFollowing) "Following" else "Follow")
                }
            }

            Spacer(modifier = Modifier.width(32.dp))

            // Right column - Popular tracks and Discography (scrollable)
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                // Popular tracks section
                if (topTracks.isNotEmpty()) {
                    item {
                        SectionHeader("Popular")
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    itemsIndexed(topTracks.take(5)) { index, track ->
                        ArtistTrackItem(
                            trackNumber = index + 1,
                            track = track,
                            isPlaying = track.id == currentlyPlayingTrackId,
                            onClick = { onPlayTrack(track) }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }

                // Discography section
                if (albums.isNotEmpty()) {
                    item {
                        SectionHeader("Discography")
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Display albums in rows of 5
                    val chunkedAlbums = albums.chunked(5)
                    items(chunkedAlbums) { rowAlbums ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowAlbums.forEach { album ->
                                AlbumCard(
                                    imageUrl = album.images.firstOrNull()?.url,
                                    title = album.name,
                                    subtitle = album.releaseDate?.take(4) ?: "",
                                    onClick = { onAlbumClick(album.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Fill remaining space if row isn't complete
                            repeat(5 - rowAlbums.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistTrackItem(
    trackNumber: Int,
    track: SpotifyTrack,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number or playing indicator
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Playing",
                    tint = SpotifyGreen,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = trackNumber.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Album art
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HomeControlColors.cardBorder()),
            contentAlignment = Alignment.Center
        ) {
            val imageUrl = track.album.images.firstOrNull()?.url
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Track name
        Text(
            text = track.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isPlaying) SpotifyGreen else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Duration
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlaylistDetailView(
    name: String,
    tracks: List<SpotifyTrack>,
    onBack: () -> Unit,
    onPlayTrack: (SpotifyTrack) -> Unit,
    onPlayAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onPlayAll,
                modifier = Modifier
                    .size(48.dp)
                    .background(SpotifyGreen, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play all",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SpotifyGreen)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(tracks) { track ->
                    TrackListItem(
                        track = track,
                        onClick = { onPlayTrack(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LikedSongsDetailView(
    tracks: List<SpotifyTrack>,
    onBack: () -> Unit,
    onPlayTrack: (SpotifyTrack) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Liked Songs",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SpotifyGreen)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(tracks) { track ->
                    TrackListItem(
                        track = track,
                        onClick = { onPlayTrack(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionDetailView(
    title: String,
    sectionType: SectionType,
    recentlyPlayed: List<SpotifyPlayHistoryItem>,
    topArtists: List<SpotifyArtist>,
    playlists: List<SpotifyPlaylist>,
    topTracks: List<SpotifyTrack>,
    onBack: () -> Unit,
    onPlayTrack: (SpotifyTrack) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Grid content based on section type
        when (sectionType) {
            SectionType.RECENTLY_PLAYED -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(recentlyPlayed) { item ->
                        AlbumCard(
                            imageUrl = item.track.album.images.firstOrNull()?.url,
                            title = item.track.name,
                            subtitle = item.track.artists.joinToString(", ") { it.name },
                            onClick = { onAlbumClick(item.track.album.id) }
                        )
                    }
                }
            }

            SectionType.TOP_ARTISTS -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(topArtists) { artist ->
                        ArtistCard(
                            imageUrl = artist.images.firstOrNull()?.url,
                            name = artist.name,
                            onClick = { onArtistClick(artist.id) }
                        )
                    }
                }
            }

            SectionType.YOUR_PLAYLISTS -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(playlists) { playlist ->
                        PlaylistCard(
                            imageUrl = playlist.images.firstOrNull()?.url,
                            name = playlist.name,
                            onClick = { onPlaylistClick(playlist.id, playlist.name) }
                        )
                    }
                }
            }

            SectionType.JUMP_BACK_IN -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(topTracks) { track ->
                        AlbumCard(
                            imageUrl = track.album.images.firstOrNull()?.url,
                            title = track.name,
                            subtitle = track.artists.joinToString(", ") { it.name },
                            onClick = { onPlayTrack(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicePickerDialog(
    devices: List<SpotifyDevice>,
    onDeviceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Connect to a device",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (devices.isEmpty()) {
                Text("No devices available")
            } else {
                LazyColumn {
                    items(devices) { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDeviceSelected(device.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = getDeviceIcon(device.type),
                                contentDescription = null,
                                tint = if (device.isActive) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (device.isActive) SpotifyGreen else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = device.type,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (device.isActive) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Active",
                                    tint = SpotifyGreen
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = HomeControlColors.cardBackgroundSolid()
    )
}

// Helper functions
private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatNumber(number: Int): String {
    return when {
        number >= 1_000_000 -> "%.1fM".format(number / 1_000_000f)
        number >= 1_000 -> "%.1fK".format(number / 1_000f)
        else -> number.toString()
    }
}

@Composable
private fun getDeviceIcon(type: String?) = when (type?.lowercase()) {
    "computer" -> Icons.Default.Computer
    "smartphone" -> Icons.Default.Smartphone
    "speaker" -> Icons.Default.Speaker
    "tv" -> Icons.Default.Tv
    "cast_audio", "cast_video" -> Icons.Default.Cast
    else -> Icons.Default.Devices
}
