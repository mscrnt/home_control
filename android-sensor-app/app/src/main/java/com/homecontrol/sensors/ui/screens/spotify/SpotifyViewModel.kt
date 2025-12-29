package com.homecontrol.sensors.ui.screens.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.sensors.data.model.*
import com.homecontrol.sensors.data.repository.SpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SpotifyViewModel @Inject constructor(
    private val repository: SpotifyRepository,
    @com.homecontrol.sensors.di.ServerUrl private val serverUrl: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpotifyUiState(serverUrl = serverUrl))
    val uiState: StateFlow<SpotifyUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var pollingJob: Job? = null

    init {
        loadInitialData()
        startPlaybackPolling()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Load playback state
            loadPlayback()

            // Load devices
            loadDevices()

            // Load home content
            loadHomeContent()

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun loadPlayback() {
        repository.getPlayback()
            .onSuccess { playback ->
                _uiState.update { it.copy(playback = playback) }
            }
            .onFailure { error ->
                android.util.Log.e("SpotifyViewModel", "Failed to load playback: ${error.message}")
            }
    }

    private suspend fun loadDevices() {
        repository.getDevices()
            .onSuccess { devices ->
                _uiState.update { it.copy(devices = devices) }
            }
    }

    private suspend fun loadHomeContent() {
        // Load all home content in parallel
        viewModelScope.launch {
            repository.getRecentlyPlayed()
                .onSuccess { response ->
                    _uiState.update { it.copy(recentlyPlayed = response.items) }
                }
        }

        viewModelScope.launch {
            repository.getTopArtists()
                .onSuccess { response ->
                    _uiState.update { it.copy(topArtists = response.items) }
                }
        }

        viewModelScope.launch {
            repository.getTopTracks()
                .onSuccess { response ->
                    _uiState.update { it.copy(topTracks = response.items) }
                }
        }

        viewModelScope.launch {
            repository.getPlaylists()
                .onSuccess { response ->
                    _uiState.update { it.copy(playlists = response.items) }
                }
        }
    }

    fun loadLibraryContent() {
        viewModelScope.launch {
            repository.getLibraryAlbums()
                .onSuccess { response ->
                    _uiState.update { it.copy(libraryAlbums = response.items) }
                }
        }

        viewModelScope.launch {
            repository.getLibraryArtists()
                .onSuccess { response ->
                    _uiState.update { it.copy(libraryArtists = response.items) }
                }
        }

        viewModelScope.launch {
            repository.getLibraryTracks()
                .onSuccess { response ->
                    val tracks = response.items.mapNotNull { it.track }
                    _uiState.update { it.copy(libraryTracks = tracks) }
                }
        }
    }

    private fun startPlaybackPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(1_000) // Poll every second for smooth progress updates
                loadPlayback()
            }
        }
    }

    // Tab navigation
    fun setActiveTab(tab: SpotifyTab) {
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == SpotifyTab.LIBRARY && _uiState.value.libraryAlbums.isEmpty()) {
            loadLibraryContent()
        }
    }

    fun setLibraryFilter(filter: LibraryFilter) {
        _uiState.update { it.copy(libraryFilter = filter) }
        if (filter == LibraryFilter.LIKED_SONGS && _uiState.value.libraryTracks.isEmpty()) {
            viewModelScope.launch {
                repository.getLibraryTracks()
                    .onSuccess { response ->
                        val tracks = response.items.mapNotNull { it.track }
                        _uiState.update { it.copy(libraryTracks = tracks) }
                    }
            }
        }
    }

    // Search
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = null, isSearching = false) }
            return
        }

        // Only call global search API when NOT in Library tab
        // Library tab filtering is done client-side in the UI
        if (_uiState.value.activeTab != SpotifyTab.LIBRARY) {
            searchJob = viewModelScope.launch {
                delay(300) // Debounce
                _uiState.update { it.copy(isSearching = true) }

                repository.search(query)
                    .onSuccess { results ->
                        _uiState.update { it.copy(searchResults = results, isSearching = false) }
                    }
                    .onFailure {
                        _uiState.update { it.copy(isSearching = false) }
                    }
            }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = null) }
    }

    // Detail views
    fun openAlbumDetail(albumId: String) {
        _uiState.update { it.copy(detailView = DetailView.Album(albumId), detailAlbum = null) }

        viewModelScope.launch {
            repository.getAlbum(albumId)
                .onSuccess { album ->
                    _uiState.update { it.copy(detailAlbum = album) }
                }

            repository.isAlbumSaved(albumId)
                .onSuccess { saved ->
                    _uiState.update { it.copy(isAlbumSaved = saved) }
                }
        }
    }

    fun openArtistDetail(artistId: String) {
        _uiState.update {
            it.copy(
                detailView = DetailView.Artist(artistId),
                detailArtist = null,
                detailArtistAlbums = emptyList(),
                detailArtistTopTracks = emptyList()
            )
        }

        viewModelScope.launch {
            repository.getArtist(artistId)
                .onSuccess { artist ->
                    _uiState.update { it.copy(detailArtist = artist) }
                }

            repository.isFollowingArtist(artistId)
                .onSuccess { following ->
                    _uiState.update { it.copy(isFollowingArtist = following) }
                }
        }

        viewModelScope.launch {
            repository.getArtistAlbums(artistId)
                .onSuccess { response ->
                    _uiState.update { it.copy(detailArtistAlbums = response.items) }
                }
        }

        viewModelScope.launch {
            repository.getArtistTopTracks(artistId)
                .onSuccess { response ->
                    _uiState.update { it.copy(detailArtistTopTracks = response.tracks) }
                }
        }
    }

    fun openPlaylistDetail(playlistId: String, name: String) {
        _uiState.update {
            it.copy(
                detailView = DetailView.Playlist(playlistId, name),
                playlistTracks = emptyList()
            )
        }

        viewModelScope.launch {
            repository.getPlaylistTracks(playlistId)
                .onSuccess { response ->
                    val tracks = response.items.mapNotNull { it.track }
                    _uiState.update { it.copy(playlistTracks = tracks) }
                }
        }
    }

    fun openLikedSongs() {
        _uiState.update { it.copy(detailView = DetailView.LikedSongs) }

        if (_uiState.value.libraryTracks.isEmpty()) {
            viewModelScope.launch {
                repository.getLibraryTracks()
                    .onSuccess { response ->
                        val tracks = response.items.mapNotNull { it.track }
                        _uiState.update { it.copy(libraryTracks = tracks) }
                    }
            }
        }
    }

    fun openSectionView(sectionType: SectionType) {
        val title = when (sectionType) {
            SectionType.RECENTLY_PLAYED -> "Recently Played"
            SectionType.TOP_ARTISTS -> "Top Artists"
            SectionType.YOUR_PLAYLISTS -> "Your Playlists"
            SectionType.JUMP_BACK_IN -> "Jump Back In"
        }
        _uiState.update { it.copy(detailView = DetailView.Section(title, sectionType)) }
    }

    fun closeDetailView() {
        _uiState.update { it.copy(detailView = null) }
    }

    // Album actions
    fun toggleAlbumSaved() {
        val albumId = (_uiState.value.detailView as? DetailView.Album)?.id ?: return
        val isSaved = _uiState.value.isAlbumSaved

        viewModelScope.launch {
            if (isSaved) {
                repository.removeAlbum(albumId)
                    .onSuccess {
                        _uiState.update { it.copy(isAlbumSaved = false) }
                        loadLibraryContent() // Refresh library
                    }
            } else {
                repository.saveAlbum(albumId)
                    .onSuccess {
                        _uiState.update { it.copy(isAlbumSaved = true) }
                        loadLibraryContent()
                    }
            }
        }
    }

    // Artist actions
    fun toggleFollowArtist() {
        val artistId = (_uiState.value.detailView as? DetailView.Artist)?.id ?: return
        val isFollowing = _uiState.value.isFollowingArtist

        viewModelScope.launch {
            if (isFollowing) {
                repository.unfollowArtist(artistId)
                    .onSuccess {
                        _uiState.update { it.copy(isFollowingArtist = false) }
                        loadLibraryContent()
                    }
            } else {
                repository.followArtist(artistId)
                    .onSuccess {
                        _uiState.update { it.copy(isFollowingArtist = true) }
                        loadLibraryContent()
                    }
            }
        }
    }

    // Playback controls
    fun togglePlayPause() {
        viewModelScope.launch {
            val isPlaying = _uiState.value.playback?.isPlaying ?: false
            if (isPlaying) {
                repository.pause()
            } else {
                repository.play()
            }
            delay(100)
            loadPlayback()
        }
    }

    fun next() {
        viewModelScope.launch {
            repository.next()
            delay(100)
            loadPlayback()
        }
    }

    fun previous() {
        viewModelScope.launch {
            repository.previous()
            delay(100)
            loadPlayback()
        }
    }

    fun seek(positionMs: Long) {
        viewModelScope.launch {
            repository.seek(positionMs)
        }
    }

    fun setVolume(volume: Int) {
        viewModelScope.launch {
            repository.setVolume(volume)
        }
    }

    fun toggleShuffle() {
        viewModelScope.launch {
            val currentState = _uiState.value.playback?.shuffleState ?: false
            repository.setShuffle(!currentState)
            delay(100)
            loadPlayback()
        }
    }

    fun cycleRepeat() {
        viewModelScope.launch {
            val currentState = _uiState.value.playback?.repeatState ?: "off"
            val nextState = when (currentState) {
                "off" -> "context"
                "context" -> "track"
                else -> "off"
            }
            repository.setRepeat(nextState)
            delay(100)
            loadPlayback()
        }
    }

    // Play content
    fun playTrack(track: SpotifyTrack, contextUri: String? = null) {
        viewModelScope.launch {
            // Use track URI directly, or context URI if provided
            val uri = contextUri ?: track.uri
            repository.play(SpotifyPlayRequest(uri = uri))
            delay(100)
            loadPlayback()
        }
    }

    fun playContext(contextUri: String) {
        viewModelScope.launch {
            repository.play(SpotifyPlayRequest(uri = contextUri))
            delay(100)
            loadPlayback()
        }
    }

    fun shuffleContext(contextUri: String) {
        viewModelScope.launch {
            // Enable shuffle first, then play the context
            repository.setShuffle(true)
            delay(50)
            repository.play(SpotifyPlayRequest(uri = contextUri))
            delay(100)
            loadPlayback()
        }
    }

    fun playAlbum(albumId: String, trackUri: String? = null) {
        viewModelScope.launch {
            // Use track URI if provided, otherwise album URI
            val uri = trackUri ?: "spotify:album:$albumId"
            repository.play(SpotifyPlayRequest(uri = uri))
            delay(100)
            loadPlayback()
        }
    }

    fun playPlaylist(playlistId: String, trackUri: String? = null) {
        viewModelScope.launch {
            // Use track URI if provided, otherwise playlist URI
            val uri = trackUri ?: "spotify:playlist:$playlistId"
            repository.play(SpotifyPlayRequest(uri = uri))
            delay(100)
            loadPlayback()
        }
    }

    // Device picker
    fun showDevicePicker() {
        viewModelScope.launch {
            loadDevices()
            _uiState.update { it.copy(showDevicePicker = true) }
        }
    }

    fun hideDevicePicker() {
        _uiState.update { it.copy(showDevicePicker = false) }
    }

    fun transferToDevice(deviceId: String) {
        viewModelScope.launch {
            repository.transfer(deviceId)
            delay(200)
            loadPlayback()
            loadDevices()
            hideDevicePicker()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        searchJob?.cancel()
    }
}
