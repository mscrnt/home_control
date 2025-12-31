package com.homecontrol.sensors.data.api

import com.homecontrol.sensors.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface HomeControlApi {

    // ============ Entities (Home Assistant) ============

    @GET("api/entities")
    suspend fun getEntities(): Response<List<EntityGroup>>

    @POST("api/toggle/{entityId}")
    suspend fun toggleEntity(@Path("entityId") entityId: String): Response<Unit>

    // Climate
    @POST("api/climate/{entityId}/temperature")
    suspend fun setClimateTemperature(
        @Path("entityId") entityId: String,
        @Body request: TemperatureRequest
    ): Response<Unit>

    @POST("api/climate/{entityId}/mode")
    suspend fun setClimateMode(
        @Path("entityId") entityId: String,
        @Body request: ModeRequest
    ): Response<Unit>

    @POST("api/climate/{entityId}/fan")
    suspend fun setClimateFanMode(
        @Path("entityId") entityId: String,
        @Body request: FanModeRequest
    ): Response<Unit>

    // ============ Hue ============

    @GET("api/hue/rooms")
    suspend fun getHueRooms(): Response<List<HueRoom>>

    @POST("api/hue/light/{id}/toggle")
    suspend fun toggleHueLight(@Path("id") id: String): Response<Unit>

    @POST("api/hue/light/{id}/brightness")
    suspend fun setHueLightBrightness(
        @Path("id") id: String,
        @Body request: BrightnessRequest
    ): Response<Unit>

    @POST("api/hue/group/{id}/toggle")
    suspend fun toggleHueGroup(@Path("id") id: String): Response<Unit>

    @POST("api/hue/group/{id}/brightness")
    suspend fun setHueGroupBrightness(
        @Path("id") id: String,
        @Body request: BrightnessRequest
    ): Response<Unit>

    @POST("api/hue/scene/{id}/activate")
    suspend fun activateHueScene(@Path("id") id: String): Response<Unit>

    @POST("api/hue/entertainment/{id}/activate")
    suspend fun activateEntertainment(@Path("id") id: String): Response<Unit>

    @POST("api/hue/entertainment/deactivate")
    suspend fun deactivateEntertainment(): Response<Unit>

    @GET("api/hue/entertainment/status")
    suspend fun getEntertainmentStatus(): Response<EntertainmentStatus>

    // ============ Sync Box ============

    @GET("api/syncbox")
    suspend fun getSyncBoxes(): Response<List<SyncBox>>

    @GET("api/syncbox/{index}/status")
    suspend fun getSyncBoxStatus(@Path("index") index: Int): Response<SyncBoxStatus>

    @POST("api/syncbox/{index}/sync")
    suspend fun setSyncBoxSync(
        @Path("index") index: Int,
        @Body request: SyncRequest
    ): Response<Unit>

    @POST("api/syncbox/{index}/mode")
    suspend fun setSyncBoxMode(
        @Path("index") index: Int,
        @Body request: ModeRequest
    ): Response<Unit>

    @POST("api/syncbox/{index}/brightness")
    suspend fun setSyncBoxBrightness(
        @Path("index") index: Int,
        @Body request: BrightnessRequest
    ): Response<Unit>

    @POST("api/syncbox/{index}/input")
    suspend fun setSyncBoxInput(
        @Path("index") index: Int,
        @Body request: InputRequest
    ): Response<Unit>

    // ============ Spotify ============

    @GET("api/spotify/status")
    suspend fun getSpotifyStatus(): Response<SpotifyStatus>

    @GET("api/spotify/playback")
    suspend fun getSpotifyPlayback(): Response<SpotifyPlayback?>

    @GET("api/spotify/devices")
    suspend fun getSpotifyDevices(): Response<List<SpotifyDevice>>

    @POST("api/spotify/play")
    suspend fun spotifyPlay(@Body request: SpotifyPlayRequest? = null): Response<Unit>

    @POST("api/spotify/pause")
    suspend fun spotifyPause(): Response<Unit>

    @POST("api/spotify/next")
    suspend fun spotifyNext(): Response<Unit>

    @POST("api/spotify/previous")
    suspend fun spotifyPrevious(): Response<Unit>

    @POST("api/spotify/volume")
    suspend fun spotifyVolume(@Body request: VolumeRequest): Response<Unit>

    @POST("api/spotify/seek")
    suspend fun spotifySeek(@Body request: SeekRequest): Response<Unit>

    @POST("api/spotify/shuffle")
    suspend fun spotifyShuffle(@Body request: ShuffleRequest): Response<Unit>

    @POST("api/spotify/repeat")
    suspend fun spotifyRepeat(@Body request: RepeatRequest): Response<Unit>

    @POST("api/spotify/transfer")
    suspend fun spotifyTransfer(@Body request: TransferRequest): Response<Unit>

    @GET("api/spotify/playlists")
    suspend fun getSpotifyPlaylists(): Response<SpotifyPlaylistsResponse>

    @GET("api/spotify/playlist/{id}/tracks")
    suspend fun getSpotifyPlaylistTracks(@Path("id") id: String): Response<SpotifyTracksResponse>

    @GET("api/spotify/search")
    suspend fun spotifySearch(@Query("q") query: String): Response<SpotifySearchResponse>

    @GET("api/spotify/recent")
    suspend fun getSpotifyRecentlyPlayed(): Response<SpotifyRecentResponse>

    @GET("api/spotify/top/artists")
    suspend fun getSpotifyTopArtists(): Response<SpotifyArtistsResponse>

    @GET("api/spotify/top/tracks")
    suspend fun getSpotifyTopTracks(): Response<SpotifyTopTracksResponse>

    @GET("api/spotify/album/{id}")
    suspend fun getSpotifyAlbum(@Path("id") id: String): Response<SpotifyAlbum>

    @GET("api/spotify/album/{id}/saved")
    suspend fun isSpotifyAlbumSaved(@Path("id") id: String): Response<SavedResponse>

    @PUT("api/spotify/album/{id}/save")
    suspend fun saveSpotifyAlbum(@Path("id") id: String): Response<Unit>

    @DELETE("api/spotify/album/{id}/save")
    suspend fun removeSpotifyAlbum(@Path("id") id: String): Response<Unit>

    @GET("api/spotify/artist/{id}")
    suspend fun getSpotifyArtist(@Path("id") id: String): Response<SpotifyArtist>

    @GET("api/spotify/artist/{id}/albums")
    suspend fun getSpotifyArtistAlbums(@Path("id") id: String): Response<SpotifyAlbumsResponse>

    @GET("api/spotify/artist/{id}/top-tracks")
    suspend fun getSpotifyArtistTopTracks(@Path("id") id: String): Response<SpotifyArtistTopTracksResponse>

    @GET("api/spotify/artist/{id}/following")
    suspend fun isFollowingArtist(@Path("id") id: String): Response<FollowingResponse>

    @PUT("api/spotify/artist/{id}/follow")
    suspend fun followArtist(@Path("id") id: String): Response<Unit>

    @DELETE("api/spotify/artist/{id}/follow")
    suspend fun unfollowArtist(@Path("id") id: String): Response<Unit>

    @GET("api/spotify/library/albums")
    suspend fun getSpotifyLibraryAlbums(): Response<SpotifyAlbumsResponse>

    @GET("api/spotify/library/artists")
    suspend fun getSpotifyLibraryArtists(): Response<SpotifyArtistsResponse>

    @GET("api/spotify/library/tracks")
    suspend fun getSpotifyLibraryTracks(): Response<SpotifyTracksResponse>

    // Queue
    @GET("api/spotify/queue")
    suspend fun getSpotifyQueue(): Response<SpotifyQueue>

    @POST("api/spotify/queue")
    suspend fun addToSpotifyQueue(@Body request: AddToQueueRequest): Response<Unit>

    // Browse
    @GET("api/spotify/browse/new-releases")
    suspend fun getSpotifyNewReleases(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyNewReleasesResponse>

    @GET("api/spotify/browse/featured")
    suspend fun getSpotifyFeaturedPlaylists(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyFeaturedPlaylistsResponse>

    @GET("api/spotify/browse/categories")
    suspend fun getSpotifyCategories(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyCategoriesResponse>

    @GET("api/spotify/browse/categories/{id}/playlists")
    suspend fun getSpotifyCategoryPlaylists(
        @Path("id") categoryId: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<SpotifyPlaylistsResponse>

    // Recommendations
    @GET("api/spotify/recommendations")
    suspend fun getSpotifyRecommendations(
        @Query("seed_artists") seedArtists: String? = null,
        @Query("seed_genres") seedGenres: String? = null,
        @Query("seed_tracks") seedTracks: String? = null,
        @Query("limit") limit: Int = 20
    ): Response<SpotifyRecommendationsResponse>

    // Track operations
    @PUT("api/spotify/track/{id}/save")
    suspend fun saveSpotifyTrack(@Path("id") trackId: String): Response<Unit>

    @DELETE("api/spotify/track/{id}/save")
    suspend fun removeSpotifyTrack(@Path("id") trackId: String): Response<Unit>

    @GET("api/spotify/track/{id}/saved")
    suspend fun isSpotifyTrackSaved(@Path("id") trackId: String): Response<SavedResponse>

    @GET("api/spotify/tracks/saved")
    suspend fun areSpotifyTracksSaved(@Query("ids") ids: String): Response<Map<String, Boolean>>

    // ============ Calendar ============

    @GET("api/calendar/events")
    suspend fun getCalendarEvents(
        @Query("view") view: String,
        @Query("date") date: String
    ): Response<List<CalendarEvent>>

    @GET("api/calendar/prefs")
    suspend fun getCalendars(): Response<List<Calendar>>

    @GET("api/calendar/colors")
    suspend fun getCalendarColors(): Response<CalendarColors>

    @POST("api/calendar/event")
    suspend fun createEvent(@Body event: CalendarEventRequest): Response<CalendarEvent>

    @GET("api/calendar/event/{calendarId}/{eventId}")
    suspend fun getEvent(
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String
    ): Response<CalendarEvent>

    @PUT("api/calendar/event/{calendarId}/{eventId}")
    suspend fun updateEvent(
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String,
        @Body event: CalendarEventRequest
    ): Response<CalendarEvent>

    @DELETE("api/calendar/event/{calendarId}/{eventId}")
    suspend fun deleteEvent(
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String
    ): Response<Unit>

    // ============ Tasks ============

    @GET("api/tasks")
    suspend fun getTasks(): Response<List<Task>?>

    @GET("api/tasks/lists")
    suspend fun getTaskLists(): Response<List<TaskList>?>

    @POST("api/tasks")
    suspend fun createTask(@Body task: TaskRequest): Response<Task>

    @POST("api/tasks/{listId}/{taskId}/toggle")
    suspend fun toggleTask(
        @Path("listId") listId: String,
        @Path("taskId") taskId: String
    ): Response<Unit>

    @DELETE("api/tasks/{listId}/{taskId}")
    suspend fun deleteTask(
        @Path("listId") listId: String,
        @Path("taskId") taskId: String
    ): Response<Unit>

    // ============ Weather ============

    @GET("api/weather")
    suspend fun getWeather(): Response<Weather>

    // ============ Cameras ============

    @GET("api/cameras")
    suspend fun getCameras(): Response<List<Camera>>

    @GET("api/camera/{name}/snapshot")
    suspend fun getCameraSnapshot(@Path("name") name: String): Response<okhttp3.ResponseBody>

    /**
     * Send audio to camera for push-to-talk.
     * Body should be raw PCM audio: 16-bit, mono, 8kHz sample rate.
     */
    @POST("api/camera/{name}/talk")
    suspend fun postCameraAudio(
        @Path("name") name: String,
        @Body audio: okhttp3.RequestBody
    ): Response<Unit>

    // ============ Entertainment ============

    @GET("api/entertainment/devices")
    suspend fun getEntertainmentDevices(): Response<EntertainmentDevices>

    // Sony
    @GET("api/entertainment/sony")
    suspend fun getSonyDevices(): Response<List<SonyDevice>>

    @GET("api/entertainment/sony/{name}/state")
    suspend fun getSonyState(@Path("name") name: String): Response<SonyState>

    @POST("api/entertainment/sony/{name}/power")
    suspend fun sonyPower(
        @Path("name") name: String,
        @Body request: PowerRequest
    ): Response<Unit>

    @POST("api/entertainment/sony/{name}/volume")
    suspend fun sonyVolume(
        @Path("name") name: String,
        @Body request: VolumeRequest
    ): Response<Unit>

    @POST("api/entertainment/sony/{name}/mute")
    suspend fun sonyMute(
        @Path("name") name: String,
        @Body request: MuteRequest
    ): Response<Unit>

    @POST("api/entertainment/sony/{name}/input")
    suspend fun sonyInput(
        @Path("name") name: String,
        @Body request: InputRequest
    ): Response<Unit>

    // Shield
    @GET("api/entertainment/shield")
    suspend fun getShieldDevices(): Response<List<ShieldDevice>>

    @GET("api/entertainment/shield/{name}/state")
    suspend fun getShieldState(@Path("name") name: String): Response<ShieldState>

    @POST("api/entertainment/shield/{name}/power")
    suspend fun shieldPower(
        @Path("name") name: String,
        @Body request: PowerRequest
    ): Response<Unit>

    @POST("api/entertainment/shield/{name}/app")
    suspend fun shieldLaunchApp(
        @Path("name") name: String,
        @Body request: AppRequest
    ): Response<Unit>

    // Xbox
    @GET("api/entertainment/xbox")
    suspend fun getXboxDevices(): Response<List<XboxDevice>>

    @GET("api/entertainment/xbox/{name}/state")
    suspend fun getXboxState(@Path("name") name: String): Response<XboxState>

    @POST("api/entertainment/xbox/{name}/power")
    suspend fun xboxPower(
        @Path("name") name: String,
        @Body request: PowerRequest
    ): Response<Unit>

    // PS5
    @GET("api/entertainment/ps5")
    suspend fun getPS5Devices(): Response<List<PS5Device>>

    @GET("api/entertainment/ps5/{name}/state")
    suspend fun getPS5State(@Path("name") name: String): Response<PS5State>

    @POST("api/entertainment/ps5/{name}/power")
    suspend fun ps5Power(
        @Path("name") name: String,
        @Body request: PowerRequest
    ): Response<Unit>

    // ============ Drive (Screensaver) ============

    @GET("api/drive/photos")
    suspend fun getDrivePhotos(): Response<List<DrivePhoto>>

    @GET("api/drive/photos/random")
    suspend fun getRandomDrivePhoto(): Response<DrivePhoto>

    @GET("api/screensaver/config")
    suspend fun getScreensaverConfig(): Response<ScreensaverConfig>
}
