package com.homecontrol.sensors.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
// Colors matching web UI CSS variables
// ============================================

// Primary colors - Weldon Blue accent (matching --accent)
val Primary = Color(0xFF8197AC)  // Weldon Blue
val PrimaryVariant = Color(0xFF9AADBC)  // Lighter Weldon Blue (--accent-hover)
val OnPrimary = Color(0xFF0E141C)  // Dark background for contrast

// Secondary colors
val Secondary = Color(0xFF607EA2)  // Rackley (--info)
val SecondaryVariant = Color(0xFF314B6E)  // --bg-tertiary
val OnSecondary = Color(0xFFBDB3A3)  // Tan text

// Error colors (matching --danger)
val Error = Color(0xFFC45B5B)  // Muted red
val OnError = Color.White

// ============================================
// Light theme colors (Sand Tan theme)
// ============================================
val LightBackground = Color(0xFFE1B382)  // Sand Tan (--bg-primary without alpha)
val LightSurface = Color(0xFFF5EBE0)  // Light cream (--bg-secondary)
val LightSurfaceVariant = Color(0xFFECDCC8)  // Warm beige (--bg-tertiary)
val LightOnBackground = Color(0xFF12343B)  // Night Blue Shadow (--text-primary)
val LightOnSurface = Color(0xFF2D545E)  // Night Blue (--text-secondary)
val LightOnSurfaceVariant = Color(0xFF5A7A82)  // Muted blue-grey (--text-muted)
val LightOutline = Color(0xFFC89666)  // Sand Tan Shadow (--border)
val LightCardBackground = Color(0xE6F5EBE0)  // Cream with 90% opacity

// ============================================
// Dark theme colors (Navy Glassmorphism theme)
// ============================================
val DarkBackground = Color(0xFF0E141C)  // --bg-primary (dark navy)
val DarkSurface = Color(0xFF1A2332)  // --bg-secondary
val DarkSurfaceVariant = Color(0xFF1E2D42)  // --bg-elevated
val DarkOnBackground = Color(0xFFBDB3A3)  // --text-primary (tan/beige)
val DarkOnSurface = Color(0xFFBDB3A3)  // --text-primary
val DarkOnSurfaceVariant = Color(0xFF8197AC)  // --text-secondary (Weldon Blue)
val DarkOutline = Color(0xFF314B6E)  // --border
val DarkCardBackground = Color(0x661A2332)  // --bg-secondary with 40% opacity for glass effect

// Additional dark theme colors
val DarkTertiary = Color(0xFF314B6E)  // --bg-tertiary
val DarkHover = Color(0xFF607EA2)  // --bg-hover
val DarkTextMuted = Color(0xFF607EA2)  // --text-muted

// Entity state colors (matching web UI --success, --danger)
val StateOn = Color(0xFF7A9E8A)  // Muted teal-green (--success)
val StateOff = Color(0xFF607EA2)  // Rackley (--text-muted)
val StateUnavailable = Color(0xFFC45B5B)  // Muted red (--danger)

// Climate colors
val ClimateHeating = Color(0xFFC45B5B)  // Muted red (matches --danger)
val ClimateCooling = Color(0xFF8197AC)  // Weldon Blue (matches --accent)
val ClimateIdle = Color(0xFF607EA2)  // Rackley

// Hue colors for lights
val HueLightOn = Color(0xFFFBBF24)  // Amber-400 (warm light)
val HueLightOff = Color(0xFF607EA2)  // Rackley

// Spotify colors
val SpotifyGreen = Color(0xFF1DB954)
val SpotifyBlack = Color(0xFF191414)

// Calendar event colors (matching Google Calendar)
val CalendarLavender = Color(0xFF7986CB)
val CalendarSage = Color(0xFF33B679)
val CalendarGrape = Color(0xFF8E24AA)
val CalendarFlamingo = Color(0xFFE67C73)
val CalendarBanana = Color(0xFFF6BF26)
val CalendarTangerine = Color(0xFFF4511E)
val CalendarPeacock = Color(0xFF039BE5)
val CalendarGraphite = Color(0xFF616161)
val CalendarBlueberry = Color(0xFF3F51B5)
val CalendarBasil = Color(0xFF0B8043)
val CalendarTomato = Color(0xFFD50000)
