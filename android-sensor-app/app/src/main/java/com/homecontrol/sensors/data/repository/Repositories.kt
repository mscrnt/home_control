package com.homecontrol.sensors.data.repository

import android.util.Log
import com.homecontrol.sensors.data.api.HomeControlApi
import com.homecontrol.sensors.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

// ============ Entity Repository ============

interface EntityRepository {
    suspend fun getEntities(): Result<List<EntityGroup>>
    suspend fun toggleEntity(entityId: String): Result<Unit>
    suspend fun setClimateTemperature(entityId: String, request: TemperatureRequest): Result<Unit>
    suspend fun setClimateMode(entityId: String, mode: String): Result<Unit>
    suspend fun setClimateFanMode(entityId: String, fanMode: String): Result<Unit>
}

class EntityRepositoryImpl @Inject constructor(
    private val api: HomeControlApi
) : EntityRepository {

    override suspend fun getEntities(): Result<List<EntityGroup>> = runCatching {
        val response = api.getEntities()
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun toggleEntity(entityId: String): Result<Unit> = runCatching {
        val response = api.toggleEntity(entityId)
        if (!response.isSuccessful) {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun setClimateTemperature(entityId: String, request: TemperatureRequest): Result<Unit> = runCatching {
        val response = api.setClimateTemperature(entityId, request)
        if (!response.isSuccessful) {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun setClimateMode(entityId: String, mode: String): Result<Unit> = runCatching {
        val response = api.setClimateMode(entityId, ModeRequest(mode))
        if (!response.isSuccessful) {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun setClimateFanMode(entityId: String, fanMode: String): Result<Unit> = runCatching {
        val response = api.setClimateFanMode(entityId, FanModeRequest(fanMode))
        if (!response.isSuccessful) {
            throw Exception("API error: ${response.code()}")
        }
    }
}

// ============ Hue Repository ============

interface HueRepository {
    suspend fun getRooms(): Result<List<HueRoom>>
    suspend fun toggleLight(id: String): Result<Unit>
    suspend fun setLightBrightness(id: String, brightness: Int): Result<Unit>
    suspend fun toggleGroup(id: String): Result<Unit>
    suspend fun setGroupBrightness(id: String, brightness: Int): Result<Unit>
    suspend fun activateScene(id: String): Result<Unit>
    suspend fun getSyncBoxes(): Result<List<SyncBox>>
    suspend fun getSyncBoxStatus(index: Int): Result<SyncBoxStatus>
    suspend fun setSyncBoxSync(index: Int, sync: Boolean): Result<Unit>
    suspend fun setSyncBoxMode(index: Int, mode: String): Result<Unit>
    suspend fun setSyncBoxBrightness(index: Int, brightness: Int): Result<Unit>
    suspend fun setSyncBoxInput(index: Int, input: String): Result<Unit>
}

class HueRepositoryImpl @Inject constructor(
    private val api: HomeControlApi
) : HueRepository {

    override suspend fun getRooms(): Result<List<HueRoom>> = runCatching {
        val response = api.getHueRooms()
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun toggleLight(id: String): Result<Unit> = runCatching {
        val response = api.toggleHueLight(id)
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun setLightBrightness(id: String, brightness: Int): Result<Unit> = runCatching {
        val response = api.setHueLightBrightness(id, BrightnessRequest(brightness))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun toggleGroup(id: String): Result<Unit> = runCatching {
        val response = api.toggleHueGroup(id)
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun setGroupBrightness(id: String, brightness: Int): Result<Unit> = runCatching {
        val response = api.setHueGroupBrightness(id, BrightnessRequest(brightness))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun activateScene(id: String): Result<Unit> = runCatching {
        val response = api.activateHueScene(id)
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun getSyncBoxes(): Result<List<SyncBox>> = runCatching {
        val response = api.getSyncBoxes()
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getSyncBoxStatus(index: Int): Result<SyncBoxStatus> = runCatching {
        val response = api.getSyncBoxStatus(index)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun setSyncBoxSync(index: Int, sync: Boolean): Result<Unit> = runCatching {
        val response = api.setSyncBoxSync(index, SyncRequest(sync))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun setSyncBoxMode(index: Int, mode: String): Result<Unit> = runCatching {
        val response = api.setSyncBoxMode(index, ModeRequest(mode))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun setSyncBoxBrightness(index: Int, brightness: Int): Result<Unit> = runCatching {
        val response = api.setSyncBoxBrightness(index, BrightnessRequest(brightness))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun setSyncBoxInput(index: Int, input: String): Result<Unit> = runCatching {
        val response = api.setSyncBoxInput(index, InputRequest(input))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }
}

// ============ Spotify Repository ============

interface SpotifyRepository {
    suspend fun getPlayback(): Result<SpotifyPlayback>
    suspend fun getDevices(): Result<List<SpotifyDevice>>
    suspend fun play(request: SpotifyPlayRequest? = null): Result<Unit>
    suspend fun pause(): Result<Unit>
    suspend fun next(): Result<Unit>
    suspend fun previous(): Result<Unit>
    suspend fun seek(positionMs: Long): Result<Unit>
    suspend fun setVolume(volume: Int): Result<Unit>
    suspend fun setShuffle(state: Boolean): Result<Unit>
    suspend fun setRepeat(state: String): Result<Unit>
    suspend fun transfer(deviceId: String): Result<Unit>
    suspend fun getPlaylists(): Result<SpotifyPlaylistsResponse>
    suspend fun getPlaylistTracks(id: String): Result<SpotifyTracksResponse>
    suspend fun search(query: String): Result<SpotifySearchResponse>
    suspend fun getRecentlyPlayed(): Result<SpotifyRecentResponse>
    suspend fun getTopArtists(): Result<SpotifyArtistsResponse>
    suspend fun getTopTracks(): Result<SpotifyTopTracksResponse>
    suspend fun getAlbum(id: String): Result<SpotifyAlbum>
    suspend fun isAlbumSaved(id: String): Result<Boolean>
    suspend fun saveAlbum(id: String): Result<Unit>
    suspend fun removeAlbum(id: String): Result<Unit>
    suspend fun getArtist(id: String): Result<SpotifyArtist>
    suspend fun getArtistAlbums(id: String): Result<SpotifyAlbumsResponse>
    suspend fun getArtistTopTracks(id: String): Result<SpotifyArtistTopTracksResponse>
    suspend fun isFollowingArtist(id: String): Result<Boolean>
    suspend fun followArtist(id: String): Result<Unit>
    suspend fun unfollowArtist(id: String): Result<Unit>
    suspend fun getLibraryAlbums(): Result<SpotifyAlbumsResponse>
    suspend fun getLibraryArtists(): Result<SpotifyArtistsResponse>
    suspend fun getLibraryTracks(): Result<SpotifyTracksResponse>
    // New features
    suspend fun getQueue(): Result<SpotifyQueue>
    suspend fun addToQueue(uri: String, deviceId: String? = null): Result<Unit>
    suspend fun getNewReleases(): Result<SpotifyNewReleasesResponse>
    suspend fun getFeaturedPlaylists(): Result<SpotifyFeaturedPlaylistsResponse>
    suspend fun getCategories(): Result<SpotifyCategoriesResponse>
    suspend fun getCategoryPlaylists(categoryId: String): Result<SpotifyPlaylistsResponse>
    suspend fun getRecommendations(seedArtists: List<String>? = null, seedGenres: List<String>? = null, seedTracks: List<String>? = null): Result<SpotifyRecommendationsResponse>
    suspend fun saveTrack(id: String): Result<Unit>
    suspend fun removeTrack(id: String): Result<Unit>
    suspend fun isTrackSaved(id: String): Result<Boolean>
    suspend fun areTracksSaved(ids: List<String>): Result<Map<String, Boolean>>
}

class SpotifyRepositoryImpl @Inject constructor(
    private val api: HomeControlApi
) : SpotifyRepository {

    override suspend fun getPlayback(): Result<SpotifyPlayback> {
        return try {
            val response = api.getSpotifyPlayback()
            if (response.isSuccessful) {
                Result.success(response.body() ?: SpotifyPlayback())
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: kotlinx.serialization.SerializationException) {
            // API returns literal "null" when nothing is playing
            Result.success(SpotifyPlayback())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDevices(): Result<List<SpotifyDevice>> = runCatching {
        val response = api.getSpotifyDevices()
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun play(request: SpotifyPlayRequest?): Result<Unit> = runCatching {
        // Always send a request object (empty for resume, with uri for specific track)
        val response = api.spotifyPlay(request ?: SpotifyPlayRequest())
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun pause(): Result<Unit> = runCatching {
        val response = api.spotifyPause()
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun next(): Result<Unit> = runCatching {
        val response = api.spotifyNext()
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun previous(): Result<Unit> = runCatching {
        val response = api.spotifyPrevious()
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun seek(positionMs: Long): Result<Unit> = runCatching {
        val response = api.spotifySeek(SeekRequest(positionMs))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun setVolume(volume: Int): Result<Unit> = runCatching {
        val response = api.spotifyVolume(VolumeRequest(volume))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun setShuffle(state: Boolean): Result<Unit> = runCatching {
        val response = api.spotifyShuffle(ShuffleRequest(state))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun setRepeat(state: String): Result<Unit> = runCatching {
        val response = api.spotifyRepeat(RepeatRequest(state))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun transfer(deviceId: String): Result<Unit> = runCatching {
        val response = api.spotifyTransfer(TransferRequest(deviceId))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun getPlaylists(): Result<SpotifyPlaylistsResponse> = runCatching {
        val response = api.getSpotifyPlaylists()
        if (response.isSuccessful) {
            response.body() ?: SpotifyPlaylistsResponse(emptyList())
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getPlaylistTracks(id: String): Result<SpotifyTracksResponse> = runCatching {
        val response = api.getSpotifyPlaylistTracks(id)
        if (response.isSuccessful) {
            response.body() ?: SpotifyTracksResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun search(query: String): Result<SpotifySearchResponse> = runCatching {
        val response = api.spotifySearch(query)
        if (response.isSuccessful) {
            response.body() ?: SpotifySearchResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getRecentlyPlayed(): Result<SpotifyRecentResponse> = runCatching {
        val response = api.getSpotifyRecentlyPlayed()
        if (response.isSuccessful) {
            response.body() ?: SpotifyRecentResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getTopArtists(): Result<SpotifyArtistsResponse> = runCatching {
        val response = api.getSpotifyTopArtists()
        if (response.isSuccessful) {
            response.body() ?: SpotifyArtistsResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getTopTracks(): Result<SpotifyTopTracksResponse> = runCatching {
        val response = api.getSpotifyTopTracks()
        if (response.isSuccessful) {
            response.body() ?: SpotifyTopTracksResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getAlbum(id: String): Result<SpotifyAlbum> = runCatching {
        val response = api.getSpotifyAlbum(id)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun isAlbumSaved(id: String): Result<Boolean> = runCatching {
        val response = api.isSpotifyAlbumSaved(id)
        if (response.isSuccessful) {
            response.body()?.saved ?: false
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun saveAlbum(id: String): Result<Unit> = runCatching {
        val response = api.saveSpotifyAlbum(id)
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun removeAlbum(id: String): Result<Unit> = runCatching {
        val response = api.removeSpotifyAlbum(id)
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun getArtist(id: String): Result<SpotifyArtist> = runCatching {
        val response = api.getSpotifyArtist(id)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getArtistAlbums(id: String): Result<SpotifyAlbumsResponse> = runCatching {
        val response = api.getSpotifyArtistAlbums(id)
        if (response.isSuccessful) {
            response.body() ?: SpotifyAlbumsResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getArtistTopTracks(id: String): Result<SpotifyArtistTopTracksResponse> = runCatching {
        val response = api.getSpotifyArtistTopTracks(id)
        if (response.isSuccessful) {
            response.body() ?: SpotifyArtistTopTracksResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun isFollowingArtist(id: String): Result<Boolean> = runCatching {
        val response = api.isFollowingArtist(id)
        if (response.isSuccessful) {
            response.body()?.following ?: false
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun followArtist(id: String): Result<Unit> = runCatching {
        val response = api.followArtist(id)
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun unfollowArtist(id: String): Result<Unit> = runCatching {
        val response = api.unfollowArtist(id)
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun getLibraryAlbums(): Result<SpotifyAlbumsResponse> = runCatching {
        val response = api.getSpotifyLibraryAlbums()
        if (response.isSuccessful) {
            response.body() ?: SpotifyAlbumsResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getLibraryArtists(): Result<SpotifyArtistsResponse> = runCatching {
        val response = api.getSpotifyLibraryArtists()
        if (response.isSuccessful) {
            response.body() ?: SpotifyArtistsResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getLibraryTracks(): Result<SpotifyTracksResponse> = runCatching {
        val response = api.getSpotifyLibraryTracks()
        if (response.isSuccessful) {
            response.body() ?: SpotifyTracksResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getQueue(): Result<SpotifyQueue> = runCatching {
        val response = api.getSpotifyQueue()
        if (response.isSuccessful) {
            response.body() ?: SpotifyQueue()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun addToQueue(uri: String, deviceId: String?): Result<Unit> = runCatching {
        val response = api.addToSpotifyQueue(AddToQueueRequest(uri, deviceId))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun getNewReleases(): Result<SpotifyNewReleasesResponse> = runCatching {
        val response = api.getSpotifyNewReleases()
        if (response.isSuccessful) {
            response.body() ?: SpotifyNewReleasesResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getFeaturedPlaylists(): Result<SpotifyFeaturedPlaylistsResponse> = runCatching {
        val response = api.getSpotifyFeaturedPlaylists()
        if (response.isSuccessful) {
            response.body() ?: SpotifyFeaturedPlaylistsResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getCategories(): Result<SpotifyCategoriesResponse> = runCatching {
        val response = api.getSpotifyCategories()
        if (response.isSuccessful) {
            response.body() ?: SpotifyCategoriesResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getCategoryPlaylists(categoryId: String): Result<SpotifyPlaylistsResponse> = runCatching {
        val response = api.getSpotifyCategoryPlaylists(categoryId)
        if (response.isSuccessful) {
            response.body() ?: SpotifyPlaylistsResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getRecommendations(
        seedArtists: List<String>?,
        seedGenres: List<String>?,
        seedTracks: List<String>?
    ): Result<SpotifyRecommendationsResponse> = runCatching {
        val response = api.getSpotifyRecommendations(
            seedArtists = seedArtists?.joinToString(","),
            seedGenres = seedGenres?.joinToString(","),
            seedTracks = seedTracks?.joinToString(",")
        )
        if (response.isSuccessful) {
            response.body() ?: SpotifyRecommendationsResponse()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun saveTrack(id: String): Result<Unit> = runCatching {
        val response = api.saveSpotifyTrack(id)
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun removeTrack(id: String): Result<Unit> = runCatching {
        val response = api.removeSpotifyTrack(id)
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun isTrackSaved(id: String): Result<Boolean> = runCatching {
        val response = api.isSpotifyTrackSaved(id)
        if (response.isSuccessful) {
            response.body()?.saved ?: false
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun areTracksSaved(ids: List<String>): Result<Map<String, Boolean>> = runCatching {
        if (ids.isEmpty()) return@runCatching emptyMap()
        val idsParam = ids.joinToString(",")
        val response = api.areSpotifyTracksSaved(idsParam)
        if (response.isSuccessful) {
            response.body() ?: emptyMap()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }
}

// ============ Calendar Repository ============

interface CalendarRepository {
    suspend fun getEvents(view: String, date: String): Result<List<CalendarEvent>>
    suspend fun getCalendars(): Result<List<Calendar>>
    suspend fun createEvent(event: CalendarEventRequest): Result<CalendarEvent>
    suspend fun updateEvent(calendarId: String, eventId: String, event: CalendarEventRequest): Result<CalendarEvent>
    suspend fun deleteEvent(calendarId: String, eventId: String): Result<Unit>
    suspend fun getTasks(): Result<List<Task>>
    suspend fun toggleTask(listId: String, taskId: String): Result<Unit>
}

class CalendarRepositoryImpl @Inject constructor(
    private val api: HomeControlApi
) : CalendarRepository {

    override suspend fun getEvents(view: String, date: String): Result<List<CalendarEvent>> = runCatching {
        val response = api.getCalendarEvents(view, date)
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getCalendars(): Result<List<Calendar>> = runCatching {
        val response = api.getCalendars()
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun createEvent(event: CalendarEventRequest): Result<CalendarEvent> = runCatching {
        Log.d("CalendarRepository", "createEvent called with: $event")
        val response = api.createEvent(event)
        Log.d("CalendarRepository", "createEvent response: ${response.code()} - ${response.message()}")
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e("CalendarRepository", "createEvent error body: $errorBody")
            throw Exception("API error: ${response.code()} - $errorBody")
        }
    }

    override suspend fun updateEvent(calendarId: String, eventId: String, event: CalendarEventRequest): Result<CalendarEvent> = runCatching {
        val response = api.updateEvent(calendarId, eventId, event)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun deleteEvent(calendarId: String, eventId: String): Result<Unit> = runCatching {
        val response = api.deleteEvent(calendarId, eventId)
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun getTasks(): Result<List<Task>> = runCatching {
        val response = api.getTasks()
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun toggleTask(listId: String, taskId: String): Result<Unit> = runCatching {
        val response = api.toggleTask(listId, taskId)
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }
}

// ============ Camera Repository ============

interface CameraRepository {
    suspend fun getCameras(): Result<List<Camera>>
    fun getSnapshotUrl(name: String): String
    fun getStreamUrl(name: String): String
    /**
     * Send audio to camera for push-to-talk.
     * @param name Camera name
     * @param pcmData Raw PCM audio: 16-bit, mono, 8kHz sample rate
     */
    suspend fun postAudio(name: String, pcmData: ByteArray): Result<Unit>
}

class CameraRepositoryImpl @Inject constructor(
    private val api: HomeControlApi,
    @com.homecontrol.sensors.di.ServerUrl private val serverUrl: String
) : CameraRepository {

    override suspend fun getCameras(): Result<List<Camera>> = runCatching {
        val response = api.getCameras()
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override fun getSnapshotUrl(name: String): String = "${serverUrl}api/camera/$name/snapshot"

    override fun getStreamUrl(name: String): String = "${serverUrl}api/camera/$name/stream"

    override suspend fun postAudio(name: String, pcmData: ByteArray): Result<Unit> = runCatching {
        val requestBody = pcmData.toRequestBody("application/octet-stream".toMediaType())
        val response = api.postCameraAudio(name, requestBody)
        if (!response.isSuccessful) {
            throw Exception("API error: ${response.code()}")
        }
    }
}

// ============ Entertainment Repository ============

interface EntertainmentRepository {
    suspend fun getDevices(): Result<EntertainmentDevices>
    suspend fun getSonyState(name: String): Result<SonyState>
    suspend fun sonyPower(name: String, power: Boolean): Result<Unit>
    suspend fun sonyVolume(name: String, volume: Int): Result<Unit>
    suspend fun sonyMute(name: String, mute: Boolean): Result<Unit>
    suspend fun sonyInput(name: String, input: String): Result<Unit>
    suspend fun getSonySoundSettings(name: String): Result<List<SonySoundSetting>>
    suspend fun setSonySoundSetting(name: String, target: String, value: String): Result<Unit>
    suspend fun sendSonyCommand(name: String, command: String): Result<Unit>
    suspend fun launchSonyApp(name: String, uri: String): Result<Unit>
    suspend fun getShieldState(name: String): Result<ShieldState>
    suspend fun getShieldApps(name: String, includeSystem: Boolean = false): Result<List<ShieldApp>>
    suspend fun shieldPower(name: String, power: Boolean): Result<Unit>
    suspend fun shieldLaunchApp(name: String, app: String): Result<Unit>
    suspend fun shieldSendKey(name: String, key: String): Result<Unit>
    suspend fun getXboxState(name: String): Result<XboxState>
    suspend fun xboxPower(name: String, power: Boolean): Result<Unit>
    suspend fun getPS5State(name: String): Result<PS5State>
    suspend fun ps5Power(name: String, power: Boolean): Result<Unit>
}

class EntertainmentRepositoryImpl @Inject constructor(
    private val api: HomeControlApi
) : EntertainmentRepository {

    override suspend fun getDevices(): Result<EntertainmentDevices> = runCatching {
        val response = api.getEntertainmentDevices()
        if (response.isSuccessful) {
            response.body() ?: EntertainmentDevices()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getSonyState(name: String): Result<SonyState> = runCatching {
        val response = api.getSonyState(name)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun sonyPower(name: String, power: Boolean): Result<Unit> = runCatching {
        val response = api.sonyPower(name, PowerRequest(power))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun sonyVolume(name: String, volume: Int): Result<Unit> = runCatching {
        val response = api.sonyVolume(name, VolumeRequest(volume))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun sonyMute(name: String, mute: Boolean): Result<Unit> = runCatching {
        val response = api.sonyMute(name, MuteRequest(mute))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun sonyInput(name: String, input: String): Result<Unit> = runCatching {
        val response = api.sonyInput(name, InputRequest(input))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun getSonySoundSettings(name: String): Result<List<SonySoundSetting>> = runCatching {
        val response = api.getSonySoundSettings(name)
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun setSonySoundSetting(name: String, target: String, value: String): Result<Unit> = runCatching {
        val response = api.setSonySoundSetting(name, SoundSettingRequest(target, value))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun sendSonyCommand(name: String, command: String): Result<Unit> = runCatching {
        val response = api.sendSonyCommand(name, CommandRequest(command))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun launchSonyApp(name: String, uri: String): Result<Unit> = runCatching {
        val response = api.launchSonyApp(name, SonyAppRequest(uri))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun getShieldState(name: String): Result<ShieldState> = runCatching {
        val response = api.getShieldState(name)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun shieldPower(name: String, power: Boolean): Result<Unit> = runCatching {
        val response = api.shieldPower(name, PowerRequest(power))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun getShieldApps(name: String, includeSystem: Boolean): Result<List<ShieldApp>> = runCatching {
        val response = api.getShieldApps(name, includeSystem)
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun shieldLaunchApp(name: String, app: String): Result<Unit> = runCatching {
        val response = api.shieldLaunchApp(name, AppRequest(packageName = app))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun shieldSendKey(name: String, key: String): Result<Unit> = runCatching {
        val response = api.shieldSendKey(name, ShieldKeyRequest(key))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun getXboxState(name: String): Result<XboxState> = runCatching {
        val response = api.getXboxState(name)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun xboxPower(name: String, power: Boolean): Result<Unit> = runCatching {
        val response = api.xboxPower(name, PowerRequest(power))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }

    override suspend fun getPS5State(name: String): Result<PS5State> = runCatching {
        val response = api.getPS5State(name)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun ps5Power(name: String, power: Boolean): Result<Unit> = runCatching {
        val response = api.ps5Power(name, PowerRequest(power))
        if (!response.isSuccessful) throw Exception("API error: ${response.code()}")
    }
}

// ============ Weather Repository ============

interface WeatherRepository {
    suspend fun getWeather(): Result<Weather>
}

class WeatherRepositoryImpl @Inject constructor(
    private val api: HomeControlApi
) : WeatherRepository {

    override suspend fun getWeather(): Result<Weather> = runCatching {
        val response = api.getWeather()
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }
}

// ============ Drive Repository (Background Images & Screensaver) ============

interface DriveRepository {
    suspend fun getPhotos(): Result<List<DrivePhoto>>
    suspend fun getRandomPhoto(): Result<DrivePhoto>
    suspend fun getScreensaverConfig(): Result<ScreensaverConfig>
    fun getPhotoUrl(id: String): String
    fun getLocalPhotoPath(id: String): String?
    fun hasCachedPhotos(): Boolean
    fun getCachedPhotoIds(): List<String>
}

class DriveRepositoryImpl @Inject constructor(
    private val api: HomeControlApi,
    @com.homecontrol.sensors.di.ServerUrl private val serverUrl: String,
    private val photoSyncManager: com.homecontrol.sensors.data.sync.PhotoSyncManager
) : DriveRepository {

    override suspend fun getPhotos(): Result<List<DrivePhoto>> = runCatching {
        val response = api.getDrivePhotos()
        if (response.isSuccessful) {
            response.body() ?: emptyList()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getRandomPhoto(): Result<DrivePhoto> = runCatching {
        val response = api.getRandomDrivePhoto()
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getScreensaverConfig(): Result<ScreensaverConfig> = runCatching {
        val response = api.getScreensaverConfig()
        if (response.isSuccessful) {
            response.body() ?: ScreensaverConfig()
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override fun getPhotoUrl(id: String): String {
        // Prefer local cached photo if available
        val localPath = photoSyncManager.getCachedPhotoPath(id)
        return if (localPath != null) {
            "file://$localPath"
        } else {
            "${serverUrl}api/drive/photo/$id"
        }
    }

    override fun getLocalPhotoPath(id: String): String? {
        return photoSyncManager.getCachedPhotoPath(id)
    }

    override fun hasCachedPhotos(): Boolean {
        return photoSyncManager.getCachedPhotos().isNotEmpty()
    }

    override fun getCachedPhotoIds(): List<String> {
        return photoSyncManager.getCachedPhotos().map { it.nameWithoutExtension }
    }
}
