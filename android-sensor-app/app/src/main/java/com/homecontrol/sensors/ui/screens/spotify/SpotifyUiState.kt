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

    // Dialogs
    val showDevicePicker: Boolean = false
)

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
    data class Section(val title: String, val sectionType: SectionType) : DetailView()
}

enum class SectionType {
    RECENTLY_PLAYED,
    TOP_ARTISTS,
    YOUR_PLAYLISTS,
    JUMP_BACK_IN
}
