package com.homecontrol.sensors.data.repository

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
    suspend fun getAlbum(id: String): Result<SpotifyAlbum>
    suspend fun getArtist(id: String): Result<SpotifyArtist>
    suspend fun getLibraryAlbums(): Result<SpotifyAlbumsResponse>
    suspend fun getLibraryArtists(): Result<SpotifyArtistsResponse>
}

class SpotifyRepositoryImpl @Inject constructor(
    private val api: HomeControlApi
) : SpotifyRepository {

    override suspend fun getPlayback(): Result<SpotifyPlayback> = runCatching {
        val response = api.getSpotifyPlayback()
        if (response.isSuccessful) {
            response.body() ?: SpotifyPlayback()
        } else {
            throw Exception("API error: ${response.code()}")
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
        val response = api.spotifyPlay(request)
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

    override suspend fun getAlbum(id: String): Result<SpotifyAlbum> = runCatching {
        val response = api.getSpotifyAlbum(id)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("API error: ${response.code()}")
        }
    }

    override suspend fun getArtist(id: String): Result<SpotifyArtist> = runCatching {
        val response = api.getSpotifyArtist(id)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("API error: ${response.code()}")
        }
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
        val response = api.createEvent(event)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("API error: ${response.code()}")
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
    suspend fun getShieldState(name: String): Result<ShieldState>
    suspend fun shieldPower(name: String, power: Boolean): Result<Unit>
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
