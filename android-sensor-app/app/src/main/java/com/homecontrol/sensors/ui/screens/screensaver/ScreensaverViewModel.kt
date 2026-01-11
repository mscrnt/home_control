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
    val tomorrowEvents: List<CalendarEvent> = emptyList(),
    val tomorrowHolidays: List<Holiday> = emptyList(),
    val showTomorrow: Boolean = false,  // True when after 9pm
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

    // Track the last date we loaded events for to detect day changes
    private var lastLoadedEventsDate: java.time.LocalDate? = null

    init {
        observeSettings()
        loadInitialData()
        startClockUpdates()
        startSpotifyPolling()
        startWeatherPolling()
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
        val now = java.time.LocalDateTime.now()
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)
        val todayStr = today.toString() // Format: YYYY-MM-DD
        val tomorrowStr = tomorrow.toString()

        // Check if it's after 9pm (21:00)
        val showTomorrow = now.hour >= 21

        // Check if date has changed - if so, clear old events first
        if (lastLoadedEventsDate != null && lastLoadedEventsDate != today) {
            Log.d(TAG, "Date changed from $lastLoadedEventsDate to $today, clearing old events")
            _uiState.update { it.copy(
                todayEvents = emptyList(),
                todayHolidays = emptyList(),
                tomorrowEvents = emptyList(),
                tomorrowHolidays = emptyList()
            ) }
        }
        lastLoadedEventsDate = today

        // Load holidays for today (these are computed locally, not from API)
        val todayHolidays = Holidays.getRegularHolidaysForDate(today)
        val tomorrowHolidays = if (showTomorrow) Holidays.getRegularHolidaysForDate(tomorrow) else emptyList()
        _uiState.update { it.copy(
            todayHolidays = todayHolidays,
            tomorrowHolidays = tomorrowHolidays,
            showTomorrow = showTomorrow
        ) }
        if (todayHolidays.isNotEmpty()) {
            Log.d(TAG, "Today's holidays: ${todayHolidays.map { it.name }}")
        }

        // Load today's events
        calendarRepository.getEvents("day", todayStr).onSuccess { events ->
            val todayEvents = events.filter { event ->
                val startDate = extractDateFromStart(event.start)
                startDate == todayStr
            }.sortedBy { it.start }
            _uiState.update { it.copy(todayEvents = todayEvents) }
            Log.d(TAG, "Loaded ${todayEvents.size} events for today (filtered from ${events.size})")
        }.onFailure { error ->
            Log.w(TAG, "Failed to load today's events: ${error.message}")
            if (lastLoadedEventsDate == today) {
                _uiState.update { it.copy(todayEvents = emptyList()) }
            }
        }

        // Load tomorrow's events if after 9pm
        if (showTomorrow) {
            calendarRepository.getEvents("day", tomorrowStr).onSuccess { events ->
                val tomorrowEvents = events.filter { event ->
                    val startDate = extractDateFromStart(event.start)
                    startDate == tomorrowStr
                }.sortedBy { it.start }
                _uiState.update { it.copy(tomorrowEvents = tomorrowEvents) }
                Log.d(TAG, "Loaded ${tomorrowEvents.size} events for tomorrow")
            }.onFailure { error ->
                Log.w(TAG, "Failed to load tomorrow's events: ${error.message}")
                _uiState.update { it.copy(tomorrowEvents = emptyList()) }
            }
        } else {
            _uiState.update { it.copy(tomorrowEvents = emptyList(), tomorrowHolidays = emptyList()) }
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

    private fun startWeatherPolling() {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            while (true) {
                delay(15 * 60_000) // Poll every 15 minutes
                Log.d(TAG, "Auto-refreshing weather")
                loadWeather()
            }
        }
    }

    private fun startClockUpdates() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            var lastCheckedDate: java.time.LocalDate? = null
            var lastShowTomorrow: Boolean? = null
            var tickCount = 0

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

                // Check for date change or 9pm transition every minute (60 ticks) or on first run
                tickCount++
                if (tickCount >= 60 || lastCheckedDate == null) {
                    tickCount = 0
                    val today = now.toLocalDate()
                    val shouldShowTomorrow = now.hour >= 21

                    // Reload events if date changed OR if we just crossed 9pm
                    if (lastCheckedDate != today ||
                        (lastShowTomorrow == false && shouldShowTomorrow)) {
                        Log.d(TAG, "Date changed or crossed 9pm, refreshing events (showTomorrow: $shouldShowTomorrow)")
                        lastCheckedDate = today
                        loadTodayEvents()
                    }
                    lastShowTomorrow = shouldShowTomorrow
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
