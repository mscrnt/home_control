package com.homecontrol.sensors.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.homecontrol.sensors.SensorService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge interface for communicating with the SensorService from native UI.
 */
interface SensorServiceBridge {
    /** Current proximity state - true when something is near the sensor */
    val proximityNear: StateFlow<Boolean>

    /** Emits when the screen should wake to screensaver (from proximity detection) */
    val wakeToScreensaver: SharedFlow<Unit>

    /** Wake the screen */
    fun wakeScreen()

    /** Reset the activity timer to prevent screensaver */
    fun resetActivityTimer()

    /** Reset the proximity timer to prevent screen from turning off */
    fun resetProximityTimer()

    /** Update the proximity timeout setting */
    fun updateProximityTimeout(minutes: Int)

    /** Update the adaptive brightness setting */
    fun updateAdaptiveBrightness(enabled: Boolean)

    /** Start listening for sensor events */
    fun start()

    /** Stop listening for sensor events */
    fun stop()
}

/**
 * Implementation of SensorServiceBridge that uses broadcasts to communicate
 * with the SensorService.
 */
@Singleton
class SensorServiceBridgeImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorServiceBridge {

    companion object {
        private const val TAG = "SensorServiceBridge"
    }

    private val _proximityNear = MutableStateFlow(false)
    override val proximityNear: StateFlow<Boolean> = _proximityNear.asStateFlow()

    private val _wakeToScreensaver = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val wakeToScreensaver: SharedFlow<Unit> = _wakeToScreensaver.asSharedFlow()

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var isStarted = false

    private val proximityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SensorService.ACTION_PROXIMITY) {
                val near = intent.getBooleanExtra(SensorService.EXTRA_NEAR, false)
                Log.d(TAG, "Proximity broadcast received: near=$near")
                _proximityNear.value = near
            }
        }
    }

    private val wakeToScreensaverReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SensorService.ACTION_WAKE_TO_SCREENSAVER) {
                Log.d(TAG, "Wake to screensaver broadcast received")
                _wakeToScreensaver.tryEmit(Unit)
            }
        }
    }

    override fun start() {
        if (isStarted) return
        isStarted = true

        val proximityFilter = IntentFilter(SensorService.ACTION_PROXIMITY)
        val wakeFilter = IntentFilter(SensorService.ACTION_WAKE_TO_SCREENSAVER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(proximityReceiver, proximityFilter, Context.RECEIVER_NOT_EXPORTED)
            context.registerReceiver(wakeToScreensaverReceiver, wakeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(proximityReceiver, proximityFilter)
            context.registerReceiver(wakeToScreensaverReceiver, wakeFilter)
        }
        Log.d(TAG, "Started listening for proximity and wake-to-screensaver events")
    }

    override fun stop() {
        if (!isStarted) return
        isStarted = false

        try {
            context.unregisterReceiver(proximityReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister proximity receiver: ${e.message}")
        }
        try {
            context.unregisterReceiver(wakeToScreensaverReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister wake-to-screensaver receiver: ${e.message}")
        }
        Log.d(TAG, "Stopped listening for events")
    }

    override fun wakeScreen() {
        Log.d(TAG, "Waking screen")

        // Send broadcast to SensorService to wake screen
        val intent = Intent(SensorService.ACTION_WAKE_SCREEN).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)

        // Also try to wake directly using PowerManager (requires WAKE_LOCK permission)
        try {
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "$TAG:WakeScreen"
            )
            wakeLock.acquire(1000) // Hold for 1 second
            wakeLock.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to wake screen directly: ${e.message}")
        }
    }

    override fun resetActivityTimer() {
        Log.d(TAG, "Resetting activity timer")

        // Send broadcast to SensorService to reset activity timer
        val intent = Intent(SensorService.ACTION_RESET_ACTIVITY).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    override fun resetProximityTimer() {
        Log.d(TAG, "Resetting proximity timer")

        // Send broadcast to SensorService to reset proximity timer
        val intent = Intent(SensorService.ACTION_RESET_PROXIMITY_TIMER).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    override fun updateProximityTimeout(minutes: Int) {
        Log.d(TAG, "Updating proximity timeout to $minutes minutes")

        val intent = Intent(SensorService.ACTION_UPDATE_PROXIMITY_TIMEOUT).apply {
            putExtra(SensorService.EXTRA_TIMEOUT_MINUTES, minutes)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    override fun updateAdaptiveBrightness(enabled: Boolean) {
        Log.d(TAG, "Updating adaptive brightness to $enabled")

        val intent = Intent(SensorService.ACTION_UPDATE_ADAPTIVE_BRIGHTNESS).apply {
            putExtra(SensorService.EXTRA_ENABLED, enabled)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
