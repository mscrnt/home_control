package com.homecontrol.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Automatically restarts the app after an OTA update.
 * Receives MY_PACKAGE_REPLACED which fires after the app is updated.
 */
class SelfUpdateReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SelfUpdateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i(TAG, "App updated, restarting...")

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(launchIntent)
                Log.i(TAG, "App restarted successfully")
            } else {
                Log.e(TAG, "Could not get launch intent for package")
            }
        }
    }
}
