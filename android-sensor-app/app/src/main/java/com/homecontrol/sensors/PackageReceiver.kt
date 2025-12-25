package com.homecontrol.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Listens for package installations and removes blocklisted apps.
 */
class PackageReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PackageReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                val packageName = intent.data?.schemeSpecificPart ?: return

                Log.d(TAG, "Package installed/updated: $packageName")

                // Check if it's blocklisted and remove if so
                if (BloatwareManager.isBlocklisted(packageName)) {
                    Log.w(TAG, "Blocklisted package detected: $packageName - removing...")
                    Thread {
                        BloatwareManager.removeIfBlocklisted(context, packageName)
                    }.start()
                }
            }
        }
    }
}
