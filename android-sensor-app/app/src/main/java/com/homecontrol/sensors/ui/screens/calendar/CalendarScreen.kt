package com.homecontrol.sensors.ui.screens.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.homecontrol.sensors.data.model.Calendar
import com.homecontrol.sensors.data.model.Weather
import com.homecontrol.sensors.ui.components.LoadingIndicator
import com.homecontrol.sensors.ui.components.WeatherWidget
import com.homecontrol.sensors.ui.components.WeatherWidgetExpanded
import com.homecontrol.sensors.ui.theme.HomeControlColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showWeatherModal by remember { mutableStateOf(false) }

    // Show errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header
                CalendarHeader(
                    viewMode = uiState.viewMode,
                    currentDate = uiState.currentDate,
                    weather = uiState.weather,
                    calendars = uiState.calendars,
                    onViewModeChange = { viewModel.setViewMode(it) },
                    onNavigatePrev = { viewModel.navigateDate(-1) },
                    onNavigateNext = { viewModel.navigateDate(1) },
                    onTodayClick = { viewModel.goToToday() },
                    onTasksClick = { viewModel.toggleTasksPanel() },
                    onAddEventClick = { viewModel.openCreateEventModal(uiState.currentDate) },
                    onWeatherClick = { showWeatherModal = true },
                    onSmartHomeClick = onOpenDrawer
                )

                // Calendar content with swipe gesture support
                if (uiState.isLoading) {
                    LoadingIndicator()
                } else {
                    // Track swipe gesture
                    var swipeOffset by remember { mutableStateOf(0f) }
                    val swipeThreshold = 100f

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (swipeOffset > swipeThreshold) {
                                            // Swipe right = previous
                                            viewModel.navigateDate(-1)
                                        } else if (swipeOffset < -swipeThreshold) {
                                            // Swipe left = next
                                            viewModel.navigateDate(1)
                                        }
                                        swipeOffset = 0f
                                    },
                                    onDragCancel = {
                                        swipeOffset = 0f
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        swipeOffset += dragAmount
                                    }
                                )
                            }
                    ) {
                        when (uiState.viewMode) {
                            CalendarViewMode.MONTH -> MonthView(
                                currentDate = uiState.currentDate,
                                events = uiState.events,
                                onDayClick = { date -> viewModel.openCreateEventModal(date) },
                                onEventClick = { event -> viewModel.openEditEventModal(event) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp)
                            )
                            CalendarViewMode.WEEK -> WeekView(
                                currentDate = uiState.currentDate,
                                events = uiState.events,
                                onDayClick = { date -> viewModel.openCreateEventModal(date) },
                                onEventClick = { event -> viewModel.openEditEventModal(event) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp)
                            )
                            CalendarViewMode.DAY -> DayView(
                                currentDate = uiState.currentDate,
                                events = uiState.events,
                                onTimeSlotClick = { time -> viewModel.openCreateEventModal(uiState.currentDate, time) },
                                onEventClick = { event -> viewModel.openEditEventModal(event) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // Tasks panel (slides in from right)
        TasksPanel(
            isOpen = uiState.isTasksPanelOpen,
            tasks = uiState.tasks,
            onClose = { viewModel.toggleTasksPanel() },
            onTaskToggle = { task -> viewModel.toggleTask(task) },
            onAddTask = { /* TODO: Show add task dialog */ },
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // Event modal
        if (uiState.isEventModalOpen) {
            EventModal(
                isCreateMode = uiState.isCreateMode,
                event = uiState.selectedEvent,
                calendars = uiState.calendars,
                initialDate = uiState.eventFormDate,
                initialTime = uiState.eventFormTime,
                onDismiss = { viewModel.closeEventModal() },
                onCreate = { request -> viewModel.createEvent(request) },
                onUpdate = { event, request -> viewModel.updateEvent(event, request) },
                onDelete = { event -> viewModel.deleteEvent(event) }
            )
        }

        // Weather modal
        if (showWeatherModal && uiState.weather != null) {
            WeatherModal(
                weather = uiState.weather!!,
                onDismiss = { showWeatherModal = false }
            )
        }
    }
}

@Composable
private fun WeatherModal(
    weather: Weather,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        WeatherWidgetExpanded(
            weather = weather,
            onDismiss = onDismiss,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun CompactWeatherButton(
    weather: Weather,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val current = weather.current
    val temp = current.temp.toInt()
    val icon = getWeatherIcon(current.icon)
    val iconColor = getWeatherIconColor(current.icon)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = HomeControlColors.cardBorder(),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = current.condition,
            modifier = Modifier.size(18.dp),
            tint = iconColor
        )
        Text(
            text = "$temp°",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// Map weather icon codes to Material icons
private fun getWeatherIcon(iconCode: String): ImageVector {
    return when (iconCode) {
        "sun" -> Icons.Default.WbSunny
        "moon" -> Icons.Default.WbSunny  // Use sunny icon for clear night
        "cloud-sun", "cloud-moon" -> Icons.Default.WbCloudy
        "clouds" -> Icons.Default.Cloud
        "cloud-showers", "cloud-rain" -> Icons.Default.WaterDrop
        "cloud-bolt", "thunderstorm" -> Icons.Default.Thunderstorm
        "snowflake", "snow" -> Icons.Default.Cloud
        "smog", "fog", "mist" -> Icons.Default.Air
        else -> Icons.Default.WbSunny
    }
}

@Composable
private fun getWeatherIconColor(iconCode: String): Color {
    return when (iconCode) {
        "sun" -> Color(0xFFFFB300)  // Sunny yellow
        "moon" -> Color(0xFF90CAF9)  // Moon blue
        "cloud-sun", "cloud-moon" -> Color(0xFF90A4AE)  // Cloudy gray
        "clouds" -> Color(0xFF78909C)  // Darker gray
        "cloud-showers", "cloud-rain" -> Color(0xFF42A5F5)  // Rain blue
        "cloud-bolt", "thunderstorm" -> Color(0xFFFFCA28)  // Storm yellow
        "snowflake", "snow" -> Color(0xFFE0E0E0)  // Snow white
        "smog", "fog", "mist" -> Color(0xFFB0BEC5)  // Mist gray
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun CalendarHeader(
    viewMode: CalendarViewMode,
    currentDate: LocalDate,
    weather: com.homecontrol.sensors.data.model.Weather?,
    calendars: List<Calendar>,
    onViewModeChange: (CalendarViewMode) -> Unit,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    onTodayClick: () -> Unit,
    onTasksClick: () -> Unit,
    onAddEventClick: () -> Unit,
    onWeatherClick: () -> Unit,
    onSmartHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCalendarDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackground()
        ),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        // Single compact row with all controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Smart Home button + View mode toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Smart Home button
                IconButton(
                    onClick = onSmartHomeClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Smart Home",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                CalendarViewMode.entries.forEach { mode ->
                    FilterChip(
                        selected = viewMode == mode,
                        onClick = { onViewModeChange(mode) },
                        label = {
                            Text(
                                text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }

                // Compact weather widget
                if (weather != null) {
                    CompactWeatherButton(
                        weather = weather,
                        onClick = onWeatherClick
                    )
                }
            }

            // Center: Navigation with date
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigatePrev,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = formatHeaderDate(viewMode, currentDate),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = onNavigateNext,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Today button
                if (currentDate != LocalDate.now()) {
                    IconButton(
                        onClick = onTodayClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Today",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Right: Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Calendars dropdown
                if (calendars.isNotEmpty()) {
                    Box {
                        IconButton(
                            onClick = { showCalendarDropdown = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Calendars",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showCalendarDropdown,
                            onDismissRequest = { showCalendarDropdown = false }
                        ) {
                            calendars.forEach { calendar ->
                                CalendarDropdownItem(
                                    calendar = calendar,
                                    onToggle = { /* TODO: Toggle calendar visibility */ }
                                )
                            }
                        }
                    }
                }

                // Add Event
                IconButton(
                    onClick = onAddEventClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Event",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Tasks
                IconButton(
                    onClick = onTasksClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = "Tasks",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDropdownItem(
    calendar: Calendar,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val calendarColor = calendar.backgroundColor?.let { parseHexColor(it) }
        ?: MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = calendar.selected,
            onCheckedChange = { onToggle() }
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Calendar color indicator
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(calendarColor)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = calendar.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        val colorString = hex.removePrefix("#")
        val colorLong = colorString.toLong(16)
        Color(
            red = ((colorLong shr 16) and 0xFF) / 255f,
            green = ((colorLong shr 8) and 0xFF) / 255f,
            blue = (colorLong and 0xFF) / 255f
        )
    } catch (e: Exception) {
        Color.Gray
    }
}

private fun formatHeaderDate(viewMode: CalendarViewMode, date: LocalDate): String {
    return when (viewMode) {
        CalendarViewMode.MONTH -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        CalendarViewMode.WEEK -> {
            val weekStart = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))
            val weekEnd = weekStart.plusDays(6)
            if (weekStart.month == weekEnd.month) {
                "${weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} - ${weekEnd.dayOfMonth}, ${weekEnd.year}"
            } else {
                "${weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} - ${weekEnd.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
            }
        }
        CalendarViewMode.DAY -> date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
    }
}
