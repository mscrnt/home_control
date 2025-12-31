package com.homecontrol.sensors.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyStatus(
    val authenticated: Boolean,
    @SerialName("auth_url")
    val authUrl: String? = null
)

@Serializable
data class SpotifyPlayback(
    @SerialName("is_playing")
    val isPlaying: Boolean = false,
    @SerialName("shuffle_state")
    val shuffleState: Boolean = false,
    @SerialName("repeat_state")
    val repeatState: String = "off",
    @SerialName("progress_ms")
    val progressMs: Long = 0,
    val item: SpotifyTrack? = null,
    val device: SpotifyDevice? = null,
    val context: SpotifyContext? = null
)

@Serializable
data class SpotifyTrack(
    val id: String,
    val name: String,
    @SerialName("duration_ms")
    val durationMs: Long,
    val artists: List<SpotifyArtistSimple>,
    val album: SpotifyAlbumSimple,
    val uri: String,
    @SerialName("is_playable")
    val isPlayable: Boolean = true,
    @SerialName("track_number")
    val trackNumber: Int = 1,
    @SerialName("disc_number")
    val discNumber: Int = 1
)

@Serializable
data class SpotifyArtistSimple(
    val id: String,
    val name: String,
    val uri: String? = null
)

@Serializable
data class SpotifyAlbumSimple(
    val id: String,
    val name: String,
    val images: List<SpotifyImage> = emptyList(),
    val uri: String? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
    @SerialName("total_tracks")
    val totalTracks: Int = 0
)

@Serializable
data class SpotifyImage(
    val url: String,
    val height: Int? = null,
    val width: Int? = null
)

@Serializable
data class SpotifyDevice(
    val id: String,
    val name: String,
    val type: String,
    @SerialName("is_active")
    val isActive: Boolean = false,
    @SerialName("volume_percent")
    val volumePercent: Int = 0,
    @SerialName("is_restricted")
    val isRestricted: Boolean = false
)

@Serializable
data class SpotifyContext(
    val type: String,
    val uri: String,
    val href: String? = null
)

@Serializable
data class SpotifyPlaylistsResponse(
    val items: List<SpotifyPlaylist> = emptyList(),
    val total: Int = 0,
    val limit: Int = 50,
    val offset: Int = 0
)

@Serializable
data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    val images: List<SpotifyImage> = emptyList(),
    val owner: SpotifyUser? = null,
    val uri: String,
    val tracks: SpotifyPlaylistTracks? = null,
    val public: Boolean? = null
)

@Serializable
data class SpotifyUser(
    val id: String,
    @SerialName("display_name")
    val displayName: String? = null
)

@Serializable
data class SpotifyPlaylistTracks(
    val total: Int
)

@Serializable
data class SpotifyTracksResponse(
    val items: List<SpotifyTrackItem> = emptyList(),
    val total: Int = 0,
    val limit: Int = 50,
    val offset: Int = 0
)

@Serializable
data class SpotifyTrackItem(
    val track: SpotifyTrack? = null,
    @SerialName("added_at")
    val addedAt: String? = null
)

// Response for top tracks endpoint (items are tracks directly, not wrapped)
@Serializable
data class SpotifyTopTracksResponse(
    val items: List<SpotifyTrack> = emptyList()
)

@Serializable
data class SpotifySearchResponse(
    val tracks: SpotifyTracksResponse? = null,
    val artists: SpotifyArtistsResponse? = null,
    val albums: SpotifyAlbumsResponse? = null,
    val playlists: SpotifyPlaylistsResponse? = null
)

@Serializable
data class SpotifyRecentResponse(
    val items: List<SpotifyPlayHistoryItem> = emptyList()
)

@Serializable
data class SpotifyPlayHistoryItem(
    val track: SpotifyTrack,
    @SerialName("played_at")
    val playedAt: String
)

@Serializable
data class SpotifyArtistsResponse(
    val items: List<SpotifyArtist> = emptyList(),
    val total: Int = 0
)

@Serializable
data class SpotifyArtist(
    val id: String,
    val name: String,
    val images: List<SpotifyImage> = emptyList(),
    val uri: String,
    val genres: List<String> = emptyList(),
    val followers: SpotifyFollowers? = null,
    val popularity: Int = 0
)

@Serializable
data class SpotifyFollowers(
    val total: Int = 0
)

@Serializable
data class SpotifyArtistTopTracksResponse(
    val tracks: List<SpotifyTrack> = emptyList()
)

@Serializable
data class SpotifyAlbumsResponse(
    val items: List<SpotifyAlbum> = emptyList(),
    val total: Int = 0
)

@Serializable
data class SpotifyAlbum(
    val id: String,
    val name: String,
    val images: List<SpotifyImage> = emptyList(),
    val artists: List<SpotifyArtistSimple> = emptyList(),
    val uri: String,
    @SerialName("release_date")
    val releaseDate: String? = null,
    @SerialName("total_tracks")
    val totalTracks: Int = 0,
    @SerialName("album_type")
    val albumType: String? = null,
    val tracks: SpotifyAlbumTracks? = null
)

@Serializable
data class SpotifyAlbumTracks(
    val items: List<SpotifyTrack> = emptyList(),
    val total: Int = 0
)

@Serializable
data class SavedResponse(
    val saved: Boolean
)

@Serializable
data class FollowingResponse(
    val following: Boolean
)

// Request models
@Serializable
data class SpotifyPlayRequest(
    @SerialName("device_id")
    val deviceId: String? = null,
    val uri: String? = null,
    val position: Int? = null
)

@Serializable
data class VolumeRequest(
    val volume: Int
)

@Serializable
data class SeekRequest(
    @SerialName("position_ms")
    val positionMs: Long
)

@Serializable
data class ShuffleRequest(
    val state: Boolean
)

@Serializable
data class RepeatRequest(
    val state: String  // "off", "track", "context"
)

@Serializable
data class TransferRequest(
    @SerialName("device_id")
    val deviceId: String
)

// Queue models
@Serializable
data class SpotifyQueue(
    @SerialName("currently_playing")
    val currentlyPlaying: SpotifyTrack? = null,
    val queue: List<SpotifyTrack> = emptyList()
)

@Serializable
data class AddToQueueRequest(
    val uri: String,
    @SerialName("device_id")
    val deviceId: String? = null
)

// Browse models
@Serializable
data class SpotifyNewReleasesResponse(
    val items: List<SpotifyAlbum> = emptyList(),
    val total: Int = 0
)

@Serializable
data class SpotifyFeaturedPlaylistsResponse(
    val items: List<SpotifyPlaylist> = emptyList(),
    val message: String? = null
)

@Serializable
data class SpotifyCategory(
    val id: String,
    val name: String,
    val icons: List<SpotifyImage> = emptyList()
)

@Serializable
data class SpotifyCategoriesResponse(
    val items: List<SpotifyCategory> = emptyList(),
    val total: Int = 0
)

// Recommendations
@Serializable
data class SpotifyRecommendationsResponse(
    val tracks: List<SpotifyTrack> = emptyList()
)
