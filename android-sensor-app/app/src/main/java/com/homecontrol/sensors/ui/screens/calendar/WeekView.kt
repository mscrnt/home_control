package com.homecontrol.sensors.ui.screens.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homecontrol.sensors.data.model.CalendarEvent
import com.homecontrol.sensors.ui.theme.HomeControlColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class WeekDay(
    val date: LocalDate,
    val dayName: String,
    val dateStr: String,
    val isToday: Boolean,
    val events: List<CalendarEvent>,
    val holidays: List<Holiday> = emptyList()
)

@Composable
fun WeekView(
    currentDate: LocalDate,
    events: List<CalendarEvent>,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val weekDays = remember(currentDate, events) {
        generateWeekDays(currentDate, events)
    }

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        weekDays.forEach { day ->
            WeekDayColumn(
                day = day,
                onClick = { onDayClick(day.date) },
                onEventClick = onEventClick,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun WeekDayColumn(
    day: WeekDay,
    onClick: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackground()
        ),
        border = BorderStroke(
            width = if (day.isToday) 2.dp else 1.dp,
            color = if (day.isToday) MaterialTheme.colorScheme.primary else HomeControlColors.cardBorder()
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Day header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (day.isToday) MaterialTheme.colorScheme.primaryContainer
                        else HomeControlColors.cardBackgroundSolid()
                    )
                    .clickable(onClick = onClick)
                    .padding(12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = day.dayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (day.isToday) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .then(
                                if (day.isToday) {
                                    Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(8.dp)
                                } else {
                                    Modifier.padding(8.dp)
                                }
                            )
                    ) {
                        Text(
                            text = day.dateStr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (day.isToday) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Add event hint
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add event",
                            modifier = Modifier.height(12.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Events list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Show holidays first
                val regularHolidays = day.holidays.filter { !it.isMoonPhase }
                val moonPhases = day.holidays.filter { it.isMoonPhase }

                items(regularHolidays) { holiday ->
                    HolidayCard(holiday = holiday)
                }

                items(moonPhases) { moonPhase ->
                    MoonPhaseCard(moonPhase = moonPhase)
                }

                items(day.events) { event ->
                    WeekEventCard(
                        event = event,
                        onClick = { onEventClick(event) }
                    )
                }

                if (day.events.isEmpty() && day.holidays.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No events",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HolidayCard(
    holiday: Holiday,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF9C27B0).copy(alpha = 0.85f)
        )
    ) {
        Text(
            text = holiday.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MoonPhaseCard(
    moonPhase: Holiday,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = moonPhase.icon ?: "",
            fontSize = 18.sp
        )
        Text(
            text = moonPhase.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WeekEventCard(
    event: CalendarEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eventColor = event.backgroundColor?.let { parseHexColor(it) }
        ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackgroundSolid()
        ),
        border = BorderStroke(2.dp, eventColor)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Time
            if (!event.allDay) {
                val timeStr = formatEventTime(event)
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "All day",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Title
            Text(
                text = event.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Location
            event.location?.takeIf { it.isNotBlank() }?.let { location ->
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun generateWeekDays(currentDate: LocalDate, events: List<CalendarEvent>): List<WeekDay> {
    val weekStart = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val today = LocalDate.now()
    val dayFormatter = DateTimeFormatter.ofPattern("EEE")
    val dateFormatter = DateTimeFormatter.ofPattern("d")

    // Get holidays for the week's year(s)
    val holidaysForYear = Holidays.getUSHolidays(weekStart.year)
    val holidaysNextYear = if (weekStart.plusDays(6).year > weekStart.year)
        Holidays.getUSHolidays(weekStart.year + 1) else emptyList()
    val allHolidays = holidaysForYear + holidaysNextYear

    return (0..6).map { offset ->
        val date = weekStart.plusDays(offset.toLong())
        val dayEvents = events.filter { event ->
            val eventDate = parseEventDate(event)
            eventDate == date
        }.sortedBy { event ->
            if (event.allDay) LocalDateTime.MIN
            else parseEventDateTime(event) ?: LocalDateTime.MAX
        }

        val dayHolidays = allHolidays.filter { it.date == date }

        WeekDay(
            date = date,
            dayName = date.format(dayFormatter),
            dateStr = date.format(dateFormatter),
            isToday = date == today,
            events = dayEvents,
            holidays = dayHolidays
        )
    }
}

private fun formatEventTime(event: CalendarEvent): String {
    val startTime = parseEventDateTime(event)
    return startTime?.format(timeFormatter) ?: ""
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
            LocalDate.parse(cleanDtStr).atStartOfDay()
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
