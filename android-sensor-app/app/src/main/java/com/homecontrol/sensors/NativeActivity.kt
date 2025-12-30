package com.homecontrol.sensors

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homecontrol.sensors.data.repository.AppSettings
import com.homecontrol.sensors.data.repository.SettingsRepository
import com.homecontrol.sensors.data.repository.SpotifyRepository
import com.homecontrol.sensors.data.repository.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.homecontrol.sensors.ui.components.MiniSpotifyPlayer
import com.homecontrol.sensors.ui.screens.home.HomeScreen
import com.homecontrol.sensors.ui.screens.hue.HueScreen
import com.homecontrol.sensors.ui.screens.calendar.CalendarScreen
import com.homecontrol.sensors.ui.screens.screensaver.ScreensaverScreen
import com.homecontrol.sensors.ui.screens.settings.SettingsScreen
import com.homecontrol.sensors.ui.screens.spotify.SpotifyScreen
import com.homecontrol.sensors.ui.theme.HomeControlColors
import com.homecontrol.sensors.ui.theme.HomeControlTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

// Callback interface for touch events
interface TouchCallback {
    fun onUserInteraction()
}

// Global holder for the callback
object TouchCallbackHolder {
    var callback: TouchCallback? = null
}

// Callback for proximity events from SensorService
interface ProximityCallback {
    fun onProximityChanged(isNear: Boolean)
}

// Global holder for proximity callback
object ProximityCallbackHolder {
    var callback: ProximityCallback? = null
}

@AndroidEntryPoint
class NativeActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var spotifyRepository: SpotifyRepository

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Broadcast receiver for proximity events from SensorService
    private val proximityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SensorService.ACTION_PROXIMITY) {
                val isNear = intent.getBooleanExtra(SensorService.EXTRA_NEAR, false)
                Log.d(TAG, "Proximity broadcast received: near=$isNear")
                if (isNear) {
                    // Dismiss screensaver via callback
                    ProximityCallbackHolder.callback?.onProximityChanged(isNear)
                }
            }
        }
    }

    companion object {
        private const val TAG = "NativeActivity"
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
        private var spotifyLaunched = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Override display density to make UI elements larger on low-density tablets
        // Target 240 dpi (hdpi) for consistent sizing
        adjustDisplayDensity(targetDpi = 240)

        enableEdgeToEdge()

        // Enable immersive fullscreen mode - hides status bar until user swipes from edge
        setupImmersiveMode()

        // Start managed apps manager (auto-installs Spotify, Home Assistant, etc.)
        ManagedAppsManager.start(this)

        // Start the sensor service for proximity/light detection
        startSensorService()

        // Register broadcast receiver for proximity events
        val proximityFilter = IntentFilter(SensorService.ACTION_PROXIMITY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(proximityReceiver, proximityFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(proximityReceiver, proximityFilter)
        }
        Log.d(TAG, "Proximity receiver registered")

        // Launch Spotify quickly in background (only once per app session)
        if (!spotifyLaunched) {
            launchSpotifyQuickly()
        }

        setContent {
            // Observe theme settings
            val settings by settingsRepository.settings.collectAsState(
                initial = AppSettings()
            )
            val systemDarkTheme = isSystemInDarkTheme()

            // Determine dark theme based on user preference
            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDarkTheme
            }

            // Apply keep screen on setting
            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    Log.d(TAG, "Keep screen on: enabled")
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    Log.d(TAG, "Keep screen on: disabled")
                }
            }

            HomeControlTheme(darkTheme = darkTheme) {
                MainContent(idleTimeoutSeconds = settings.idleTimeout)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(proximityReceiver)
            Log.d(TAG, "Proximity receiver unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister proximity receiver: ${e.message}")
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // Reset idle timer on any touch
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            TouchCallbackHolder.callback?.onUserInteraction()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun startSensorService() {
        val intent = Intent(this, SensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Log.d(TAG, "SensorService started")
    }

    @Suppress("DEPRECATION")
    private fun isSpotifyRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // Method 1: Check running tasks (works on Android TV)
        try {
            val tasks = activityManager.getRunningTasks(10)
            if (tasks.any { it.baseActivity?.packageName == SPOTIFY_PACKAGE }) {
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "getRunningTasks failed: ${e.message}")
        }

        // Method 2: Check app processes as fallback
        val runningProcesses = activityManager.runningAppProcesses ?: return false
        return runningProcesses.any {
            it.processName == SPOTIFY_PACKAGE &&
            it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }
    }

    @Suppress("DEPRECATION")
    private fun setupImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    private fun adjustDisplayDensity(targetDpi: Int) {
        val displayMetrics = resources.displayMetrics
        val configuration = resources.configuration

        // Calculate the scale factor to reach target DPI
        // Only adjust if current density is lower than target
        if (displayMetrics.densityDpi < targetDpi) {
            val scaleFactor = targetDpi.toFloat() / displayMetrics.densityDpi.toFloat()
            displayMetrics.density = displayMetrics.density * scaleFactor
            displayMetrics.scaledDensity = displayMetrics.scaledDensity * scaleFactor
            displayMetrics.densityDpi = targetDpi

            configuration.densityDpi = targetDpi
            resources.updateConfiguration(configuration, displayMetrics)

            Log.d(TAG, "Adjusted display density to $targetDpi dpi (scale factor: $scaleFactor)")
        } else {
            Log.d(TAG, "Display density already at ${displayMetrics.densityDpi} dpi, no adjustment needed")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveMode()
        }
    }

    private fun launchSpotifyQuickly() {
        // Launch in a coroutine to check if Spotify needs to be started
        activityScope.launch {
            try {
                Log.d(TAG, "Checking if Spotify needs to be launched on this device...")

                // FIRST: Check if Spotify is running locally on THIS device
                // This is the most reliable check - API calls show all devices, not just this one
                if (isSpotifyRunning()) {
                    Log.d(TAG, "Spotify process is already running on this device, skipping launch")
                    spotifyLaunched = true
                    return@launch
                }

                // Spotify not running - launch it
                val spotifyIntent = packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)
                if (spotifyIntent == null) {
                    Log.d(TAG, "Spotify not installed, cannot launch")
                    return@launch
                }

                spotifyLaunched = true
                Log.i(TAG, "Launching Spotify in background...")

                // Launch Spotify with no animation
                spotifyIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                startActivity(spotifyIntent)

                // Bring our app back after a short delay (100ms to ensure Spotify starts)
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "Bringing HomeControl back to foreground")
                    val bringBackIntent = Intent(this@NativeActivity, NativeActivity::class.java)
                    bringBackIntent.addFlags(
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                    startActivity(bringBackIntent)
                    overridePendingTransition(0, 0)
                }, 100)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch Spotify: ${e.message}", e)
            }
        }
    }
}

// Modal types for smart home screens
sealed class SmartHomeModal {
    object Home : SmartHomeModal()
    object Lights : SmartHomeModal()
    object Music : SmartHomeModal()
    object Cameras : SmartHomeModal()
    object Media : SmartHomeModal()
    object Settings : SmartHomeModal()
}

data class DrawerNavItem(
    val modal: SmartHomeModal,
    val label: String,
    val icon: ImageVector
)

val smartHomeNavItems = listOf(
    DrawerNavItem(
        modal = SmartHomeModal.Home,
        label = "Home",
        icon = Icons.Filled.Home
    ),
    DrawerNavItem(
        modal = SmartHomeModal.Lights,
        label = "Lights",
        icon = Icons.Filled.Lightbulb
    ),
    DrawerNavItem(
        modal = SmartHomeModal.Music,
        label = "Spotify",
        icon = Icons.Filled.MusicNote // Will be overridden with Spotify logo in drawer
    ),
    DrawerNavItem(
        modal = SmartHomeModal.Cameras,
        label = "Cameras",
        icon = Icons.Filled.Videocam
    ),
    DrawerNavItem(
        modal = SmartHomeModal.Media,
        label = "Entertainment",
        icon = Icons.Filled.Tv
    ),
    DrawerNavItem(
        modal = SmartHomeModal.Settings,
        label = "Settings",
        icon = Icons.Filled.Settings
    )
)

@Composable
fun MainContent(idleTimeoutSeconds: Int = 60) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Track which modal is currently open (null = none)
    var activeModal by remember { mutableStateOf<SmartHomeModal?>(null) }

    // Screensaver state
    var showScreensaver by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Register touch callback to reset idle timer
    DisposableEffect(Unit) {
        val touchCallback = object : TouchCallback {
            override fun onUserInteraction() {
                lastInteractionTime = System.currentTimeMillis()
                if (showScreensaver) {
                    showScreensaver = false
                }
            }
        }
        TouchCallbackHolder.callback = touchCallback

        // Register proximity callback to dismiss screensaver when someone approaches
        val proximityCallback = object : ProximityCallback {
            override fun onProximityChanged(isNear: Boolean) {
                if (isNear) {
                    lastInteractionTime = System.currentTimeMillis()
                    if (showScreensaver) {
                        showScreensaver = false
                    }
                }
            }
        }
        ProximityCallbackHolder.callback = proximityCallback

        onDispose {
            TouchCallbackHolder.callback = null
            ProximityCallbackHolder.callback = null
        }
    }

    // Idle timer - check periodically if we should show screensaver
    LaunchedEffect(idleTimeoutSeconds, lastInteractionTime) {
        if (idleTimeoutSeconds > 0) {
            while (true) {
                delay(1000)
                val elapsed = System.currentTimeMillis() - lastInteractionTime
                if (elapsed >= idleTimeoutSeconds * 1000L && !showScreensaver) {
                    showScreensaver = true
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content - Calendar with drawer
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false, // Disable swipe to not interfere with calendar
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(300.dp),
                    drawerContainerColor = HomeControlColors.cardBackgroundSolid()
                ) {
                    SmartHomeDrawerContent(
                        onItemClick = { modal ->
                            scope.launch {
                                drawerState.close()
                                activeModal = modal
                            }
                        },
                        onClose = {
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        ) {
            // Calendar is always the base
            CalendarScreen(
                onOpenDrawer = {
                    scope.launch { drawerState.open() }
                },
                isScreensaverActive = showScreensaver,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Full-screen modal overlay
        AnimatedVisibility(
            visible = activeModal != null,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 }
        ) {
            FullScreenModal(
                title = when (activeModal) {
                    SmartHomeModal.Home -> "Home"
                    SmartHomeModal.Lights -> "hue"
                    SmartHomeModal.Music -> "Spotify"
                    SmartHomeModal.Cameras -> "Cameras"
                    SmartHomeModal.Media -> "Entertainment"
                    SmartHomeModal.Settings -> "Settings"
                    null -> ""
                },
                onClose = { activeModal = null },
                titleContent = if (activeModal == SmartHomeModal.Music) {
                    { SpotifyLogoTitle() }
                } else null
            ) {
                when (activeModal) {
                    SmartHomeModal.Home -> HomeScreen()
                    SmartHomeModal.Lights -> HueScreen()
                    SmartHomeModal.Music -> SpotifyScreen()
                    SmartHomeModal.Cameras -> PlaceholderContent("Cameras")
                    SmartHomeModal.Media -> PlaceholderContent("Media")
                    SmartHomeModal.Settings -> SettingsScreen()
                    null -> {}
                }
            }
        }

        // Screensaver overlay
        AnimatedVisibility(
            visible = showScreensaver,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ScreensaverScreen(
                onDismiss = {
                    showScreensaver = false
                    lastInteractionTime = System.currentTimeMillis()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun FullScreenModal(
    title: String,
    onClose: () -> Unit,
    titleContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeControlColors.cardBackgroundSolid())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with title and close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Empty spacer for balance
                Spacer(modifier = Modifier.size(48.dp))

                // Title - either custom composable or text
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (titleContent != null) {
                        titleContent()
                    } else {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PlaceholderContent(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Spotify brand color
private val SpotifyGreen = Color(0xFF1DB954)

@Composable
private fun SpotifyLogoTitle() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_spotify),
            contentDescription = "Spotify",
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Spotify",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = SpotifyGreen
        )
    }
}

@Composable
private fun SmartHomeDrawerContent(
    onItemClick: (SmartHomeModal) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_house_signal),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Home Control",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = HomeControlColors.cardBorder())
        Spacer(modifier = Modifier.height(16.dp))

        // Smart home items
        smartHomeNavItems.forEach { item ->
            when (item.modal) {
                SmartHomeModal.Music -> {
                    // Special Spotify drawer item with logo
                    SpotifyDrawerItem(
                        selected = false,
                        onClick = { onItemClick(item.modal) }
                    )
                }
                SmartHomeModal.Lights -> {
                    // Special Philips Hue drawer item with logo
                    PhilipsHueDrawerItem(
                        selected = false,
                        onClick = { onItemClick(item.modal) }
                    )
                }
                SmartHomeModal.Cameras -> {
                    // Special Amcrest drawer item with logo
                    AmcrestDrawerItem(
                        selected = false,
                        onClick = { onItemClick(item.modal) }
                    )
                }
                SmartHomeModal.Media -> {
                    // Special Entertainment drawer item with icon
                    EntertainmentDrawerItem(
                        selected = false,
                        onClick = { onItemClick(item.modal) }
                    )
                }
                else -> {
                    DrawerItem(
                        icon = item.icon,
                        label = item.label,
                        selected = false,
                        onClick = { onItemClick(item.modal) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Mini Spotify Player
        MiniSpotifyPlayer()

        Spacer(modifier = Modifier.height(12.dp))

        // Version info
        Text(
            text = "Home Control v1.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }

    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor
        )
    }
}

@Composable
private fun SpotifyDrawerItem(
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        SpotifyGreen.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_spotify),
            contentDescription = "Spotify",
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "Spotify",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = SpotifyGreen
        )
    }
}

// Philips Hue brand color
private val PhilipsHueOrange = Color(0xFFFFB700)

@Composable
private fun PhilipsHueDrawerItem(
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        PhilipsHueOrange.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_hue_lightbulb),
            contentDescription = "Hue Lights",
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Image(
            painter = painterResource(id = R.drawable.ic_philipshue),
            contentDescription = "Philips Hue",
            modifier = Modifier.height(16.dp)
        )
    }
}

// Amcrest brand color
private val AmcrestBlue = Color(0xFF091D40)

@Composable
private fun AmcrestDrawerItem(
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        AmcrestBlue.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_amcrest),
            contentDescription = "Amcrest Cameras",
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "Cameras",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = AmcrestBlue
        )
    }
}

// Entertainment purple color
private val EntertainmentPurple = Color(0xFF9C27B0)

@Composable
private fun EntertainmentDrawerItem(
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        EntertainmentPurple.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_photo_film_music),
            contentDescription = "Entertainment",
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "Entertainment",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = EntertainmentPurple
        )
    }
}
