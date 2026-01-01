package com.homecontrol.sensors.data.repository

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.homecontrol.sensors.InstallResultReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class UpdateManifest(
    val version: String,
    val versionCode: Int = 0,
    val url: String,
    val changelog: String = ""
)

data class UpdateState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val isInstalling: Boolean = false,
    val downloadProgress: Float = 0f,
    val availableUpdate: UpdateManifest? = null,
    val error: String? = null,
    val lastCheckTime: Long = 0
)

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val TAG = "UpdateRepository"
        private const val MANIFEST_URL = "https://raw.githubusercontent.com/mscrnt/home_control/refs/heads/android-native/android-sensor-app/homecontrol-latest.json"
        private const val APK_FILENAME = "update.apk"
    }

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val packageInfo: PackageInfo by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }

    /**
     * Get current app version name
     */
    fun getCurrentVersion(): String = packageInfo.versionName ?: "unknown"

    /**
     * Get current app version code
     */
    fun getCurrentVersionCode(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode
    }

    /**
     * Check if an update is available
     */
    suspend fun checkForUpdate(): Result<UpdateManifest?> = withContext(Dispatchers.IO) {
        _updateState.value = _updateState.value.copy(isChecking = true, error = null)

        try {
            val request = Request.Builder()
                .url(MANIFEST_URL)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Failed to fetch manifest: ${response.code}")
            }

            val body = response.body?.string() ?: throw Exception("Empty response")
            val manifest = json.decodeFromString<UpdateManifest>(body)

            Log.d(TAG, "Manifest fetched: version=${manifest.version}, current=${getCurrentVersion()}")

            val hasUpdate = isNewerVersion(manifest.version, getCurrentVersion()) ||
                    (manifest.versionCode > 0 && manifest.versionCode > getCurrentVersionCode())

            _updateState.value = _updateState.value.copy(
                isChecking = false,
                availableUpdate = if (hasUpdate) manifest else null,
                lastCheckTime = System.currentTimeMillis()
            )

            if (hasUpdate) {
                Log.i(TAG, "Update available: ${manifest.version}")
                Result.success(manifest)
            } else {
                Log.d(TAG, "No update available")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for update", e)
            _updateState.value = _updateState.value.copy(
                isChecking = false,
                error = e.message
            )
            Result.failure(e)
        }
    }

    /**
     * Download and install update
     */
    suspend fun downloadAndInstall(manifest: UpdateManifest): Result<Unit> = withContext(Dispatchers.IO) {
        _updateState.value = _updateState.value.copy(
            isDownloading = true,
            downloadProgress = 0f,
            error = null
        )

        try {
            // Download APK
            val apkFile = downloadApk(manifest.url)

            _updateState.value = _updateState.value.copy(
                isDownloading = false,
                isInstalling = true
            )

            // Install APK
            installApk(apkFile)

            _updateState.value = _updateState.value.copy(isInstalling = false)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download/install update", e)
            _updateState.value = _updateState.value.copy(
                isDownloading = false,
                isInstalling = false,
                error = e.message
            )
            Result.failure(e)
        }
    }

    private suspend fun downloadApk(url: String): File = withContext(Dispatchers.IO) {
        Log.d(TAG, "Downloading APK from: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Download failed: ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val contentLength = body.contentLength()

        val apkFile = File(context.cacheDir, APK_FILENAME)
        apkFile.outputStream().use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (contentLength > 0) {
                        val progress = totalBytesRead.toFloat() / contentLength
                        _updateState.value = _updateState.value.copy(downloadProgress = progress)
                    }
                }
            }
        }

        Log.d(TAG, "APK downloaded: ${apkFile.length()} bytes")
        apkFile
    }

    private fun installApk(apkFile: File) {
        Log.d(TAG, "Installing APK: ${apkFile.absolutePath}")

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        try {
            // Write APK to session
            session.openWrite("update.apk", 0, apkFile.length()).use { output ->
                apkFile.inputStream().use { input ->
                    input.copyTo(output)
                }
                session.fsync(output)
            }

            // Create intent for install result
            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                action = "com.homecontrol.sensors.INSTALL_RESULT"
                putExtra("package", context.packageName)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                flags
            )

            // Commit the session
            session.commit(pendingIntent.intentSender)
            Log.d(TAG, "Install session committed")
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            // Clean up downloaded APK
            apkFile.delete()
        }
    }

    /**
     * Compare version strings (e.g., "1.4.0" vs "1.3.0")
     */
    private fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
        try {
            val newParts = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLength = maxOf(newParts.size, currentParts.size)
            for (i in 0 until maxLength) {
                val newPart = newParts.getOrElse(i) { 0 }
                val currentPart = currentParts.getOrElse(i) { 0 }

                if (newPart > currentPart) return true
                if (newPart < currentPart) return false
            }
            return false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compare versions: $newVersion vs $currentVersion", e)
            return false
        }
    }

    /**
     * Clear any error state
     */
    fun clearError() {
        _updateState.value = _updateState.value.copy(error = null)
    }

    /**
     * Clear available update (e.g., after user dismisses)
     */
    fun clearAvailableUpdate() {
        _updateState.value = _updateState.value.copy(availableUpdate = null)
    }
}
