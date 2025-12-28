package com.homecontrol.sensors.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Primary.copy(alpha = 0.12f),
    onPrimaryContainer = PrimaryVariant,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = Secondary.copy(alpha = 0.12f),
    onSecondaryContainer = SecondaryVariant,
    tertiary = Secondary,
    onTertiary = OnSecondary,
    error = Error,
    onError = OnError,
    errorContainer = Error.copy(alpha = 0.12f),
    onErrorContainer = Error,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.5f),
    scrim = Color.Black.copy(alpha = 0.32f),
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

// Extension properties for custom colors not in Material3
object HomeControlColors {
    @Composable
    fun cardBackground(): Color = if (isSystemInDarkTheme()) DarkCardBackground else LightCardBackground

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
