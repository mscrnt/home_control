package com.homecontrol.sensors.ui.screens.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homecontrol.sensors.data.model.CalendarEvent
import com.homecontrol.sensors.ui.theme.HomeControlColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HOUR_HEIGHT = 60.dp
private val TIME_COLUMN_WIDTH = 56.dp

@Composable
fun DayView(
    currentDate: LocalDate,
    events: List<CalendarEvent>,
    onTimeSlotClick: (LocalTime) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val currentHour = LocalTime.now().hour

    // Scroll to current time on first load
    LaunchedEffect(Unit) {
        val scrollToHour = (currentHour - 1).coerceIn(0, 23)
        listState.animateScrollToItem(scrollToHour)
    }

    // Filter events to only show those on the selected date
    val dayEvents = remember(events, currentDate) {
        events.filter { event ->
            val eventDate = parseEventDate(event)
            eventDate == currentDate
        }
    }

    val allDayEvents = remember(dayEvents) {
        dayEvents.filter { it.allDay }
    }

    val timedEvents = remember(dayEvents) {
        dayEvents.filter { !it.allDay }
    }

    // Get holidays for the current date
    val holidays = remember(currentDate) {
        Holidays.getUSHolidays(currentDate.year).filter { it.date == currentDate }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Holidays and all-day events section
        val regularHolidays = holidays.filter { !it.isMoonPhase }
        val moonPhases = holidays.filter { it.isMoonPhase }

        if (regularHolidays.isNotEmpty() || moonPhases.isNotEmpty() || allDayEvents.isNotEmpty()) {
            AllDaySection(
                events = allDayEvents,
                holidays = regularHolidays,
                moonPhases = moonPhases,
                onEventClick = onEventClick
            )
            HorizontalDivider(color = HomeControlColors.cardBorder())
        }

        // Timeline with overlaid events
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(24) { hour ->
                    HourRow(
                        hour = hour,
                        onTimeSlotClick = { onTimeSlotClick(LocalTime.of(hour, 0)) }
                    )
                }
            }

            // Events overlay - positioned absolutely over the grid
            EventsOverlay(
                events = timedEvents,
                listState = listState,
                hourHeight = HOUR_HEIGHT,
                onEventClick = onEventClick,
                onTimeSlotClick = onTimeSlotClick
            )

            // Current time indicator - positioned relative to scroll
            if (currentDate == LocalDate.now()) {
                CurrentTimeIndicator(listState = listState)
            }
        }
    }
}

@Composable
private fun AllDaySection(
    events: List<CalendarEvent>,
    holidays: List<Holiday>,
    moonPhases: List<Holiday>,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackground()
        ),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Holidays row
            if (holidays.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    holidays.forEachIndexed { index, holiday ->
                        if (index > 0) Spacer(modifier = Modifier.width(8.dp))
                        HolidayChip(holiday = holiday)
                    }
                }
                if (moonPhases.isNotEmpty() || events.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Moon phases row
            if (moonPhases.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    moonPhases.forEachIndexed { index, moonPhase ->
                        if (index > 0) Spacer(modifier = Modifier.width(8.dp))
                        MoonPhaseChip(moonPhase = moonPhase)
                    }
                }
                if (events.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // All-day events
            if (events.isNotEmpty()) {
                Text(
                    text = "All Day",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(events) { event ->
                        AllDayEventChip(
                            event = event,
                            onClick = { onEventClick(event) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HolidayChip(
    holiday: Holiday,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF9C27B0).copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = holiday.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Composable
private fun MoonPhaseChip(
    moonPhase: Holiday,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = moonPhase.icon ?: "",
            fontSize = 16.sp
        )
        Text(
            text = moonPhase.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AllDayEventChip(
    event: CalendarEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eventColor = event.backgroundColor?.let { parseHexColor(it) }
        ?: MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(eventColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = event.summary,
            style = MaterialTheme.typography.bodySmall,
            color = event.foregroundColor?.let { parseHexColor(it) } ?: Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HourRow(
    hour: Int,
    onTimeSlotClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HOUR_HEIGHT)
    ) {
        // Time label
        Box(
            modifier = Modifier
                .width(TIME_COLUMN_WIDTH)
                .fillMaxHeight()
                .padding(end = 8.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Text(
                text = formatHour(hour),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Hour slot
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onTimeSlotClick)
        ) {
            // Hour line
            HorizontalDivider(
                modifier = Modifier.align(Alignment.TopStart),
                color = HomeControlColors.cardBorder().copy(alpha = 0.5f)
            )
        }
    }
}

// Data class to hold event layout information
private data class EventLayout(
    val event: CalendarEvent,
    val startMinutes: Int,
    val endMinutes: Int,
    val column: Int,
    val totalColumns: Int
)

// Calculate column assignments for overlapping events
private fun calculateEventColumns(events: List<CalendarEvent>): List<EventLayout> {
    // Parse and sort events by start time
    val parsedEvents = events.mapNotNull { event ->
        val startTime = parseEventDateTime(event)
        val endTime = parseEventEndDateTime(event)
        if (startTime != null && endTime != null) {
            Triple(event, startTime.hour * 60 + startTime.minute,
                   (endTime.hour * 60 + endTime.minute).coerceAtLeast(startTime.hour * 60 + startTime.minute + 30))
        } else null
    }.sortedBy { it.second }

    if (parsedEvents.isEmpty()) return emptyList()

    // Track which columns are occupied and until when
    val columnEndTimes = mutableListOf<Int>()
    val eventColumns = mutableListOf<Triple<CalendarEvent, Int, Int>>() // event, startMinutes, endMinutes, column
    val eventColumnAssignments = mutableMapOf<CalendarEvent, Int>()

    for ((event, startMinutes, endMinutes) in parsedEvents) {
        // Find first available column (where the event ends before our start)
        var assignedColumn = -1
        for (col in columnEndTimes.indices) {
            if (columnEndTimes[col] <= startMinutes) {
                assignedColumn = col
                columnEndTimes[col] = endMinutes
                break
            }
        }

        // If no column available, create new one
        if (assignedColumn == -1) {
            assignedColumn = columnEndTimes.size
            columnEndTimes.add(endMinutes)
        }

        eventColumnAssignments[event] = assignedColumn
        eventColumns.add(Triple(event, startMinutes, endMinutes))
    }

    // Now determine total columns for each event based on overlapping events
    return eventColumns.map { (event, startMinutes, endMinutes) ->
        // Find all events that overlap with this one
        val overlappingEvents = eventColumns.filter { (otherEvent, otherStart, otherEnd) ->
            // Two events overlap if one starts before the other ends
            startMinutes < otherEnd && endMinutes > otherStart
        }

        // Get the max column used by overlapping events + 1 = total columns
        val totalColumns = overlappingEvents.maxOfOrNull { (e, _, _) ->
            eventColumnAssignments[e] ?: 0
        }?.plus(1) ?: 1

        EventLayout(
            event = event,
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            column = eventColumnAssignments[event] ?: 0,
            totalColumns = totalColumns
        )
    }
}

@Composable
private fun EventsOverlay(
    events: List<CalendarEvent>,
    listState: LazyListState,
    hourHeight: Dp,
    onEventClick: (CalendarEvent) -> Unit,
    onTimeSlotClick: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Calculate scroll offset
    val scrollOffsetPx = with(density) {
        listState.firstVisibleItemIndex * hourHeight.toPx() +
            listState.firstVisibleItemScrollOffset
    }

    val hourHeightPx = with(density) { hourHeight.toPx() }

    // Calculate event column layouts
    val eventLayouts = remember(events) {
        calculateEventColumns(events)
    }

    // Pre-calculate event bounds for tap detection (include X bounds for columns)
    data class EventBounds(
        val event: CalendarEvent,
        val topPx: Float,
        val bottomPx: Float,
        val leftFraction: Float,
        val rightFraction: Float
    )

    val eventBounds = remember(eventLayouts, scrollOffsetPx) {
        eventLayouts.map { layout ->
            val absolutePositionPx = with(density) { hourHeight.toPx() * layout.startMinutes / 60f }
            val relativePositionPx = absolutePositionPx - scrollOffsetPx
            val durationMinutes = (layout.endMinutes - layout.startMinutes).coerceAtLeast(30)
            val heightPx = with(density) { hourHeight.toPx() * durationMinutes / 60f }

            val columnWidth = 1f / layout.totalColumns
            EventBounds(
                event = layout.event,
                topPx = relativePositionPx,
                bottomPx = relativePositionPx + heightPx,
                leftFraction = layout.column * columnWidth,
                rightFraction = (layout.column + 1) * columnWidth
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = TIME_COLUMN_WIDTH)
            .clipToBounds()
            .pointerInput(listState) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    coroutineScope.launch {
                        listState.scroll {
                            scrollBy(-dragAmount)
                        }
                    }
                }
            }
            .pointerInput(eventBounds, scrollOffsetPx, hourHeightPx) {
                detectTapGestures { offset ->
                    // Find which event was tapped (check both Y and X bounds)
                    val containerWidth = size.width.toFloat()
                    val tappedEvent = eventBounds.find { bounds ->
                        val leftPx = bounds.leftFraction * containerWidth
                        val rightPx = bounds.rightFraction * containerWidth
                        offset.y >= bounds.topPx && offset.y <= bounds.bottomPx &&
                        offset.x >= leftPx && offset.x <= rightPx
                    }
                    if (tappedEvent != null) {
                        onEventClick(tappedEvent.event)
                    } else {
                        // Tapped on empty space - calculate the time
                        val absoluteY = offset.y + scrollOffsetPx
                        val totalMinutes = (absoluteY / hourHeightPx * 60).toInt().coerceIn(0, 24 * 60 - 1)
                        val hour = totalMinutes / 60
                        val minute = (totalMinutes % 60 / 15) * 15 // Round to nearest 15 min
                        onTimeSlotClick(LocalTime.of(hour, minute))
                    }
                }
            }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val containerWidth = maxWidth

            eventLayouts.forEach { layout ->
                val durationMinutes = (layout.endMinutes - layout.startMinutes).coerceAtLeast(30)

                // Calculate position and height
                val absolutePositionPx = with(density) { hourHeight.toPx() * layout.startMinutes / 60f }
                val relativePositionPx = absolutePositionPx - scrollOffsetPx
                val heightPx = with(density) { hourHeight.toPx() * durationMinutes / 60f }

                val relativePositionDp = with(density) { relativePositionPx.toDp() }
                val heightDp = with(density) { heightPx.toDp() }

                // Calculate width and horizontal offset based on column
                val columnWidth = containerWidth / layout.totalColumns
                val horizontalOffset = columnWidth * layout.column

                TimelineEventCard(
                    event = layout.event,
                    modifier = Modifier
                        .offset(x = horizontalOffset, y = relativePositionDp)
                        .width(columnWidth - 2.dp) // Small gap between columns
                        .height(heightDp.coerceAtLeast(24.dp))
                )
            }
        }
    }
}

@Composable
private fun TimelineEventCard(
    event: CalendarEvent,
    modifier: Modifier = Modifier
) {
    val eventColor = event.backgroundColor?.let { parseHexColor(it) }
        ?: MaterialTheme.colorScheme.primary

    val startTime = parseEventDateTime(event)
    val endTime = parseEventEndDateTime(event)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = eventColor.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Time
            if (startTime != null && endTime != null) {
                Text(
                    text = "${startTime.format(timeFormatter)} - ${endTime.format(timeFormatter)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = event.foregroundColor?.let { parseHexColor(it) }?.copy(alpha = 0.8f)
                        ?: Color.White.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }

            // Title
            Text(
                text = event.summary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = event.foregroundColor?.let { parseHexColor(it) } ?: Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Location
            event.location?.takeIf { it.isNotBlank() }?.let { location ->
                Text(
                    text = location,
                    style = MaterialTheme.typography.labelSmall,
                    color = event.foregroundColor?.let { parseHexColor(it) }?.copy(alpha = 0.7f)
                        ?: Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CurrentTimeIndicator(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val currentTime = LocalTime.now()
    val hourFraction = currentTime.hour + currentTime.minute / 60f
    val density = LocalDensity.current

    // Calculate the absolute position of the indicator in the full scrollable content
    val absolutePositionPx = with(density) { HOUR_HEIGHT.toPx() * hourFraction }

    // Calculate the scroll offset
    val scrollOffsetPx = with(density) {
        listState.firstVisibleItemIndex * HOUR_HEIGHT.toPx() +
            listState.firstVisibleItemScrollOffset
    }

    // Position relative to current viewport
    val relativePositionDp = with(density) { (absolutePositionPx - scrollOffsetPx).toDp() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = relativePositionDp)
            .padding(start = TIME_COLUMN_WIDTH - 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Red dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Red)
        )

        // Red line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(Color.Red)
        )
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun formatHour(hour: Int): String {
    return when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}

private fun parseEventStartHour(event: CalendarEvent): Int? {
    return try {
        val dtStr = event.start.substringBefore("+")
        val cleanDtStr = dtStr.replace(Regex("-\\d{2}:\\d{2}$"), "")
        if (cleanDtStr.contains("T")) {
            LocalDateTime.parse(cleanDtStr).hour
        } else {
            0 // All-day events start at midnight
        }
    } catch (e: Exception) {
        null
    }
}

private fun parseEventDate(event: CalendarEvent): LocalDate? {
    return try {
        val dtStr = event.start.substringBefore("+")
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

private fun parseEventDateTime(event: CalendarEvent): LocalDateTime? {
    return try {
        val dtStr = event.start.substringBefore("+")
        val cleanDtStr = dtStr.replace(Regex("-\\d{2}:\\d{2}$"), "")
        if (cleanDtStr.contains("T")) {
            LocalDateTime.parse(cleanDtStr)
        } else {
            java.time.LocalDate.parse(cleanDtStr).atStartOfDay()
        }
    } catch (e: Exception) {
        null
    }
}

private fun parseEventEndDateTime(event: CalendarEvent): LocalDateTime? {
    return try {
        val dtStr = event.end.substringBefore("+")
        val cleanDtStr = dtStr.replace(Regex("-\\d{2}:\\d{2}$"), "")
        if (cleanDtStr.contains("T")) {
            LocalDateTime.parse(cleanDtStr)
        } else {
            java.time.LocalDate.parse(cleanDtStr).atStartOfDay()
        }
    } catch (e: Exception) {
        null
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
