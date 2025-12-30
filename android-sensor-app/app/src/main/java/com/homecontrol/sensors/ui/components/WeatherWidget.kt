package com.homecontrol.sensors.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.homecontrol.sensors.data.model.DailyWeather
import com.homecontrol.sensors.data.model.HourlyWeather
import com.homecontrol.sensors.data.model.Weather
import com.homecontrol.sensors.ui.theme.HomeControlColors
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun WeatherWidget(
    weather: Weather?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (weather == null) return

    val current = weather.current
    val temp = current.temp.roundToInt()
    val icon = getWeatherIcon(current.icon)
    val iconColor = getWeatherIconColor(current.icon)

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackground()
        ),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = current.condition,
                modifier = Modifier.size(if (compact) 24.dp else 32.dp),
                tint = iconColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "$temp°",
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!compact) {
                    Text(
                        text = current.condition,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherWidgetExpanded(
    weather: Weather,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val current = weather.current
    val scrollState = rememberScrollState()

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackgroundSolid()
        ),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with title and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(40.dp)) // Balance the close button
                Text(
                    text = "Weather",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(40.dp))
                }
            }

            // Main weather card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HomeControlColors.cardBackgroundSolid()
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Large weather icon and temp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Use Google Weather icon if available, fallback to emoji
                        if (current.iconUri.isNotEmpty()) {
                            AsyncImage(
                                model = "${current.iconUri}.png",
                                contentDescription = current.condition,
                                modifier = Modifier.size(72.dp)
                            )
                        } else {
                            Text(
                                text = getWeatherEmoji(current.icon),
                                fontSize = 64.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "${current.temp.roundToInt()}°",
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = current.condition,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Detail cards row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WeatherDetailCard(
                            icon = "🌡️",
                            value = "${current.feelsLike.roundToInt()}°",
                            label = "FEELS LIKE",
                            modifier = Modifier.weight(1f)
                        )
                        WeatherDetailCard(
                            icon = "💧",
                            value = "${current.humidity}%",
                            label = "HUMIDITY",
                            modifier = Modifier.weight(1f)
                        )
                        WeatherDetailCard(
                            icon = "💨",
                            value = "${current.windSpeed.roundToInt()}",
                            label = "MPH",
                            modifier = Modifier.weight(1f)
                        )
                        WeatherDetailCard(
                            icon = "☁️",
                            value = "${current.clouds}%",
                            label = "CLOUDS",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sunrise, Sunset, Moon Phase row
                    // Use today's daily forecast sunrise/sunset as they are more reliable
                    val today = weather.daily.firstOrNull()
                    val sunriseTime = today?.sunrise?.takeIf { it > 0 } ?: current.sunrise
                    val sunsetTime = today?.sunset?.takeIf { it > 0 } ?: current.sunset

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sunrise
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "🌅", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatTime(sunriseTime),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Sunset
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "🌇", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatTime(sunsetTime),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Moon phase (approximate based on date)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = getMoonPhaseEmoji(), fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = getMoonPhaseName(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // HOURLY FORECAST - 12 hour forecast with horizontal scroll, centered
            if (weather.hourly.isNotEmpty()) {
                val hourlyScrollState = rememberScrollState()
                // Get sunrise/sunset for determining night hours - prefer daily, fallback to current
                val today = weather.daily.firstOrNull()
                val sunrise = today?.sunrise?.takeIf { it > 0 } ?: current.sunrise
                val sunset = today?.sunset?.takeIf { it > 0 } ?: current.sunset
                // Filter to only future hours and take next 12
                val currentTime = System.currentTimeMillis() / 1000
                val futureHours = weather.hourly.filter { it.time > currentTime }.take(12)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "HOURLY FORECAST",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(hourlyScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        futureHours.forEach { hour ->
                            HourlyForecastCard(
                                hourly = hour,
                                sunrise = sunrise,
                                sunset = sunset
                            )
                        }
                    }
                }
            }

            // 10-DAY FORECAST with horizontal scroll, centered
            if (weather.daily.isNotEmpty()) {
                val dailyScrollState = rememberScrollState()
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "10-DAY FORECAST",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(dailyScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        weather.daily.take(10).forEachIndexed { index, day ->
                            DailyForecastCard(daily = day, isToday = index == 0)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherDetailCard(
    icon: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackground()
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 16.sp)
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HourlyForecastCard(
    hourly: HourlyWeather,
    sunrise: Long = 0L,
    sunset: Long = 0L,
    modifier: Modifier = Modifier
) {
    // Determine if this hour is nighttime based on sunrise/sunset
    // Convert to LocalTime for comparison (time of day only)
    val hourLocalTime = Instant.ofEpochSecond(hourly.time)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
    val sunriseTime = if (sunrise > 0) {
        Instant.ofEpochSecond(sunrise)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
    } else null
    val sunsetTime = if (sunset > 0) {
        Instant.ofEpochSecond(sunset)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
    } else null

    val isNight = if (sunriseTime != null && sunsetTime != null) {
        // Night = before sunrise OR at/after sunset
        hourLocalTime.isBefore(sunriseTime) || !hourLocalTime.isBefore(sunsetTime)
    } else {
        false
    }

    // Override icon for clear conditions at night
    val effectiveIcon = if (isNight && (hourly.icon == "sun" || hourly.icon == "cloud-sun")) {
        if (hourly.icon == "sun") "moon" else "cloud-moon"
    } else {
        hourly.icon
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackgroundSolid()
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = formatHour(hourly.time),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Use Google Weather icon if available, fallback to emoji
            if (hourly.iconUri.isNotEmpty()) {
                AsyncImage(
                    model = "${hourly.iconUri}.png",
                    contentDescription = hourly.condition,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Text(
                    text = getWeatherEmoji(effectiveIcon, hourly.time),
                    fontSize = 28.sp
                )
            }
            Text(
                text = "${hourly.temp.roundToInt()}°",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DailyForecastCard(
    daily: DailyWeather,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackgroundSolid()
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isToday) "Today" else formatDayWithDate(daily.time),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Use Google Weather icon if available, fallback to emoji
            if (daily.iconUri.isNotEmpty()) {
                AsyncImage(
                    model = "${daily.iconUri}.png",
                    contentDescription = daily.condition,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Text(
                    text = getWeatherEmoji(daily.icon),
                    fontSize = 28.sp
                )
            }
            // Show precipitation if > 0
            if (daily.pop > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(text = "💧", fontSize = 10.sp)
                    Text(
                        text = "${(daily.pop * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF42A5F5)
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${daily.tempMax.roundToInt()}°",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${daily.tempMin.roundToInt()}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeatherDetail(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Get weather emoji based on icon code
// For nighttime clear ("moon" icon), use actual moon phase if timestamp provided
private fun getWeatherEmoji(iconCode: String, timestamp: Long = 0): String {
    return when (iconCode) {
        "sun" -> "☀️"
        "moon" -> {
            // Show actual moon phase for clear nighttime
            if (timestamp > 0) {
                getMoonPhaseEmojiForTime(timestamp)
            } else {
                getMoonPhaseEmoji()
            }
        }
        "cloud-sun" -> "⛅"
        "cloud-moon" -> "☁️"
        "cloud", "clouds" -> "☁️"
        "cloud-showers", "cloud-rain" -> "🌧️"
        "cloud-bolt", "bolt", "thunderstorm" -> "⛈️"
        "snowflake", "snow" -> "❄️"
        "smog", "fog", "mist" -> "🌫️"
        "wind" -> "💨"
        else -> "☁️" // Default to clouds instead of sun
    }
}

// Format Unix timestamp to time string (e.g., "6:54 AM")
private fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return "--"
    return try {
        val instant = Instant.ofEpochSecond(timestamp)
        val formatter = DateTimeFormatter.ofPattern("h:mm a")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        "--"
    }
}

// Format Unix timestamp to hour string (e.g., "4 PM")
private fun formatHour(timestamp: Long): String {
    if (timestamp == 0L) return "--"
    return try {
        val instant = Instant.ofEpochSecond(timestamp)
        val formatter = DateTimeFormatter.ofPattern("h a")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        "--"
    }
}

// Format Unix timestamp to day name (e.g., "Mon")
private fun formatDayName(timestamp: Long): String {
    if (timestamp == 0L) return "--"
    return try {
        val instant = Instant.ofEpochSecond(timestamp)
        val formatter = DateTimeFormatter.ofPattern("EEE")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        "--"
    }
}

// Format Unix timestamp to day with date (e.g., "Mon 12/30")
private fun formatDayWithDate(timestamp: Long): String {
    if (timestamp == 0L) return "--"
    return try {
        val instant = Instant.ofEpochSecond(timestamp)
        val formatter = DateTimeFormatter.ofPattern("EEE M/d")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        "--"
    }
}

// Simple moon phase calculation
private fun getMoonPhaseEmoji(): String {
    val now = java.time.LocalDate.now()
    return getMoonPhaseEmojiForDate(now)
}

// Moon phase calculation for a specific Unix timestamp
private fun getMoonPhaseEmojiForTime(timestamp: Long): String {
    val date = Instant.ofEpochSecond(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return getMoonPhaseEmojiForDate(date)
}

// Core moon phase calculation
private fun getMoonPhaseEmojiForDate(date: java.time.LocalDate): String {
    val referenceNewMoon = java.time.LocalDate.of(2000, 1, 6)
    val daysSinceReference = date.toEpochDay() - referenceNewMoon.toEpochDay()
    val synodicMonth = 29.53058867
    val phase = (daysSinceReference % synodicMonth) / synodicMonth
    val normalizedPhase = if (phase < 0) phase + 1 else phase

    return when {
        normalizedPhase < 0.03 || normalizedPhase > 0.97 -> "🌑"
        normalizedPhase < 0.22 -> "🌒"
        normalizedPhase < 0.28 -> "🌓"
        normalizedPhase < 0.47 -> "🌔"
        normalizedPhase < 0.53 -> "🌕"
        normalizedPhase < 0.72 -> "🌖"
        normalizedPhase < 0.78 -> "🌗"
        else -> "🌘"
    }
}

private fun getMoonPhaseName(): String {
    val now = java.time.LocalDate.now()
    val referenceNewMoon = java.time.LocalDate.of(2000, 1, 6)
    val daysSinceReference = now.toEpochDay() - referenceNewMoon.toEpochDay()
    val synodicMonth = 29.53058867
    val phase = (daysSinceReference % synodicMonth) / synodicMonth
    val normalizedPhase = if (phase < 0) phase + 1 else phase

    return when {
        normalizedPhase < 0.03 || normalizedPhase > 0.97 -> "New Moon"
        normalizedPhase < 0.22 -> "Waxing Crescent"
        normalizedPhase < 0.28 -> "First Quarter"
        normalizedPhase < 0.47 -> "Waxing Gibbous"
        normalizedPhase < 0.53 -> "Full Moon"
        normalizedPhase < 0.72 -> "Waning Gibbous"
        normalizedPhase < 0.78 -> "Last Quarter"
        else -> "Waning Crescent"
    }
}

// Map weather icon codes to Material icons
private fun getWeatherIcon(iconCode: String): ImageVector {
    return when (iconCode) {
        "sun" -> Icons.Default.WbSunny
        "moon" -> Icons.Default.WbSunny
        "cloud-sun", "cloud-moon" -> Icons.Default.WbCloudy
        "cloud", "clouds" -> Icons.Default.Cloud
        "cloud-showers", "cloud-rain" -> Icons.Default.WaterDrop
        "cloud-bolt", "bolt", "thunderstorm" -> Icons.Default.Thunderstorm
        "snowflake", "snow" -> Icons.Default.Cloud
        "smog", "fog", "mist" -> Icons.Default.Air
        "wind" -> Icons.Default.Air
        else -> Icons.Default.Cloud // Default to cloud instead of sun
    }
}

@Composable
private fun getWeatherIconColor(iconCode: String): Color {
    return when (iconCode) {
        "sun" -> Color(0xFFFFB300)
        "moon" -> Color(0xFF90CAF9)
        "cloud-sun", "cloud-moon" -> Color(0xFF90A4AE)
        "cloud", "clouds" -> Color(0xFF78909C)
        "cloud-showers", "cloud-rain" -> Color(0xFF42A5F5)
        "cloud-bolt", "bolt", "thunderstorm" -> Color(0xFFFFCA28)
        "snowflake", "snow" -> Color(0xFFE0E0E0)
        "smog", "fog", "mist" -> Color(0xFFB0BEC5)
        "wind" -> Color(0xFF90A4AE)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
