package com.homecontrol.sensors.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class PhotoSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val photoSyncManager: PhotoSyncManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "PhotoSyncWorker"
        private const val WORK_NAME = "photo_sync_work"

        /**
         * Schedule the photo sync worker to run every hour.
         */
        fun scheduleHourlySync(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<PhotoSyncWorker>(
                1, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.d(TAG, "Scheduled hourly photo sync")
        }

        /**
         * Cancel the scheduled photo sync.
         */
        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled photo sync schedule")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Photo sync worker started")

        return try {
            val result = photoSyncManager.syncPhotos()
            if (result.isSuccess) {
                Log.d(TAG, "Photo sync completed successfully: ${result.getOrNull()} photos downloaded")
                Result.success()
            } else {
                Log.e(TAG, "Photo sync failed: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Photo sync worker failed", e)
            Result.retry()
        }
    }
}
