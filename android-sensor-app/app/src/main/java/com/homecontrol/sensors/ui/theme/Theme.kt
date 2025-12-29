package com.homecontrol.sensors.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// CompositionLocal for tracking dark theme state across composables
val LocalDarkTheme = compositionLocalOf { false }

// Light theme uses Night Blue as accent (matching web UI [data-theme="light"])
private val LightPrimary = Color(0xFF2D545E)  // Night Blue
private val LightPrimaryVariant = Color(0xFF12343B)  // Night Blue Shadow

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimary.copy(alpha = 0.15f),
    onPrimaryContainer = LightPrimaryVariant,
    secondary = LightPrimary,
    onSecondary = Color.White,
    secondaryContainer = LightPrimary.copy(alpha = 0.12f),
    onSecondaryContainer = LightPrimaryVariant,
    tertiary = LightPrimary,
    onTertiary = Color.White,
    error = Color(0xFFA54545),  // Muted red for light theme
    onError = Color.White,
    errorContainer = Color(0xFFA54545).copy(alpha = 0.12f),
    onErrorContainer = Color(0xFFA54545),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFD4A878),  // Lighter sand
    scrim = Color(0xFF12343B).copy(alpha = 0.6f),  // Night Blue Shadow overlay
    inverseSurface = DarkSurface,
    inverseOnSurface = DarkOnSurface,
    inversePrimary = Primary
)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Primary.copy(alpha = 0.12f),
    onPrimaryContainer = Primary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = Secondary.copy(alpha = 0.12f),
    onSecondaryContainer = Secondary,
    tertiary = Secondary,
    onTertiary = OnSecondary,
    error = Error,
    onError = OnError,
    errorContainer = Error.copy(alpha = 0.12f),
    onErrorContainer = Error,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline.copy(alpha = 0.5f),
    scrim = Color.Black.copy(alpha = 0.32f),
    inverseSurface = LightSurface,
    inverseOnSurface = LightOnSurface,
    inversePrimary = PrimaryVariant
)

@Composable
fun HomeControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge is enabled via enableEdgeToEdge() in NativeActivity
            // For API 35+, system bars are styled via WindowInsetsController
            // For older APIs, we still need to set the colors directly
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                window.statusBarColor = colorScheme.background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = colorScheme.background.toArgb()
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}

// Extension properties for custom colors not in Material3
object HomeControlColors {
    // Glass card background - semi-transparent for glassmorphism effect
    @Composable
    fun cardBackground(): Color = if (LocalDarkTheme.current) {
        Color(0x26FFFFFF)  // rgba(255, 255, 255, 0.15) from CSS
    } else {
        Color(0x80FDFBF8)  // rgba(253, 251, 248, 0.5) from CSS
    }

    // Solid card background for nested elements
    @Composable
    fun cardBackgroundSolid(): Color = if (LocalDarkTheme.current) DarkSurface else LightSurface

    // Card border color
    @Composable
    fun cardBorder(): Color = if (LocalDarkTheme.current) {
        Color(0x33FFFFFF)  // rgba(255, 255, 255, 0.2) from CSS
    } else {
        LightOutline
    }

    // Hover/elevated card background
    @Composable
    fun cardHover(): Color = if (LocalDarkTheme.current) {
        Color(0x40FFFFFF)  // rgba(255, 255, 255, 0.25) from CSS
    } else {
        Color(0xE6FFFFFF)  // rgba(255, 255, 255, 0.9)
    }

    // Accent soft background
    @Composable
    fun accentSoft(): Color = if (LocalDarkTheme.current) {
        Color(0x408197AC)  // rgba(129, 151, 172, 0.25) from CSS
    } else {
        Color(0x262D545E)  // rgba(45, 84, 94, 0.15)
    }

    // Text colors
    @Composable
    fun textMuted(): Color = if (LocalDarkTheme.current) DarkTextMuted else LightOnSurfaceVariant

    @Composable
    fun stateOn(): Color = StateOn

    @Composable
    fun stateOff(): Color = StateOff

    @Composable
    fun stateUnavailable(): Color = StateUnavailable

    @Composable
    fun climateHeating(): Color = ClimateHeating

    @Composable
    fun climateCooling(): Color = ClimateCooling

    @Composable
    fun climateIdle(): Color = ClimateIdle

    @Composable
    fun hueLightOn(): Color = HueLightOn

    @Composable
    fun hueLightOff(): Color = HueLightOff
}
