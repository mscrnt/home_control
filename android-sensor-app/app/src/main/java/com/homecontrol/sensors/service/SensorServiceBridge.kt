package com.homecontrol.sensors.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.homecontrol.sensors.KioskActivity
import com.homecontrol.sensors.SensorService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge interface for communicating with the SensorService from native UI.
 */
interface SensorServiceBridge {
    /** Current proximity state - true when something is near the sensor */
    val proximityNear: StateFlow<Boolean>

    /** Wake the screen */
    fun wakeScreen()

    /** Reset the activity timer to prevent screensaver */
    fun resetActivityTimer()

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

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var isStarted = false

    private val proximityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == KioskActivity.ACTION_PROXIMITY) {
                val near = intent.getBooleanExtra(KioskActivity.EXTRA_NEAR, false)
                Log.d(TAG, "Proximity broadcast received: near=$near")
                _proximityNear.value = near
            }
        }
    }

    override fun start() {
        if (isStarted) return
        isStarted = true

        val filter = IntentFilter(KioskActivity.ACTION_PROXIMITY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(proximityReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(proximityReceiver, filter)
        }
        Log.d(TAG, "Started listening for proximity events")
    }

    override fun stop() {
        if (!isStarted) return
        isStarted = false

        try {
            context.unregisterReceiver(proximityReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister receiver: ${e.message}")
        }
        Log.d(TAG, "Stopped listening for proximity events")
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
}
