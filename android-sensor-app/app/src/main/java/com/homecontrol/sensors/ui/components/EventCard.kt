package com.homecontrol.sensors.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homecontrol.sensors.data.model.CalendarEvent
import com.homecontrol.sensors.ui.theme.HomeControlColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun EventCard(
    event: CalendarEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false
) {
    val eventColor = event.backgroundColor?.let { parseHexColor(it) }
        ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackgroundSolid()
        ),
        border = BorderStroke(2.dp, eventColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(eventColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = event.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Time
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatEventTime(event, showDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Recurring indicator
                    if (event.recurringEventId != null || !event.recurrence.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Recurring",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Location
                if (!event.location.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = event.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EventCardCompact(
    event: CalendarEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eventColor = event.backgroundColor?.let { parseHexColor(it) }
        ?: MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(eventColor.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
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
fun EventCardTimeline(
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
            containerColor = eventColor.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Time
            val startTime = parseDateTime(event.start)
            val endTime = parseDateTime(event.end)

            if (!event.allDay && startTime != null && endTime != null) {
                Text(
                    text = "${startTime.format(timeFormatter)} - ${endTime.format(timeFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = event.foregroundColor?.let { parseHexColor(it) } ?: Color.White.copy(alpha = 0.8f)
                )
            }

            // Title
            Text(
                text = event.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = event.foregroundColor?.let { parseHexColor(it) } ?: Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Location
            if (!event.location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = event.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = event.foregroundColor?.let { parseHexColor(it) }?.copy(alpha = 0.8f)
                        ?: Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

private fun formatEventTime(event: CalendarEvent, showDate: Boolean): String {
    if (event.allDay) {
        return "All day"
    }

    val startDateTime = parseDateTime(event.start)
    val endDateTime = parseDateTime(event.end)

    return if (startDateTime != null && endDateTime != null) {
        if (showDate) {
            "${startDateTime.format(dateTimeFormatter)} - ${endDateTime.format(timeFormatter)}"
        } else {
            "${startDateTime.format(timeFormatter)} - ${endDateTime.format(timeFormatter)}"
        }
    } else {
        "All day"
    }
}

private fun parseDateTime(dateTimeStr: String): LocalDateTime? {
    return try {
        // Handle ISO datetime with timezone offset like "2025-12-29T00:00:00-08:00"
        val dtStr = dateTimeStr.substringBefore("+")
        val cleanDtStr = dtStr.replace(Regex("-\\d{2}:\\d{2}$"), "")
        if (cleanDtStr.contains("T")) {
            LocalDateTime.parse(cleanDtStr)
        } else {
            // Date-only string like "2025-12-29"
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
