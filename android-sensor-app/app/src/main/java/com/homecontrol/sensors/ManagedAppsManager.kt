package com.homecontrol.sensors

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Manages required apps - downloads and installs/updates them automatically.
 * Checks for updates periodically.
 */
object ManagedAppsManager {
    private const val TAG = "ManagedAppsManager"
    private const val PREFS_NAME = "managed_apps"
    private const val CHECK_INTERVAL_HOURS = 6L

    // Apps to manage with their download URLs
    // Format: package name to download URL
    private val MANAGED_APPS = mapOf(
        "com.spotify.music" to "https://mdm.mscrnt.com/files/spotify-9-1-6-1137.apk",
        "io.homeassistant.companion.android.minimal" to "https://mdm.mscrnt.com/files/io.homeassistant.companion.android.minimal_19134.apk"
    )

    private var scheduler: ScheduledExecutorService? = null

    /**
     * Start the managed apps manager.
     * Checks immediately and then periodically.
     */
    fun start(context: Context) {
        Log.d(TAG, "Starting managed apps manager")

        // Check immediately in background
        Thread {
            checkAndInstallAll(context)
        }.start()

        // Schedule periodic checks
        scheduler?.shutdownNow()
        scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler?.scheduleAtFixedRate({
            try {
                checkAndInstallAll(context)
            } catch (e: Exception) {
                Log.e(TAG, "Periodic check failed: ${e.message}")
            }
        }, CHECK_INTERVAL_HOURS, CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
    }

    /**
     * Stop the periodic checker.
     */
    fun stop() {
        scheduler?.shutdownNow()
        scheduler = null
    }

    /**
     * Check all managed apps and install/update if needed.
     */
    fun checkAndInstallAll(context: Context) {
        Log.d(TAG, "Checking ${MANAGED_APPS.size} managed apps...")

        for ((packageName, url) in MANAGED_APPS) {
            try {
                checkAndInstall(context, packageName, url)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process $packageName: ${e.message}")
            }
        }
    }

    /**
     * Check a single app and install/update if needed.
     */
    private fun checkAndInstall(context: Context, packageName: String, url: String) {
        val installedVersion = getInstalledVersion(context, packageName)
        Log.d(TAG, "$packageName: installed version = ${installedVersion ?: "not installed"}")

        // Download APK to temp file
        val tempFile = File(context.cacheDir, "${packageName}.apk")

        try {
            if (!downloadFile(url, tempFile)) {
                Log.e(TAG, "Failed to download $packageName from $url")
                return
            }

            // Get version from downloaded APK
            val apkVersion = getApkVersion(context, tempFile)
            Log.d(TAG, "$packageName: APK version = ${apkVersion ?: "unknown"}")

            // Compare versions
            if (installedVersion == null || (apkVersion != null && isNewerVersion(apkVersion, installedVersion))) {
                Log.i(TAG, "$packageName: Installing/updating from $installedVersion to $apkVersion")
                installApk(context, tempFile, packageName)
            } else {
                Log.d(TAG, "$packageName: Already up to date")
            }
        } finally {
            // Clean up temp file
            tempFile.delete()
        }
    }

    /**
     * Get the installed version of a package.
     */
    private fun getInstalledVersion(context: Context, packageName: String): String? {
        return try {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Get version from an APK file.
     */
    private fun getApkVersion(context: Context, apkFile: File): String? {
        return try {
            val info = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            info?.versionName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get APK version: ${e.message}")
            null
        }
    }

    /**
     * Compare version strings. Returns true if newVersion > oldVersion.
     */
    private fun isNewerVersion(newVersion: String, oldVersion: String): Boolean {
        try {
            val newParts = newVersion.split(".").map { it.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 }
            val oldParts = oldVersion.split(".").map { it.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 }

            for (i in 0 until maxOf(newParts.size, oldParts.size)) {
                val newPart = newParts.getOrElse(i) { 0 }
                val oldPart = oldParts.getOrElse(i) { 0 }
                if (newPart > oldPart) return true
                if (newPart < oldPart) return false
            }
            return false
        } catch (e: Exception) {
            // If we can't parse, assume different versions mean update needed
            return newVersion != oldVersion
        }
    }

    /**
     * Download a file from URL.
     */
    private fun downloadFile(urlString: String, destFile: File): Boolean {
        var connection: HttpURLConnection? = null
        try {
            Log.d(TAG, "Downloading $urlString")
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.requestMethod = "GET"

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Download failed: HTTP ${connection.responseCode}")
                return false
            }

            connection.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Downloaded ${destFile.length()} bytes")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
            return false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Install an APK using PackageInstaller (works for device owner).
     */
    private fun installApk(context: Context, apkFile: File, packageName: String) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(packageName)

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            // Write APK to session
            session.openWrite("package", 0, apkFile.length()).use { output ->
                apkFile.inputStream().use { input ->
                    input.copyTo(output)
                }
                session.fsync(output)
            }

            // Create pending intent for result
            val intent = Intent(context, InstallResultReceiver::class.java)
            intent.action = "com.homecontrol.sensors.INSTALL_RESULT"
            intent.putExtra("package", packageName)

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            // Commit session
            session.commit(pendingIntent.intentSender)
            Log.i(TAG, "Install session committed for $packageName")

        } catch (e: Exception) {
            Log.e(TAG, "Install failed for $packageName: ${e.message}")
        }
    }

    /**
     * Get list of managed apps.
     */
    fun getManagedApps(): Map<String, String> = MANAGED_APPS

    /**
     * Force check and install all apps.
     */
    fun forceUpdate(context: Context) {
        Thread {
            checkAndInstallAll(context)
        }.start()
    }
}
