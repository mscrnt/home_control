package com.homecontrol.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/**
 * Receives results from PackageInstaller sessions.
 */
class InstallResultReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "InstallResultReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra("package") ?: "unknown"
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Successfully installed $packageName")
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Need user confirmation - this shouldn't happen for device owner
                Log.w(TAG, "Install pending user action for $packageName")
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmIntent)
                }
            }
            PackageInstaller.STATUS_FAILURE -> {
                Log.e(TAG, "Install failed for $packageName: $message")
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Log.e(TAG, "Install aborted for $packageName: $message")
            }
            PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                Log.e(TAG, "Install blocked for $packageName: $message")
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                Log.e(TAG, "Install conflict for $packageName: $message")
            }
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                Log.e(TAG, "Install incompatible for $packageName: $message")
            }
            PackageInstaller.STATUS_FAILURE_INVALID -> {
                Log.e(TAG, "Install invalid for $packageName: $message")
            }
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                Log.e(TAG, "Install storage error for $packageName: $message")
            }
            else -> {
                Log.e(TAG, "Install unknown status $status for $packageName: $message")
            }
        }
    }
}
