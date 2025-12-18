package com.homecontrol.sensors

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
    private var proximitySensor: Sensor? = null
    private var lightSensor: Sensor? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverUrl: String = ""
    private var idleTimeout: Long = 60000 // 60 seconds default

    private var lastProximityNear: Boolean = false
    private var lastLightLevel: Float = -1f
    private var lastReportedLightLevel: Float = -1f
    private var baselineLightLevel: Float = -1f
    private var lastActivityTime: Long = System.currentTimeMillis()
    private var lastWakeTime: Long = 0L
    private var screenOn: Boolean = true

    private var idleCheckJob: Job? = null
    private var heartbeatJob: Job? = null

    companion object {
        private const val TAG = "SensorService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "sensor_service"

        // Tuning constants
        private const val LIGHT_REPORT_THRESHOLD = 0.25f  // Report when light changes by 25%
        private const val PRESENCE_DETECT_THRESHOLD = 0.35f  // Detect presence at 35% light drop
        private const val WAKE_COOLDOWN_MS = 3000L  // Don't try to wake again for 3 seconds

        // Default server URL
        const val DEFAULT_SERVER_URL = "http://192.168.69.229:8080"

        // Preferences keys
        const val PREF_NAME = "sensor_prefs"
        const val PREF_SERVER_URL = "server_url"
        const val PREF_IDLE_TIMEOUT = "idle_timeout"
    }

    override fun onCreate() {
        super.onCreate()

        // Load preferences with default fallback
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        serverUrl = prefs.getString(PREF_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        idleTimeout = prefs.getLong(PREF_IDLE_TIMEOUT, 60000)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        Log.d(TAG, "Service created. Server: $serverUrl, Timeout: ${idleTimeout}ms")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Reload preferences in case they changed (with default fallback)
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        serverUrl = prefs.getString(PREF_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        idleTimeout = prefs.getLong(PREF_IDLE_TIMEOUT, 60000)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Register sensor listeners
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Proximity sensor registered")
        }

        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Light sensor registered")
        }

        // Start idle check loop and heartbeat
        startIdleCheck()
        startHeartbeat()

        // Ensure ADB WiFi is enabled and report port to server
        ensureAdbWifiEnabled()

        // Send initial state report
        reportProximity(false)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        idleCheckJob?.cancel()
        heartbeatJob?.cancel()
        scope.cancel()
        Log.d(TAG, "Service destroyed")
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

        if (lastProximityNear != isNear) {
            Log.d(TAG, "Proximity changed: near=$isNear (distance=$distance, max=$maxRange)")
            lastProximityNear = isNear

            if (isNear) {
                // Someone approached - wake screen and reset activity timer
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
        // Set baseline on first reading
        if (baselineLightLevel < 0) {
            baselineLightLevel = lux
            lastReportedLightLevel = lux
        }

        // Track current level for presence detection
        lastLightLevel = lux

        // Only report to server for brightness adjustment when change is significant (25%)
        // This reduces unnecessary brightness flickering
        if (lastReportedLightLevel < 0 ||
            kotlin.math.abs(lux - lastReportedLightLevel) / (lastReportedLightLevel + 1) > LIGHT_REPORT_THRESHOLD) {
            lastReportedLightLevel = lux
            Log.d(TAG, "Light reported: $lux lux (baseline: $baselineLightLevel)")
            reportLight(lux)
        }

        // Detect presence based on light drop (someone blocking the sensor)
        val now = System.currentTimeMillis()
        if (baselineLightLevel > 20 && lux < baselineLightLevel * (1 - PRESENCE_DETECT_THRESHOLD)) {
            Log.d(TAG, "Light drop detected - presence assumed (${lux} < ${baselineLightLevel * (1 - PRESENCE_DETECT_THRESHOLD)})")
            lastActivityTime = now

            // Wake screen with cooldown to avoid rapid on/off
            if (!screenOn && (now - lastWakeTime) > WAKE_COOLDOWN_MS) {
                lastWakeTime = now
                wakeScreen()
            }
        }

        // Update baseline slowly when light is stable and high
        if (lux > baselineLightLevel * 0.8) {
            baselineLightLevel = baselineLightLevel * 0.95f + lux * 0.05f
        }
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

    private fun ensureAdbWifiEnabled() {
        scope.launch {
            try {
                // First, try to enable classic ADB TCP on fixed port 5555
                // This is more reliable but may require elevated privileges
                if (tryEnableFixedPortAdb()) {
                    Log.d(TAG, "Classic ADB TCP enabled on port 5555")
                    delay(2000)
                    if (isAdbPort(5555)) {
                        reportAdbPort(5555)
                        return@launch
                    }
                }

                // Fall back to wireless debugging (random port)
                Log.d(TAG, "Falling back to wireless debugging...")
                val currentValue = Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0)
                if (currentValue != 1) {
                    Log.d(TAG, "Enabling ADB WiFi...")
                    Settings.Global.putInt(contentResolver, "adb_wifi_enabled", 1)
                    delay(2000)
                }

                // Find and report the ADB port
                delay(3000) // Give ADB time to bind to port
                val port = findAdbPort()
                if (port != null) {
                    Log.d(TAG, "Found ADB WiFi port: $port")
                    reportAdbPort(port)
                } else {
                    Log.w(TAG, "Could not find ADB WiFi port")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enable ADB WiFi: ${e.message}")
            }
        }
    }

    private fun tryEnableFixedPortAdb(): Boolean {
        try {
            // Try to set ADB TCP port to 5555 via setprop
            // This typically requires root, but let's try anyway
            Log.d(TAG, "Attempting to enable ADB on fixed port 5555...")

            // Method 1: Try setprop (requires root or shell permissions)
            val setPort = Runtime.getRuntime().exec(arrayOf("sh", "-c", "setprop service.adb.tcp.port 5555"))
            setPort.waitFor()

            // Check if we can read it back
            val getPort = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getprop service.adb.tcp.port"))
            val portValue = getPort.inputStream.bufferedReader().readText().trim()
            getPort.waitFor()

            if (portValue == "5555") {
                Log.d(TAG, "Successfully set ADB port to 5555, restarting adbd...")

                // Restart adbd to apply the change
                val restart = Runtime.getRuntime().exec(arrayOf("sh", "-c", "stop adbd; start adbd"))
                restart.waitFor()

                return true
            } else {
                Log.d(TAG, "setprop didn't work (got: '$portValue'), no root access")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Fixed port ADB failed: ${e.message}")
        }
        return false
    }

    private fun findAdbPort(): Int? {
        // Method 1: Try reading /proc/net/tcp6 directly (more likely to work in app sandbox)
        try {
            val tcp6File = File("/proc/net/tcp6")
            if (tcp6File.exists()) {
                val content = tcp6File.readText()
                Log.d(TAG, "tcp6 file lines: ${content.lines().size}")

                // Parse hex port from /proc/net/tcp6 format
                // Format: sl local_address rem_address st ...
                // local_address is IP:PORT in hex
                val portRegex = Regex(""":\s*[0-9A-Fa-f]+:([0-9A-Fa-f]{4})\s""")
                val foundPorts = mutableSetOf<Int>()

                for (line in content.lines()) {
                    val match = portRegex.find(line)
                    if (match != null) {
                        val hexPort = match.groupValues[1]
                        val port = hexPort.toIntOrNull(16) ?: continue
                        if (port in 30000..50000) {
                            foundPorts.add(port)
                        }
                    }
                }

                Log.d(TAG, "Found high ports from /proc/net/tcp6: $foundPorts")

                for (port in foundPorts) {
                    if (isAdbPort(port)) {
                        Log.d(TAG, "Found ADB listening on port $port via /proc/net/tcp6")
                        return port
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reading /proc/net/tcp6 failed: ${e.message}")
        }

        // Method 2: Try ss command
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ss -tlnp 2>/dev/null | grep LISTEN"))
            val output = process.inputStream.bufferedReader().readText()
            if (output.isNotEmpty()) {
                Log.d(TAG, "ss output: $output")
                val portRegex = Regex(""":(\d{5})""")
                for (match in portRegex.findAll(output)) {
                    val port = match.groupValues[1].toIntOrNull() ?: continue
                    if (port in 30000..50000 && isAdbPort(port)) {
                        Log.d(TAG, "Found ADB listening on port $port via ss")
                        return port
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ss command failed: ${e.message}")
        }

        // Method 3: Parallel scan of likely port ranges
        val scanIp = getWifiIpAddress() ?: "127.0.0.1"
        Log.d(TAG, "Falling back to parallel port scan on IP: $scanIp")

        // Check port 5555 first
        if (isAdbPort(5555)) {
            Log.d(TAG, "Found ADB listening on port 5555 via scan")
            return 5555
        }

        // Scan common wireless debugging port ranges in parallel
        val portRanges = listOf(
            35000..36000,
            36000..37000,
            37000..38000,
            38000..39000,
            39000..40000,
            40000..41000,
            41000..42000,
            42000..43000,
            43000..44000,
            44000..45000
        )

        val foundPort = java.util.concurrent.atomic.AtomicInteger(0)
        val threads = portRanges.map { range ->
            Thread {
                for (port in range) {
                    if (foundPort.get() != 0) break
                    if (isAdbPortFast(port)) {
                        foundPort.compareAndSet(0, port)
                        break
                    }
                }
            }.apply { start() }
        }

        // Wait for all threads (max 10 seconds)
        threads.forEach { it.join(10000) }

        val result = foundPort.get()
        if (result != 0) {
            Log.d(TAG, "Found ADB listening on port $result via parallel scan")
            return result
        }

        Log.w(TAG, "Could not find ADB port")
        return null
    }

    private fun getWifiIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.name.startsWith("wlan") || iface.name.startsWith("eth")) {
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get WiFi IP: ${e.message}")
        }
        return null
    }

    private fun isAdbPortFast(port: Int): Boolean {
        val ip = getWifiIpAddress() ?: "127.0.0.1"
        return try {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(ip, port), 100)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isAdbPort(port: Int): Boolean {
        val ip = getWifiIpAddress() ?: "127.0.0.1"
        // Try to connect and see if it responds like ADB
        return try {
            Socket(ip, port).use { socket ->
                socket.soTimeout = 1000
                // ADB sends a specific banner, but just connecting successfully is a good sign
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun reportAdbPort(port: Int) {
        if (serverUrl.isEmpty()) return

        scope.launch {
            try {
                val json = """{"port": $port}"""
                val request = Request.Builder()
                    .url("$serverUrl/api/tablet/adb/port")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "ADB port reported to server: $port")
                    } else {
                        Log.w(TAG, "Failed to report ADB port: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report ADB port: ${e.message}")
            }
        }
    }

    private fun wakeScreen() {
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
                        Log.d(TAG, "Screen wake command sent")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to wake screen: ${e.message}")
            }
        }
    }

    private fun sleepScreen() {
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
                        Log.d(TAG, "Screen sleep command sent")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sleep screen: ${e.message}")
            }
        }
    }

    private fun reportProximity(near: Boolean) {
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
