package com.homecontrol.sensors.ui.screens.calendar

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.PI
import kotlin.math.cos

data class MonthDay(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val events: List<CalendarEvent>,
    val moonPhase: Double? = null,
    val holidays: List<Holiday> = emptyList()
)

@Composable
fun MonthView(
    currentDate: LocalDate,
    events: List<CalendarEvent>,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val yearMonth = YearMonth.from(currentDate)
    val days = remember(yearMonth, events) {
        generateMonthDays(yearMonth, events)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Weekday headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Fixed grid that fills available space (not scrollable)
        val weeks = days.chunked(7)
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            weeks.forEach { weekDays ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)  // Each week row takes equal space
                ) {
                    weekDays.forEach { day ->
                        MonthDayCell(
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
        }
    }
}

@Composable
private fun MonthDayCell(
    day: MonthDay,
    onClick: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    // Use surfaceVariant for non-current month to create stronger visual distinction
    val backgroundColor = when {
        day.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        !day.inCurrentMonth -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    val textColor = when {
        !day.inCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        day.isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Dim events on non-current month days
    val eventAlpha = if (day.inCurrentMonth) 0.9f else 0.4f

    val regularHolidays = day.holidays.filter { !it.isMoonPhase }
    val moonPhases = day.holidays.filter { it.isMoonPhase }

    Box(
        modifier = modifier
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = if (day.isToday) MaterialTheme.colorScheme.primary else HomeControlColors.cardBorder(),
            )
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header row: Day number | Holiday | Moon phase - fixed height for alignment
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Day number
                Text(
                    text = day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = textColor
                )

                // Holiday name (in the middle, takes remaining space)
                if (regularHolidays.isNotEmpty()) {
                    Text(
                        text = regularHolidays.first().name,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF9C27B0).copy(alpha = 0.85f))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Moon phase icon (right side) - always reserve space
                Text(
                    text = moonPhases.firstOrNull()?.icon ?: "",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 2.dp)
                )
            }

            // Events (show up to 3)
            val visibleEvents = day.events.take(3)
            val moreCount = day.events.size - visibleEvents.size

            visibleEvents.forEach { event ->
                EventDot(
                    event = event,
                    onClick = { onEventClick(event) },
                    alpha = eventAlpha
                )
            }

            if (moreCount > 0) {
                Text(
                    text = "+$moreCount",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun EventDot(
    event: CalendarEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alpha: Float = 0.9f
) {
    val eventColor = event.backgroundColor?.let { parseHexColor(it) }
        ?: MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(eventColor.copy(alpha = alpha))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = event.summary,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = (event.foregroundColor?.let { parseHexColor(it) } ?: Color.White).copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MoonPhaseIndicator(
    phase: Double,
    modifier: Modifier = Modifier
) {
    // Only show for notable phases (new, first quarter, full, last quarter)
    val phaseIcon = when {
        phase < 0.03 || phase > 0.97 -> "\uD83C\uDF11" // New moon
        phase in 0.22..0.28 -> "\uD83C\uDF13" // First quarter
        phase in 0.47..0.53 -> "\uD83C\uDF15" // Full moon
        phase in 0.72..0.78 -> "\uD83C\uDF17" // Last quarter
        else -> null
    }

    phaseIcon?.let {
        Text(
            text = it,
            fontSize = 12.sp,
            modifier = modifier
        )
    }
}

private fun generateMonthDays(yearMonth: YearMonth, events: List<CalendarEvent>): List<MonthDay> {
    val days = mutableListOf<MonthDay>()
    val today = LocalDate.now()

    // Get first day of month grid (may be in previous month)
    val firstDayOfMonth = yearMonth.atDay(1)
    val firstDayOfGrid = firstDayOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    // Get last day of month grid (may be in next month)
    val lastDayOfMonth = yearMonth.atEndOfMonth()
    val lastDayOfGrid = lastDayOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))

    // Get holidays for the year(s) that might be visible
    val holidaysCurrentYear = Holidays.getUSHolidays(yearMonth.year)
    val holidaysPrevYear = if (firstDayOfGrid.year < yearMonth.year) Holidays.getUSHolidays(yearMonth.year - 1) else emptyList()
    val holidaysNextYear = if (lastDayOfGrid.year > yearMonth.year) Holidays.getUSHolidays(yearMonth.year + 1) else emptyList()
    val allHolidays = holidaysCurrentYear + holidaysPrevYear + holidaysNextYear

    var currentDay = firstDayOfGrid
    while (!currentDay.isAfter(lastDayOfGrid)) {
        val dayEvents = events.filter { event ->
            val eventDate = parseEventDate(event)
            eventDate == currentDay
        }

        // Get holidays for this day
        val dayHolidays = allHolidays.filter { it.date == currentDay }

        // Calculate approximate moon phase (simplified)
        val moonPhase = calculateMoonPhase(currentDay)

        days.add(
            MonthDay(
                date = currentDay,
                inCurrentMonth = YearMonth.from(currentDay) == yearMonth,
                isToday = currentDay == today,
                events = dayEvents,
                moonPhase = moonPhase,
                holidays = dayHolidays
            )
        )
        currentDay = currentDay.plusDays(1)
    }

    return days
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

// Simplified moon phase calculation
// Returns value from 0 (new moon) to 1 (back to new moon)
private fun calculateMoonPhase(date: LocalDate): Double {
    // Reference new moon: January 6, 2000
    val referenceNewMoon = LocalDate.of(2000, 1, 6)
    val daysSinceReference = date.toEpochDay() - referenceNewMoon.toEpochDay()

    // Synodic month is approximately 29.53 days
    val synodicMonth = 29.53058867
    val phase = (daysSinceReference % synodicMonth) / synodicMonth

    return if (phase < 0) phase + 1 else phase
}
