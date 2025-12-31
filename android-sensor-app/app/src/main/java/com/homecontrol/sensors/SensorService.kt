package com.homecontrol.sensors

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.IpConfiguration
import android.net.LinkAddress
import android.net.StaticIpConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import java.net.InetAddress
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.Socket
import java.util.concurrent.TimeUnit

class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private var proximitySensor: Sensor? = null
    private var lightSensor: Sensor? = null

    // Wake locks to keep CPU and WiFi alive when screen is off
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null

    // HTTP command server for remote shell execution
    private var commandServer: CommandServer? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverUrl: String = ""
    private var idleTimeout: Long = 180000 // 180 seconds (3 minutes) default
    private var proximityTimeoutMs: Long = 5 * 60 * 1000L // 5 minutes default
    private var adaptiveBrightnessEnabled: Boolean = true

    // Proximity timeout tracking
    private var lastProximityNearTime: Long = System.currentTimeMillis()
    private var proximityTimeoutJob: Job? = null

    private var lastProximityNear: Boolean = false
    private var lastLightBasedNear: Boolean = false  // Separate flag for light-based presence
    private var lastLightPresenceTime: Long = 0L     // When light-based presence was last detected
    private var lastLightLevel: Float = -1f
    private var lastReportedLightLevel: Float = -1f
    private var lastBroadcastBrightness: Float = -1f
    // Sliding window for light level averaging (detect rapid drops)
    private val lightHistory = ArrayDeque<Float>(LIGHT_HISTORY_SIZE)
    private var lightHistorySum: Float = 0f
    private var lastActivityTime: Long = System.currentTimeMillis()
    private var lastWakeTime: Long = 0L
    private var screenOn: Boolean = true

    private var idleCheckJob: Job? = null
    private var heartbeatJob: Job? = null
    private var adbCheckJob: Job? = null
    private var adbPort5555Working: Boolean = false

    // Receiver for native UI bridge commands
    private val bridgeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_RESET_ACTIVITY -> {
                    Log.d(TAG, "Bridge: Reset activity timer")
                    lastActivityTime = System.currentTimeMillis()
                }
                ACTION_WAKE_SCREEN -> {
                    Log.d(TAG, "Bridge: Wake screen request")
                    wakeScreen()
                }
                ACTION_UPDATE_PROXIMITY_TIMEOUT -> {
                    val minutes = intent.getIntExtra(EXTRA_TIMEOUT_MINUTES, 5)
                    proximityTimeoutMs = minutes * 60 * 1000L
                    Log.d(TAG, "Bridge: Updated proximity timeout to $minutes minutes")
                }
                ACTION_UPDATE_ADAPTIVE_BRIGHTNESS -> {
                    adaptiveBrightnessEnabled = intent.getBooleanExtra(EXTRA_ENABLED, true)
                    Log.d(TAG, "Bridge: Updated adaptive brightness to $adaptiveBrightnessEnabled")
                }
                ACTION_RESET_PROXIMITY_TIMER -> {
                    lastProximityNearTime = System.currentTimeMillis()
                    Log.d(TAG, "Bridge: Reset proximity timer")
                }
            }
        }
    }

    companion object {
        private const val TAG = "SensorService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "sensor_service"

        // Tuning constants
        private const val LIGHT_REPORT_THRESHOLD = 0.25f  // Report when light changes by 25%
        private const val PRESENCE_DETECT_THRESHOLD = 0.40f  // Detect presence at 40% drop from recent avg
        private const val WAKE_COOLDOWN_MS = 3000L  // Don't try to wake again for 3 seconds
        private const val LIGHT_HISTORY_SIZE = 20   // ~4 seconds of history at 5Hz sampling

        // Default server URL
        const val DEFAULT_SERVER_URL = "http://192.168.69.229:8080"

        // Preferences keys
        const val PREF_NAME = "sensor_prefs"
        const val PREF_SERVER_URL = "server_url"
        const val PREF_IDLE_TIMEOUT = "idle_timeout"

        // WiFi configuration
        const val WIFI_SSID = "Drug Cartel Hideout"
        const val WIFI_PASSWORD = "codeblue23"

        // Static IP configuration
        const val STATIC_IP = "192.168.69.244"
        const val PREFIX_LENGTH = 24
        const val GATEWAY = "192.168.69.1"
        const val DNS = "192.168.69.1"

        // Command server port
        const val COMMAND_SERVER_PORT = 8888

        // Bridge actions from native UI
        const val ACTION_RESET_ACTIVITY = "com.homecontrol.sensors.RESET_ACTIVITY"
        const val ACTION_WAKE_SCREEN = "com.homecontrol.sensors.WAKE_SCREEN"

        // Broadcast actions for proximity events
        const val ACTION_PROXIMITY = "com.homecontrol.sensors.PROXIMITY_CHANGED"
        const val EXTRA_NEAR = "near"

        // Broadcast action for brightness changes (sent to NativeActivity)
        const val ACTION_BRIGHTNESS_CHANGE = "com.homecontrol.sensors.BRIGHTNESS_CHANGE"
        const val EXTRA_BRIGHTNESS = "brightness"

        // Settings update actions (received from NativeActivity)
        const val ACTION_UPDATE_PROXIMITY_TIMEOUT = "com.homecontrol.sensors.UPDATE_PROXIMITY_TIMEOUT"
        const val ACTION_UPDATE_ADAPTIVE_BRIGHTNESS = "com.homecontrol.sensors.UPDATE_ADAPTIVE_BRIGHTNESS"
        const val ACTION_RESET_PROXIMITY_TIMER = "com.homecontrol.sensors.RESET_PROXIMITY_TIMER"
        const val EXTRA_TIMEOUT_MINUTES = "timeout_minutes"
        const val EXTRA_ENABLED = "enabled"

        // Wake to screensaver action (sent to NativeActivity when waking from proximity)
        const val ACTION_WAKE_TO_SCREENSAVER = "com.homecontrol.sensors.WAKE_TO_SCREENSAVER"
    }

    override fun onCreate() {
        super.onCreate()

        // Load preferences with default fallback
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        serverUrl = prefs.getString(PREF_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        idleTimeout = prefs.getLong(PREF_IDLE_TIMEOUT, 180000)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, DeviceAdminReceiver::class.java)

        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        Log.d(TAG, "Service created. Server: $serverUrl, Timeout: ${idleTimeout}ms")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Reload preferences in case they changed (with default fallback)
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        serverUrl = prefs.getString(PREF_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        idleTimeout = prefs.getLong(PREF_IDLE_TIMEOUT, 180000)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Acquire wake locks to keep CPU and WiFi alive when screen is off
        // This ensures ADB stays accessible even when screen sleeps
        acquireWakeLocks()

        // Register sensor listeners
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Proximity sensor registered: ${it.name}, maxRange=${it.maximumRange}, resolution=${it.resolution}")
        } ?: Log.w(TAG, "No proximity sensor available on this device!")

        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Light sensor registered")
        }

        // Start heartbeat and periodic ADB check
        // Note: Idle check disabled - NativeActivity handles its own screensaver
        // startIdleCheck()
        startHeartbeat()
        startAdbCheck()
        startProximityTimeoutCheck()

        // Start the HTTP command server for remote shell execution
        startCommandServer()

        // Register receiver for bridge commands from native UI
        val bridgeFilter = android.content.IntentFilter().apply {
            addAction(ACTION_RESET_ACTIVITY)
            addAction(ACTION_WAKE_SCREEN)
            addAction(ACTION_UPDATE_PROXIMITY_TIMEOUT)
            addAction(ACTION_UPDATE_ADAPTIVE_BRIGHTNESS)
            addAction(ACTION_RESET_PROXIMITY_TIMER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bridgeReceiver, bridgeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bridgeReceiver, bridgeFilter)
        }
        Log.d(TAG, "Bridge receiver registered")

        // Configure and connect to WiFi
        // configureWifi()  // Disabled for emulator testing

        // Send initial state report
        reportProximity(false)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        try {
            unregisterReceiver(bridgeReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister bridge receiver: ${e.message}")
        }
        idleCheckJob?.cancel()
        heartbeatJob?.cancel()
        adbCheckJob?.cancel()
        proximityTimeoutJob?.cancel()
        scope.cancel()
        stopCommandServer()
        releaseWakeLocks()
        Log.d(TAG, "Service destroyed")

        // Schedule restart via broadcast
        scheduleRestart()
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLocks() {
        try {
            // Partial wake lock keeps CPU running
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HomeControlSensors::CpuWakeLock"
            ).apply {
                acquire()
                Log.d(TAG, "CPU wake lock acquired")
            }

            // WiFi lock keeps WiFi active at high performance
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "HomeControlSensors::WifiLock"
            ).apply {
                acquire()
                Log.d(TAG, "WiFi lock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake locks: ${e.message}")
        }
    }

    private fun releaseWakeLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "CPU wake lock released")
                }
            }
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WiFi lock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake locks: ${e.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed, scheduling restart")
        scheduleRestart()
    }

    private fun scheduleRestart() {
        val restartIntent = Intent(this, SensorService::class.java)
        val pendingIntent = PendingIntent.getService(
            this, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 1000, // Restart in 1 second
            pendingIntent
        )
        Log.d(TAG, "Restart scheduled")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> handleProximity(event.values[0])
            Sensor.TYPE_LIGHT -> handleLight(event.values[0])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun handleProximity(distance: Float) {
        val maxRange = proximitySensor?.maximumRange ?: 5f
        val isNear = distance < maxRange

        // Always log proximity events for debugging (on-change sensor may fire rarely)
        Log.d(TAG, "Proximity event: distance=$distance, max=$maxRange, isNear=$isNear, lastNear=$lastProximityNear")

        if (isNear) {
            // Reset proximity timeout whenever someone is detected
            lastProximityNearTime = System.currentTimeMillis()
        }

        if (lastProximityNear != isNear) {
            Log.d(TAG, "Proximity CHANGED: near=$isNear (distance=$distance, max=$maxRange)")
            lastProximityNear = isNear

            if (isNear) {
                // Someone approached - wake screen (to screensaver, not main UI) and reset activity timer
                lastActivityTime = System.currentTimeMillis()
                if (!screenOn) {
                    wakeScreen()
                }
            }

            // Report to server
            reportProximity(isNear)
        }
    }

    private fun handleLight(lux: Float) {
        // Track current level
        lastLightLevel = lux

        // Only report to server for brightness adjustment when change is significant (25%)
        if (lastReportedLightLevel < 0) {
            lastReportedLightLevel = lux
        } else if (kotlin.math.abs(lux - lastReportedLightLevel) / (lastReportedLightLevel + 1) > LIGHT_REPORT_THRESHOLD) {
            lastReportedLightLevel = lux
            reportLight(lux)
        }

        // Adaptive brightness: Calculate and broadcast screen brightness
        if (adaptiveBrightnessEnabled) {
            val brightness = calculateBrightness(lux)
            // Only broadcast if brightness changed significantly (>2%)
            if (lastBroadcastBrightness < 0 || kotlin.math.abs(brightness - lastBroadcastBrightness) > 0.02f) {
                lastBroadcastBrightness = brightness
                broadcastBrightness(brightness)
            }
        }

        // Maintain sliding window of recent light readings
        // Add to history and update sum
        lightHistory.addLast(lux)
        lightHistorySum += lux

        // Remove old readings if window is full
        if (lightHistory.size > LIGHT_HISTORY_SIZE) {
            val removed = lightHistory.removeFirst()
            lightHistorySum -= removed
        }

        // Need at least half the window to make detection decisions
        if (lightHistory.size < LIGHT_HISTORY_SIZE / 2) {
            return
        }

        // Calculate recent average (excluding current reading for comparison)
        val recentAvg = if (lightHistory.size > 1) {
            (lightHistorySum - lux) / (lightHistory.size - 1)
        } else {
            lux
        }

        val now = System.currentTimeMillis()

        // Detect presence: current reading dropped significantly below recent average
        // This works regardless of ambient light level (day or night)
        val dropThreshold = recentAvg * (1 - PRESENCE_DETECT_THRESHOLD)

        if (recentAvg > 5f && lux < dropThreshold) {
            // Significant light drop detected (someone blocking sensor)
            lastLightPresenceTime = now

            if (!lastLightBasedNear) {
                Log.d(TAG, "Light presence detected: $lux < $dropThreshold (avg: $recentAvg)")
                lastActivityTime = now
                lastLightBasedNear = true
                reportProximity(true)

                // Wake screen with cooldown
                if (!screenOn && (now - lastWakeTime) > WAKE_COOLDOWN_MS) {
                    lastWakeTime = now
                    wakeScreen()
                }
            }
        } else {
            // Light is back to normal - clear presence after brief delay
            if (lastLightBasedNear && (now - lastLightPresenceTime) > 3000) {
                Log.d(TAG, "Light presence cleared: $lux (avg: $recentAvg)")
                lastLightBasedNear = false
                reportProximity(false)
            }
        }
    }

    /**
     * Calculate screen brightness based on ambient light level.
     * Uses a logarithmic curve for natural perception.
     * @param lux The ambient light level in lux
     * @return Brightness value from 0.0 to 1.0
     */
    private fun calculateBrightness(lux: Float): Float {
        val minBrightness = 0.10f  // 10% minimum
        val maxBrightness = 1.00f  // 100% maximum

        // Logarithmic mapping for natural perception
        // Typical indoor: 100-500 lux, outdoor: 1000-100000+ lux
        val normalized = when {
            lux < 1f -> 0f
            lux > 10000f -> 1f
            else -> (kotlin.math.ln(lux) / kotlin.math.ln(10000f)).toFloat()
        }

        return (minBrightness + normalized * (maxBrightness - minBrightness))
            .coerceIn(minBrightness, maxBrightness)
    }

    /**
     * Broadcast brightness change to NativeActivity for smooth transition.
     */
    private fun broadcastBrightness(brightness: Float) {
        val intent = Intent(ACTION_BRIGHTNESS_CHANGE).apply {
            putExtra(EXTRA_BRIGHTNESS, brightness)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d(TAG, "Broadcast brightness: ${"%.2f".format(brightness * 100)}%")
    }

    private fun startIdleCheck() {
        idleCheckJob?.cancel()
        idleCheckJob = scope.launch {
            while (isActive) {
                delay(5000) // Check every 5 seconds

                val idleTime = System.currentTimeMillis() - lastActivityTime
                if (screenOn && !lastProximityNear && idleTime > idleTimeout) {
                    Log.d(TAG, "Idle timeout reached (${idleTime}ms), sleeping screen")
                    sleepScreen()
                }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(30000) // Send heartbeat every 30 seconds
                reportProximity(lastProximityNear)
            }
        }
    }

    private fun startAdbCheck() {
        adbCheckJob?.cancel()
        adbCheckJob = scope.launch {
            delay(60000) // Wait 1 minute before first check
            while (isActive) {
                checkAdbPort5555()
                delay(300000) // Check every 5 minutes (only need to verify it's still working)
            }
        }
    }

    private fun startProximityTimeoutCheck() {
        proximityTimeoutJob?.cancel()
        proximityTimeoutJob = scope.launch {
            while (isActive) {
                delay(10000) // Check every 10 seconds

                val elapsed = System.currentTimeMillis() - lastProximityNearTime
                if (elapsed > proximityTimeoutMs && screenOn) {
                    Log.d(TAG, "Proximity timeout reached (${elapsed / 1000}s > ${proximityTimeoutMs / 1000}s), turning off screen")
                    sleepScreen()
                }
            }
        }
    }

    private fun startCommandServer() {
        try {
            commandServer = CommandServer(this, COMMAND_SERVER_PORT).apply {
                start()
            }
            Log.d(TAG, "Command server started on port $COMMAND_SERVER_PORT")

            // Report the command server port to the main server
            reportCommandServerPort()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start command server: ${e.message}")
        }
    }

    private fun stopCommandServer() {
        try {
            commandServer?.stop()
            commandServer = null
            Log.d(TAG, "Command server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping command server: ${e.message}")
        }
    }

    private fun reportCommandServerPort() {
        if (serverUrl.isEmpty()) return

        scope.launch {
            try {
                val json = """{"port": $COMMAND_SERVER_PORT, "type": "command_server"}"""
                val request = Request.Builder()
                    .url("$serverUrl/api/tablet/command/port")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Command server port reported to server: $COMMAND_SERVER_PORT")
                    } else {
                        Log.w(TAG, "Failed to report command server port: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report command server port: ${e.message}")
            }
        }
    }

    /**
     * Check if ADB is listening on port 5555.
     * This is just monitoring - the app cannot set up ADB on its own.
     * To enable ADB over TCP, run `adb tcpip 5555` from an external ADB session.
     */
    private suspend fun checkAdbPort5555() {
        try {
            val isWorking = try {
                Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress("127.0.0.1", 5555), 500)
                    true
                }
            } catch (e: Exception) {
                false
            }

            if (isWorking) {
                if (!adbPort5555Working) {
                    Log.d(TAG, "ADB port 5555 is working")
                    adbPort5555Working = true
                }
            } else {
                if (adbPort5555Working) {
                    Log.w(TAG, "ADB port 5555 stopped working - run 'adb tcpip 5555' to restore")
                    adbPort5555Working = false
                }
                // Note: App cannot set up ADB itself - requires external 'adb tcpip 5555' command
            }
        } catch (e: Exception) {
            Log.e(TAG, "ADB check failed: ${e.message}")
        }
    }

    private fun configureWifi() {
        scope.launch {
            try {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

                // Try Device Owner privileged API with static IP first (Android 12+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (configureWifiPrivileged(wifiManager)) {
                        return@launch
                    }
                }

                // Fallback: Try shell commands
                if (configureWifiViaShell()) {
                    return@launch
                }

                // Last resort: Network suggestions (no static IP support)
                configureWifiSuggestion(wifiManager)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to configure WiFi: ${e.message}")
            }
        }
    }

    private fun configureWifiSuggestion(wifiManager: WifiManager) {
        try {
            val suggestion = WifiNetworkSuggestion.Builder()
                .setSsid(WIFI_SSID)
                .setWpa2Passphrase(WIFI_PASSWORD)
                .setIsAppInteractionRequired(false)
                .build()

            val suggestions = listOf(suggestion)

            // Remove any previous suggestions
            wifiManager.removeNetworkSuggestions(suggestions)

            // Add new suggestion
            val status = wifiManager.addNetworkSuggestions(suggestions)
            if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                Log.d(TAG, "WiFi network suggestion added for $WIFI_SSID")
            } else {
                Log.e(TAG, "Failed to add WiFi suggestion, status=$status")
            }
        } catch (e: Exception) {
            Log.e(TAG, "WiFi suggestion config failed: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun configureWifiPrivileged(wifiManager: WifiManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false

        try {
            Log.d(TAG, "Attempting privileged WiFi config with static IP $STATIC_IP...")

            // Check if we're device owner
            if (!devicePolicyManager.isDeviceOwnerApp(packageName)) {
                Log.d(TAG, "Not device owner, skipping privileged WiFi config")
                return false
            }

            // Create LinkAddress using reflection (constructors are hidden in SDK)
            val inetAddr = InetAddress.getByName(STATIC_IP)
            val linkAddressClass = Class.forName("android.net.LinkAddress")
            val constructor = linkAddressClass.getConstructor(
                InetAddress::class.java,
                Int::class.javaPrimitiveType
            )
            val linkAddress = constructor.newInstance(inetAddr, PREFIX_LENGTH) as LinkAddress

            // Create static IP configuration
            val staticIpConfig = StaticIpConfiguration.Builder()
                .setIpAddress(linkAddress)
                .setGateway(InetAddress.getByName(GATEWAY))
                .setDnsServers(listOf(InetAddress.getByName(DNS)))
                .build()

            val ipConfig = IpConfiguration.Builder()
                .setStaticIpConfiguration(staticIpConfig)
                .build()

            // Create WiFi configuration (WifiConfiguration is deprecated but required for addNetworkPrivileged)
            val wifiConfig = android.net.wifi.WifiConfiguration().apply {
                SSID = "\"$WIFI_SSID\""
                preSharedKey = "\"$WIFI_PASSWORD\""
                allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK)
                // Set the static IP configuration
                setIpConfiguration(ipConfig)
            }

            // Use privileged API to add network (Device Owner only)
            val result = wifiManager.addNetworkPrivileged(wifiConfig)
            Log.d(TAG, "addNetworkPrivileged result: statusCode=${result.statusCode}, networkId=${result.networkId}")

            // STATUS_SUCCESS = 0, or if we got a valid networkId
            if (result.statusCode == WifiManager.AddNetworkResult.STATUS_SUCCESS || result.networkId >= 0) {
                // Enable and connect to the network
                val networkId = if (result.networkId >= 0) result.networkId else 0
                Log.d(TAG, "WiFi network added/exists with static IP, networkId=$networkId, triggering connection...")

                // Disconnect first, then enable and reconnect
                wifiManager.disconnect()
                Thread.sleep(500)
                wifiManager.enableNetwork(networkId, true)
                wifiManager.reconnect()

                // Also force connection via shell command with retries
                Thread.sleep(1000)
                repeat(3) { attempt ->
                    val connectCmd = "cmd wifi connect-network '$WIFI_SSID' wpa2 '$WIFI_PASSWORD'"
                    val connect = Runtime.getRuntime().exec(arrayOf("sh", "-c", connectCmd))
                    connect.waitFor()
                    Thread.sleep(2000)

                    // Check if connected
                    val status = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cmd wifi status"))
                    val statusResult = status.inputStream.bufferedReader().readText()
                    status.waitFor()

                    if (statusResult.contains("is connected to") && statusResult.contains(WIFI_SSID)) {
                        Log.d(TAG, "WiFi connected with static IP $STATIC_IP on attempt ${attempt + 1}")
                        return true
                    }
                    Log.d(TAG, "WiFi connection attempt ${attempt + 1} - not connected yet, retrying...")
                }
                Log.d(TAG, "WiFi configured with static IP $STATIC_IP, connection may be pending")
                return true
            } else {
                Log.e(TAG, "addNetworkPrivileged failed with status: ${result.statusCode}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception in privileged WiFi config: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Privileged WiFi config failed: ${e.message}")
        }
        return false
    }

    private fun configureWifiViaShell(): Boolean {
        try {
            Log.d(TAG, "Attempting WiFi config via shell commands...")

            // Use cmd wifi connect-network to connect (fallback if privileged API fails)
            val connectCmd = "cmd wifi connect-network '$WIFI_SSID' wpa2 '$WIFI_PASSWORD'"
            Log.d(TAG, "Running: $connectCmd")

            val connect = Runtime.getRuntime().exec(arrayOf("sh", "-c", connectCmd))
            val connectResult = connect.inputStream.bufferedReader().readText().trim()
            val connectError = connect.errorStream.bufferedReader().readText().trim()
            connect.waitFor()
            Log.d(TAG, "cmd wifi connect-network result: '$connectResult', error: '$connectError'")

            // Check status to verify connection
            val status = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cmd wifi status"))
            val statusResult = status.inputStream.bufferedReader().readText()
            status.waitFor()

            if (statusResult.contains("is connected to") && statusResult.contains(WIFI_SSID)) {
                Log.d(TAG, "WiFi connected successfully via shell")
                return true
            }

            // If not connected, add network first then connect
            Log.d(TAG, "Connection not confirmed, trying add-network...")
            val addCmd = "cmd wifi add-network '$WIFI_SSID' wpa2 '$WIFI_PASSWORD'"
            val addNetwork = Runtime.getRuntime().exec(arrayOf("sh", "-c", addCmd))
            addNetwork.waitFor()

            // Retry connect
            val retry = Runtime.getRuntime().exec(arrayOf("sh", "-c", connectCmd))
            retry.waitFor()
            Log.d(TAG, "WiFi add-network and connect retry completed")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Shell WiFi config failed: ${e.message}")
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        try {
            // Reset proximity timer so screen doesn't immediately go back to sleep
            lastProximityNearTime = System.currentTimeMillis()
            Log.d(TAG, "Reset proximity timer on wake")

            // Use FULL_WAKE_LOCK with ACQUIRE_CAUSES_WAKEUP to wake the screen
            screenWakeLock?.release()
            screenWakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "HomeControlSensors::ScreenWakeLock"
            ).apply {
                acquire(10000) // Hold for 10 seconds, then release
            }
            screenOn = true
            Log.d(TAG, "Screen woken via wake lock")

            // Broadcast to NativeActivity to show screensaver
            val intent = Intent(ACTION_WAKE_TO_SCREENSAVER).apply {
                setPackage(packageName)
            }
            sendBroadcast(intent)
            Log.d(TAG, "Broadcast wake-to-screensaver")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wake screen: ${e.message}")
            // Fallback: try via server
            fallbackWakeViaServer()
        }
    }

    private fun fallbackWakeViaServer() {
        if (serverUrl.isEmpty()) return
        scope.launch {
            try {
                val request = Request.Builder()
                    .url("$serverUrl/api/tablet/screen/wake")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        screenOn = true
                        Log.d(TAG, "Screen wake command sent via server fallback")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallback wake failed: ${e.message}")
            }
        }
    }

    private fun sleepScreen() {
        try {
            // Release any screen wake lock first
            screenWakeLock?.release()
            screenWakeLock = null

            // Use DevicePolicyManager to lock the device (turns off screen)
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                devicePolicyManager.lockNow()
                screenOn = false
                Log.d(TAG, "Screen locked via DevicePolicyManager")
            } else {
                Log.w(TAG, "Device admin not active, falling back to server")
                fallbackSleepViaServer()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sleep screen: ${e.message}")
            fallbackSleepViaServer()
        }
    }

    private fun fallbackSleepViaServer() {
        if (serverUrl.isEmpty()) return
        scope.launch {
            try {
                val request = Request.Builder()
                    .url("$serverUrl/api/tablet/screen/sleep")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        screenOn = false
                        Log.d(TAG, "Screen sleep command sent via server fallback")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallback sleep failed: ${e.message}")
            }
        }
    }

    private fun reportProximity(near: Boolean) {
        // Broadcast locally for immediate screensaver dismissal
        val intent = android.content.Intent(ACTION_PROXIMITY).apply {
            putExtra(EXTRA_NEAR, near)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        Log.d(TAG, "Sent proximity broadcast: near=$near")

        if (serverUrl.isEmpty()) return

        scope.launch {
            try {
                val idleTimeoutSecs = (idleTimeout / 1000).toInt()
                val json = """{"near": $near, "idleTimeout": $idleTimeoutSecs}"""
                val request = Request.Builder()
                    .url("$serverUrl/api/tablet/sensor/proximity")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report proximity: ${e.message}")
            }
        }
    }

    private fun reportLight(lux: Float) {
        if (serverUrl.isEmpty()) return

        scope.launch {
            try {
                val json = """{"lux": $lux}"""
                val request = Request.Builder()
                    .url("$serverUrl/api/tablet/sensor/light")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report light: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sensor Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors proximity and light sensors"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.apply {
            setContentTitle("Home Control Sensors")
            setContentText("Monitoring proximity & light")
            setSmallIcon(android.R.drawable.ic_menu_compass)
            setContentIntent(pendingIntent)
            setOngoing(true)
        }.build()
    }
}
