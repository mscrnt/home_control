package com.homecontrol.sensors.data.sync

import android.content.Context
import android.os.Environment
import android.util.Log
import com.homecontrol.sensors.data.api.HomeControlApi
import com.homecontrol.sensors.data.model.DrivePhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class PhotoSyncState(
    val isSyncing: Boolean = false,
    val lastSyncTime: Long = 0,
    val cachedPhotoCount: Int = 0,
    val totalPhotoCount: Int = 0,
    val currentDownload: String? = null
)

@Singleton
class PhotoSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: HomeControlApi,
    private val okHttpClient: OkHttpClient,
    @com.homecontrol.sensors.di.ServerUrl private val serverUrl: String
) {
    companion object {
        private const val TAG = "PhotoSyncManager"
        private const val PHOTOS_DIR = "screensaver_photos"
        private const val METADATA_FILE = "photo_metadata.txt"
    }

    private val _syncState = MutableStateFlow(PhotoSyncState())
    val syncState: StateFlow<PhotoSyncState> = _syncState.asStateFlow()

    private val photosDir: File by lazy {
        // Try external storage first (SD card), fall back to internal
        val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val dir = if (externalDir != null && externalDir.canWrite()) {
            File(externalDir, PHOTOS_DIR)
        } else {
            File(context.filesDir, PHOTOS_DIR)
        }
        dir.also { it.mkdirs() }
    }

    init {
        // Load initial state
        updateCachedPhotoCount()
    }

    /**
     * Get the local file path for a cached photo, or null if not cached.
     */
    fun getCachedPhotoPath(photoId: String): String? {
        val file = File(photosDir, "$photoId.jpg")
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * Get all cached photo files.
     */
    fun getCachedPhotos(): List<File> {
        return photosDir.listFiles { file -> file.extension == "jpg" }?.toList() ?: emptyList()
    }

    /**
     * Check if a photo is cached locally.
     */
    fun isPhotoCached(photoId: String): Boolean {
        return File(photosDir, "$photoId.jpg").exists()
    }

    /**
     * Sync photos from the server.
     * Downloads new photos and removes deleted ones.
     */
    suspend fun syncPhotos(): Result<Int> = withContext(Dispatchers.IO) {
        if (_syncState.value.isSyncing) {
            Log.d(TAG, "Sync already in progress, skipping")
            return@withContext Result.success(0)
        }

        try {
            _syncState.value = _syncState.value.copy(isSyncing = true)
            Log.d(TAG, "Starting photo sync...")

            // Fetch photo list from server
            val response = api.getDrivePhotos()
            if (!response.isSuccessful) {
                throw Exception("Failed to fetch photo list: ${response.code()}")
            }

            val serverPhotos = response.body() ?: emptyList()
            val serverPhotoIds = serverPhotos.map { it.id }.toSet()

            _syncState.value = _syncState.value.copy(totalPhotoCount = serverPhotos.size)
            Log.d(TAG, "Server has ${serverPhotos.size} photos")

            // Get currently cached photos
            val cachedFiles = getCachedPhotos()
            val cachedPhotoIds = cachedFiles.map { it.nameWithoutExtension }.toSet()

            // Find photos to download (on server but not cached)
            val toDownload = serverPhotos.filter { it.id !in cachedPhotoIds }
            Log.d(TAG, "Need to download ${toDownload.size} new photos")

            // Find photos to delete (cached but not on server)
            val toDelete = cachedPhotoIds - serverPhotoIds
            Log.d(TAG, "Need to delete ${toDelete.size} old photos")

            // Delete old photos
            for (photoId in toDelete) {
                val file = File(photosDir, "$photoId.jpg")
                if (file.delete()) {
                    Log.d(TAG, "Deleted cached photo: $photoId")
                }
            }

            // Download new photos
            var downloadCount = 0
            for (photo in toDownload) {
                try {
                    _syncState.value = _syncState.value.copy(currentDownload = photo.name)
                    downloadPhoto(photo)
                    downloadCount++
                    updateCachedPhotoCount()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download photo ${photo.id}: ${e.message}")
                }
            }

            val lastSyncTime = System.currentTimeMillis()
            saveLastSyncTime(lastSyncTime)

            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncTime = lastSyncTime,
                currentDownload = null
            )
            updateCachedPhotoCount()

            Log.d(TAG, "Photo sync complete. Downloaded $downloadCount photos.")
            Result.success(downloadCount)

        } catch (e: Exception) {
            Log.e(TAG, "Photo sync failed: ${e.message}", e)
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                currentDownload = null
            )
            Result.failure(e)
        }
    }

    private suspend fun downloadPhoto(photo: DrivePhoto) = withContext(Dispatchers.IO) {
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val photoUrl = "${baseUrl}api/drive/photo/${photo.id}"
        val request = Request.Builder()
            .url(photoUrl)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Download failed: ${response.code}")
        }

        val file = File(photosDir, "${photo.id}.jpg")
        response.body?.byteStream()?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }

        Log.d(TAG, "Downloaded photo: ${photo.name} (${file.length() / 1024} KB)")
    }

    private fun updateCachedPhotoCount() {
        val count = getCachedPhotos().size
        _syncState.value = _syncState.value.copy(cachedPhotoCount = count)
    }

    private fun saveLastSyncTime(time: Long) {
        try {
            File(photosDir, METADATA_FILE).writeText(time.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save last sync time", e)
        }
    }

    fun getLastSyncTime(): Long {
        return try {
            File(photosDir, METADATA_FILE).readText().toLong()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Clear all cached photos.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        photosDir.listFiles()?.forEach { it.delete() }
        updateCachedPhotoCount()
        Log.d(TAG, "Cache cleared")
    }

    /**
     * Get the total size of cached photos in bytes.
     */
    fun getCacheSize(): Long {
        return getCachedPhotos().sumOf { it.length() }
    }
}
