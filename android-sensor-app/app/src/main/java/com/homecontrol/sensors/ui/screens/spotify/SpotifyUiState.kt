package com.homecontrol.sensors.ui.screens.spotify

import com.homecontrol.sensors.data.model.*

data class SpotifyUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val serverUrl: String = "",

    // Playback state
    val playback: SpotifyPlayback? = null,
    val devices: List<SpotifyDevice> = emptyList(),

    // Browse content
    val activeTab: SpotifyTab = SpotifyTab.HOME,
    val recentlyPlayed: List<SpotifyPlayHistoryItem> = emptyList(),
    val topArtists: List<SpotifyArtist> = emptyList(),
    val topTracks: List<SpotifyTrack> = emptyList(),
    val playlists: List<SpotifyPlaylist> = emptyList(),

    // New features
    val queue: List<SpotifyTrack> = emptyList(),
    val newReleases: List<SpotifyAlbum> = emptyList(),

    // Library content
    val libraryAlbums: List<SpotifyAlbum> = emptyList(),
    val libraryArtists: List<SpotifyArtist> = emptyList(),
    val libraryTracks: List<SpotifyTrack> = emptyList(),
    val libraryFilter: LibraryFilter = LibraryFilter.ALL,

    // Search
    val searchQuery: String = "",
    val searchResults: SpotifySearchResponse? = null,
    val isSearching: Boolean = false,

    // Detail views
    val detailView: DetailView? = null,
    val detailAlbum: SpotifyAlbum? = null,
    val detailArtist: SpotifyArtist? = null,
    val detailArtistAlbums: List<SpotifyAlbum> = emptyList(),
    val detailArtistTopTracks: List<SpotifyTrack> = emptyList(),
    val isAlbumSaved: Boolean = false,
    val isFollowingArtist: Boolean = false,
    val playlistTracks: List<SpotifyTrack> = emptyList(),

    // Saved tracks state (track ID -> saved status)
    val savedTracks: Map<String, Boolean> = emptyMap(),

    // Dialogs
    val showDevicePicker: Boolean = false
) {
    /**
     * Get unique albums from recently played tracks.
     * Preserves order (most recent first) and removes duplicates.
     */
    val recentlyPlayedAlbums: List<SpotifyAlbumSimple>
        get() = recentlyPlayed
            .map { it.track.album }
            .distinctBy { it.id }
}

enum class SpotifyTab {
    HOME,
    LIBRARY
}

enum class LibraryFilter {
    ALL,
    PLAYLISTS,
    ALBUMS,
    ARTISTS,
    LIKED_SONGS
}

sealed class DetailView {
    data class Album(val id: String) : DetailView()
    data class Artist(val id: String) : DetailView()
    data class Playlist(val id: String, val name: String) : DetailView()
    object LikedSongs : DetailView()
    object Queue : DetailView()
    data class Section(val title: String, val sectionType: SectionType) : DetailView()
}

enum class SectionType {
    RECENTLY_PLAYED,
    TOP_ARTISTS,
    YOUR_PLAYLISTS,
    JUMP_BACK_IN,
    NEW_RELEASES
}
