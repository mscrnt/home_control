package com.homecontrol.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON"
        )

        if (intent.action in validActions) {
            Log.d(TAG, "Boot completed - action: ${intent.action}")

            // TODO: Phase 7 - Add auto-launch kiosk/native mode based on saved preference
            // Will need DevicePolicyManager and adminComponent for kiosk lock task mode

            // Just start the sensor service on boot - DON'T auto-launch kiosk
            // User can manually enable kiosk mode when ready
            Log.d(TAG, "Boot completed - starting sensor service only")

            val serviceIntent = Intent(context, SensorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
