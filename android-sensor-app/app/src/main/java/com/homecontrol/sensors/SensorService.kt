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
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

        // Preferences keys
        const val PREF_NAME = "sensor_prefs"
        const val PREF_SERVER_URL = "server_url"
        const val PREF_IDLE_TIMEOUT = "idle_timeout"
    }

    override fun onCreate() {
        super.onCreate()

        // Load preferences
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        serverUrl = prefs.getString(PREF_SERVER_URL, "") ?: ""
        idleTimeout = prefs.getLong(PREF_IDLE_TIMEOUT, 60000)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        Log.d(TAG, "Service created. Server: $serverUrl, Timeout: ${idleTimeout}ms")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Reload preferences in case they changed
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        serverUrl = prefs.getString(PREF_SERVER_URL, "") ?: ""
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
