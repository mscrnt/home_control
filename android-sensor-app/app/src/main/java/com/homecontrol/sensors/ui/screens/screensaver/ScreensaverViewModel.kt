package com.homecontrol.sensors.ui.screens.screensaver

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.sensors.data.model.CalendarEvent
import com.homecontrol.sensors.data.model.DrivePhoto
import com.homecontrol.sensors.data.model.ScreensaverConfig
import com.homecontrol.sensors.data.model.SpotifyPlayback
import com.homecontrol.sensors.data.model.Weather
import com.homecontrol.sensors.data.repository.CalendarRepository
import com.homecontrol.sensors.data.repository.DriveRepository
import com.homecontrol.sensors.data.repository.SettingsRepository
import com.homecontrol.sensors.data.repository.SpotifyRepository
import com.homecontrol.sensors.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import com.homecontrol.sensors.ui.screens.calendar.Holiday
import com.homecontrol.sensors.ui.screens.calendar.Holidays

private const val TAG = "ScreensaverViewModel"

data class ScreensaverUiState(
    val currentPhotoUrl: String? = null,
    val nextPhotoUrl: String? = null,
    val photos: List<DrivePhoto> = emptyList(),
    val photoIds: List<String> = emptyList(),  // For cached photos fallback
    val currentPhotoIndex: Int = 0,
    val weather: Weather? = null,
    val playback: SpotifyPlayback? = null,
    val config: ScreensaverConfig = ScreensaverConfig(),
    val currentTime: String = "",
    val currentDate: String = "",
    val isTransitioning: Boolean = false,
    val serverUrl: String = "",
    val usingCachedPhotos: Boolean = false,
    val todayEvents: List<CalendarEvent> = emptyList(),
    val todayHolidays: List<Holiday> = emptyList(),
    val use24HourFormat: Boolean = false
)

@HiltViewModel
class ScreensaverViewModel @Inject constructor(
    private val driveRepository: DriveRepository,
    private val weatherRepository: WeatherRepository,
    private val spotifyRepository: SpotifyRepository,
    private val calendarRepository: CalendarRepository,
    private val settingsRepository: SettingsRepository,
    @com.homecontrol.sensors.di.ServerUrl private val serverUrl: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScreensaverUiState(serverUrl = serverUrl))
    val uiState: StateFlow<ScreensaverUiState> = _uiState.asStateFlow()

    private var slideshowJob: Job? = null
    private var clockJob: Job? = null
    private var weatherJob: Job? = null
    private var spotifyJob: Job? = null
    private var calendarJob: Job? = null

    private val timeFormatter12 = DateTimeFormatter.ofPattern("h:mm a")
    private val timeFormatter24 = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")

    init {
        observeSettings()
        loadInitialData()
        startClockUpdates()
        startSpotifyPolling()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(use24HourFormat = settings.use24HourFormat) }
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // Load config
            driveRepository.getScreensaverConfig().onSuccess { config ->
                _uiState.update { it.copy(config = config) }
            }

            // Try to load photos from server, fall back to cached photos
            driveRepository.getPhotos()
                .onSuccess { photos ->
                    if (photos.isNotEmpty()) {
                        Log.d(TAG, "Loaded ${photos.size} photos from server")
                        _uiState.update {
                            it.copy(
                                photos = photos,
                                photoIds = photos.map { p -> p.id },
                                currentPhotoUrl = driveRepository.getPhotoUrl(photos[0].id),
                                usingCachedPhotos = false
                            )
                        }
                        startSlideshow()
                    } else {
                        // No photos from server, try cached
                        loadCachedPhotos()
                    }
                }
                .onFailure { error ->
                    Log.w(TAG, "Failed to load photos from server: ${error.message}")
                    // Fall back to cached photos
                    loadCachedPhotos()
                }

            // Load weather
            loadWeather()

            // Load today's events
            loadTodayEvents()
        }
    }

    private suspend fun loadTodayEvents() {
        val today = java.time.LocalDate.now()
        val todayStr = today.toString() // Format: YYYY-MM-DD

        // Load holidays for today (these are computed locally, not from API)
        val todayHolidays = Holidays.getRegularHolidaysForDate(today)
        _uiState.update { it.copy(todayHolidays = todayHolidays) }
        if (todayHolidays.isNotEmpty()) {
            Log.d(TAG, "Today's holidays: ${todayHolidays.map { it.name }}")
        }

        calendarRepository.getEvents("day", todayStr).onSuccess { events ->
            // Filter to only events that start today, then sort by start time
            // Handle various date formats: "2026-01-01", "2026-01-01T00:00:00", "2026-01-01T00:00:00-08:00"
            val todayEvents = events.filter { event ->
                val startDate = extractDateFromStart(event.start)
                startDate == todayStr
            }.sortedBy { it.start }
            _uiState.update { it.copy(todayEvents = todayEvents) }
            Log.d(TAG, "Loaded ${todayEvents.size} events for today (filtered from ${events.size})")
            if (events.isNotEmpty()) {
                Log.d(TAG, "Sample event starts: ${events.take(3).map { "${it.title}: ${it.start}" }}")
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to load today's events: ${error.message}")
        }
    }

    /**
     * Extract the date portion (YYYY-MM-DD) from various date/time formats.
     * Handles: "2026-01-01", "2026-01-01T00:00:00", "2026-01-01T00:00:00-08:00", "2026-01-01T00:00:00Z"
     */
    private fun extractDateFromStart(start: String): String {
        // If it contains 'T', take the part before it
        // If it doesn't contain 'T', it's just a date - take first 10 chars
        return if (start.contains("T")) {
            start.substringBefore("T")
        } else {
            start.take(10)
        }
    }

    private fun loadCachedPhotos() {
        val cachedIds = driveRepository.getCachedPhotoIds()
        if (cachedIds.isNotEmpty()) {
            Log.d(TAG, "Using ${cachedIds.size} cached photos")
            _uiState.update {
                it.copy(
                    photoIds = cachedIds,
                    currentPhotoUrl = driveRepository.getPhotoUrl(cachedIds[0]),
                    usingCachedPhotos = true
                )
            }
            startSlideshow()
        } else {
            Log.w(TAG, "No cached photos available")
        }
    }

    private suspend fun loadWeather() {
        weatherRepository.getWeather().onSuccess { weather ->
            _uiState.update { it.copy(weather = weather) }
        }
    }

    private fun startClockUpdates() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val use24Hour = _uiState.value.use24HourFormat
                val timeFormatter = if (use24Hour) timeFormatter24 else timeFormatter12
                _uiState.update {
                    it.copy(
                        currentTime = now.format(timeFormatter),
                        currentDate = now.format(dateFormatter)
                    )
                }
                delay(1000) // Update every second
            }
        }
    }

    private fun startSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            val intervalMs = (_uiState.value.config.slideInterval * 1000).toLong()

            while (true) {
                delay(intervalMs)

                // Use photoIds which works for both server photos and cached photos
                val photoIds = _uiState.value.photoIds
                if (photoIds.isNotEmpty()) {
                    val nextIndex = (_uiState.value.currentPhotoIndex + 1) % photoIds.size
                    val nextPhotoUrl = driveRepository.getPhotoUrl(photoIds[nextIndex])

                    // Start transition
                    _uiState.update {
                        it.copy(
                            nextPhotoUrl = nextPhotoUrl,
                            isTransitioning = true
                        )
                    }

                    // Wait for transition animation
                    delay(1000)

                    // Complete transition
                    _uiState.update {
                        it.copy(
                            currentPhotoUrl = nextPhotoUrl,
                            currentPhotoIndex = nextIndex,
                            nextPhotoUrl = null,
                            isTransitioning = false
                        )
                    }
                }
            }
        }
    }

    private fun startSpotifyPolling() {
        spotifyJob?.cancel()
        spotifyJob = viewModelScope.launch {
            while (true) {
                spotifyRepository.getPlayback().onSuccess { playback ->
                    _uiState.update { it.copy(playback = playback) }
                }
                delay(5000) // Poll every 5 seconds to respect Spotify rate limits
            }
        }
    }

    // Playback controls
    fun togglePlayPause() {
        viewModelScope.launch {
            val isPlaying = _uiState.value.playback?.isPlaying ?: false
            if (isPlaying) {
                spotifyRepository.pause()
            } else {
                spotifyRepository.play()
            }
            delay(100)
            spotifyRepository.getPlayback().onSuccess { playback ->
                _uiState.update { it.copy(playback = playback) }
            }
        }
    }

    fun next() {
        viewModelScope.launch {
            spotifyRepository.next()
            delay(100)
            spotifyRepository.getPlayback().onSuccess { playback ->
                _uiState.update { it.copy(playback = playback) }
            }
        }
    }

    fun previous() {
        viewModelScope.launch {
            spotifyRepository.previous()
            delay(100)
            spotifyRepository.getPlayback().onSuccess { playback ->
                _uiState.update { it.copy(playback = playback) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        slideshowJob?.cancel()
        clockJob?.cancel()
        weatherJob?.cancel()
        spotifyJob?.cancel()
        calendarJob?.cancel()
    }
}
