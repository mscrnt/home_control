package com.homecontrol.sensors

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Manages automatic removal of bloatware apps.
 * When the app is device owner, it will automatically uninstall/disable these packages.
 */
object BloatwareManager {
    private const val TAG = "BloatwareManager"

    // List of packages to auto-remove when detected
    private val BLOCKLIST = listOf(
        // Manufacturer bloatware
        "com.efercro.os.kids",           // UKIDOZ
        "com.efercro.os.show",           // UShow
        "com.efercro.os.speaker.photos", // AI Cloud Frame

        // Testing/debug apps
        "com.clock.pt1.keeptesting",     // KeepTesting (adware/testing)
        "com.yhk.devicenewtest",         // System DeviceNewTest
        "com.softwinner.runin",          // System Runin (factory test)
        "com.softwinner.awaiagingdemo",  // AwAiDemo

        // Unnecessary Google apps
        "com.google.android.apps.wellbeing", // Digital Wellbeing
        "com.google.android.gm",             // Gmail
        "com.google.android.apps.maps",      // Google Maps
        "com.google.android.apps.meetings",  // Google Meet
        "com.google.android.apps.tachyon",   // Google Duo/Meet
        "com.google.android.apps.adm",       // Find My Device
    )

    /**
     * Remove all blocklisted apps that are currently installed.
     * Tries to uninstall first, falls back to hiding for system apps.
     * Should be called on app startup.
     */
    fun removeAllBloatware(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        // Check if we're device owner
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.d(TAG, "Not device owner, skipping bloatware check")
            return
        }

        Log.d(TAG, "Checking for bloatware apps to remove...")
        var removedCount = 0

        for (packageName in BLOCKLIST) {
            if (removePackage(context, packageName)) {
                removedCount++
            }
        }

        if (removedCount > 0) {
            Log.i(TAG, "Removed $removedCount bloatware apps")
        }
    }

    /**
     * Remove a specific package - uninstall for user apps, disable for system apps.
     */
    private fun removePackage(context: Context, packageName: String): Boolean {
        try {
            // Check if package is installed
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            // Package not installed, skip
            return false
        }

        // Try uninstalling for current user using pm command
        val result = executeCommand("pm uninstall --user 0 $packageName")
        if (result.exitCode == 0 && result.stdout.contains("Success")) {
            Log.i(TAG, "Uninstalled bloatware: $packageName")
            return true
        }

        // If uninstall failed, try disabling
        val disableResult = executeCommand("pm disable-user --user 0 $packageName")
        if (disableResult.exitCode == 0) {
            Log.i(TAG, "Disabled bloatware: $packageName")
            return true
        }

        // Fall back to hiding via DevicePolicyManager
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)

        return try {
            if (!dpm.isApplicationHidden(adminComponent, packageName)) {
                val success = dpm.setApplicationHidden(adminComponent, packageName, true)
                if (success) {
                    Log.i(TAG, "Hidden bloatware: $packageName")
                }
                success
            } else {
                false // Already hidden
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove $packageName: ${e.message}")
            false
        }
    }

    /**
     * Remove a specific package if it's in the blocklist.
     * Called when a new package is installed.
     */
    fun removeIfBlocklisted(context: Context, packageName: String): Boolean {
        if (packageName !in BLOCKLIST) {
            return false
        }

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        // Check if we're device owner
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.d(TAG, "Not device owner, cannot remove $packageName")
            return false
        }

        return removePackage(context, packageName)
    }

    /**
     * Check if a package is in the blocklist.
     */
    fun isBlocklisted(packageName: String): Boolean {
        return packageName in BLOCKLIST
    }

    /**
     * Get the list of blocklisted packages.
     */
    fun getBlocklist(): List<String> {
        return BLOCKLIST
    }

    private data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    private fun executeCommand(command: String): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
            process.waitFor()
            CommandResult(process.exitValue(), stdout.trim(), stderr.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Command failed: $command - ${e.message}")
            CommandResult(-1, "", e.message ?: "")
        }
    }
}
