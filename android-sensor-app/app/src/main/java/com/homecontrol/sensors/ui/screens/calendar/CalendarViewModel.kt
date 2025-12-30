package com.homecontrol.sensors.ui.screens.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecontrol.sensors.data.model.*
import com.homecontrol.sensors.data.repository.CalendarRepository
import com.homecontrol.sensors.data.repository.DriveRepository
import com.homecontrol.sensors.data.repository.SettingsRepository
import com.homecontrol.sensors.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class CalendarViewMode {
    DAY, WEEK, MONTH
}

data class CalendarUiState(
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val currentDate: LocalDate = LocalDate.now(),
    val events: List<CalendarEvent> = emptyList(),
    val calendars: List<Calendar> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val weather: Weather? = null,
    val backgroundImageUrl: String? = null,
    val use24HourFormat: Boolean = false,
    val isLoading: Boolean = true,
    val isTasksPanelOpen: Boolean = false,
    val error: String? = null,
    // Event editing state
    val selectedEvent: CalendarEvent? = null,
    val isEventModalOpen: Boolean = false,
    val isCreateMode: Boolean = true,
    val eventFormDate: LocalDate = LocalDate.now(),
    val eventFormTime: LocalTime = LocalTime.of(9, 0)
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val weatherRepository: WeatherRepository,
    private val driveRepository: DriveRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val apiDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    init {
        loadInitialData()
        observeSettings()
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
            _uiState.update { it.copy(isLoading = true) }

            // Load calendars, events, tasks, weather, and background in parallel
            launch { loadCalendars() }
            launch { loadEvents() }
            launch { loadTasks() }
            launch { loadWeather() }
            launch { loadBackgroundImage() }
        }
    }

    private suspend fun loadBackgroundImage() {
        driveRepository.getRandomPhoto().onSuccess { photo ->
            val imageUrl = driveRepository.getPhotoUrl(photo.id)
            _uiState.update { it.copy(backgroundImageUrl = imageUrl) }
        }.onFailure { e ->
            android.util.Log.e("CalendarViewModel", "Failed to load background: ${e.message}")
        }
    }

    private suspend fun loadCalendars() {
        calendarRepository.getCalendars().onSuccess { calendars ->
            _uiState.update { it.copy(calendars = calendars) }
        }.onFailure { e ->
            _uiState.update { it.copy(error = "Failed to load calendars: ${e.message}") }
        }
    }

    private suspend fun loadEvents() {
        val state = _uiState.value
        val viewName = when (state.viewMode) {
            CalendarViewMode.DAY -> "day"
            CalendarViewMode.WEEK -> "week"
            CalendarViewMode.MONTH -> "month"
        }

        calendarRepository.getEvents(
            view = viewName,
            date = state.currentDate.format(apiDateFormatter)
        ).onSuccess { events ->
            _uiState.update { it.copy(events = events, isLoading = false) }
        }.onFailure { e ->
            _uiState.update { it.copy(error = "Failed to load events: ${e.message}", isLoading = false) }
        }
    }

    private suspend fun loadTasks() {
        calendarRepository.getTasks().onSuccess { tasks ->
            _uiState.update { it.copy(tasks = tasks) }
        }.onFailure { /* Tasks are optional, silently fail */ }
    }

    private suspend fun loadWeather() {
        weatherRepository.getWeather().onSuccess { weather ->
            android.util.Log.d("CalendarViewModel", "Weather loaded: ${weather.current.temp}")
            _uiState.update { it.copy(weather = weather) }
        }.onFailure { e ->
            android.util.Log.e("CalendarViewModel", "Weather failed: ${e.message}", e)
        }
    }

    fun setViewMode(mode: CalendarViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
        viewModelScope.launch { loadEvents() }
    }

    fun navigateDate(delta: Int) {
        val state = _uiState.value
        val newDate = when (state.viewMode) {
            CalendarViewMode.DAY -> state.currentDate.plusDays(delta.toLong())
            CalendarViewMode.WEEK -> state.currentDate.plusWeeks(delta.toLong())
            CalendarViewMode.MONTH -> state.currentDate.plusMonths(delta.toLong())
        }
        _uiState.update { it.copy(currentDate = newDate) }
        viewModelScope.launch { loadEvents() }
    }

    fun goToToday() {
        _uiState.update { it.copy(currentDate = LocalDate.now()) }
        viewModelScope.launch { loadEvents() }
    }

    fun goToDate(date: LocalDate) {
        _uiState.update { it.copy(currentDate = date) }
        viewModelScope.launch { loadEvents() }
    }

    fun toggleTasksPanel() {
        _uiState.update { it.copy(isTasksPanelOpen = !it.isTasksPanelOpen) }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            calendarRepository.toggleTask(task.listId, task.id).onSuccess {
                loadTasks()
            }.onFailure { e ->
                _uiState.update { it.copy(error = "Failed to toggle task: ${e.message}") }
            }
        }
    }

    // Event modal management
    fun openCreateEventModal(date: LocalDate, time: LocalTime = LocalTime.of(9, 0)) {
        _uiState.update {
            it.copy(
                isEventModalOpen = true,
                isCreateMode = true,
                selectedEvent = null,
                eventFormDate = date,
                eventFormTime = time
            )
        }
    }

    fun openEditEventModal(event: CalendarEvent) {
        _uiState.update {
            it.copy(
                isEventModalOpen = true,
                isCreateMode = false,
                selectedEvent = event
            )
        }
    }

    fun closeEventModal() {
        _uiState.update {
            it.copy(
                isEventModalOpen = false,
                selectedEvent = null
            )
        }
    }

    fun createEvent(request: CalendarEventRequest) {
        Log.d("CalendarViewModel", "createEvent called with request: $request")
        viewModelScope.launch {
            calendarRepository.createEvent(request).onSuccess {
                Log.d("CalendarViewModel", "Event created successfully")
                closeEventModal()
                loadEvents()
            }.onFailure { e ->
                Log.e("CalendarViewModel", "Failed to create event", e)
                _uiState.update { it.copy(error = "Failed to create event: ${e.message}") }
            }
        }
    }

    fun updateEvent(event: CalendarEvent, request: CalendarEventRequest) {
        viewModelScope.launch {
            calendarRepository.updateEvent(event.calendarId, event.id, request).onSuccess {
                closeEventModal()
                loadEvents()
            }.onFailure { e ->
                _uiState.update { it.copy(error = "Failed to update event: ${e.message}") }
            }
        }
    }

    fun deleteEvent(event: CalendarEvent) {
        viewModelScope.launch {
            calendarRepository.deleteEvent(event.calendarId, event.id).onSuccess {
                closeEventModal()
                loadEvents()
            }.onFailure { e ->
                _uiState.update { it.copy(error = "Failed to delete event: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        loadInitialData()
    }

    // Helper functions for date calculations
    private fun getDateRange(viewMode: CalendarViewMode, currentDate: LocalDate): Pair<LocalDate, LocalDate> {
        return when (viewMode) {
            CalendarViewMode.DAY -> {
                currentDate to currentDate.plusDays(1)
            }
            CalendarViewMode.WEEK -> {
                val weekStart = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                weekStart to weekStart.plusDays(7)
            }
            CalendarViewMode.MONTH -> {
                val yearMonth = YearMonth.from(currentDate)
                val monthStart = yearMonth.atDay(1)
                val monthEnd = yearMonth.atEndOfMonth()
                // Include days from previous/next month that appear in the calendar grid
                val gridStart = monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                val gridEnd = monthEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                gridStart to gridEnd.plusDays(1)
            }
        }
    }

    // Get events for a specific date
    fun getEventsForDate(date: LocalDate): List<CalendarEvent> {
        return _uiState.value.events.filter { event ->
            val eventDate = parseEventDate(event.start)
            eventDate == date
        }
    }

    // Get events for a date range (for week view)
    fun getEventsForDateRange(startDate: LocalDate, endDate: LocalDate): List<CalendarEvent> {
        return _uiState.value.events.filter { event ->
            val eventDate = parseEventDate(event.start)
            eventDate != null && !eventDate.isBefore(startDate) && eventDate.isBefore(endDate)
        }
    }

    private fun parseEventDate(dateTimeStr: String): LocalDate? {
        return try {
            // Handle ISO datetime string like "2025-12-29T00:00:00-08:00"
            val dtStr = dateTimeStr.substringBefore("+")
            val cleanDtStr = dtStr.replace(Regex("-\\d{2}:\\d{2}$"), "")
            if (cleanDtStr.contains("T")) {
                LocalDateTime.parse(cleanDtStr).toLocalDate()
            } else {
                LocalDate.parse(cleanDtStr)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun parseEventDateTime(dateTimeStr: String): LocalDateTime? {
        return try {
            val dtStr = dateTimeStr.substringBefore("+")
            val cleanDtStr = dtStr.replace(Regex("-\\d{2}:\\d{2}$"), "")
            if (cleanDtStr.contains("T")) {
                LocalDateTime.parse(cleanDtStr)
            } else {
                LocalDate.parse(cleanDtStr).atStartOfDay()
            }
        } catch (e: Exception) {
            null
        }
    }
}
