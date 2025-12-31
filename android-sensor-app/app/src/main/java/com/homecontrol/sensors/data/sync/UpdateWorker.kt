package com.homecontrol.sensors.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.homecontrol.sensors.data.repository.UpdateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val updateRepository: UpdateRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "UpdateWorker"
        private const val WORK_NAME = "update_check_work"

        /**
         * Schedule the update worker to run every 15 minutes.
         */
        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<UpdateWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.d(TAG, "Scheduled update check every 15 minutes")
        }

        /**
         * Cancel the scheduled update check.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled update check schedule")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Update check worker started")

        return try {
            val result = updateRepository.checkForUpdate()

            result.fold(
                onSuccess = { manifest ->
                    if (manifest != null) {
                        Log.i(TAG, "Update available: ${manifest.version}, auto-installing...")
                        // Auto-install the update
                        val installResult = updateRepository.downloadAndInstall(manifest)
                        if (installResult.isSuccess) {
                            Log.i(TAG, "Update installed successfully")
                        } else {
                            Log.e(TAG, "Update install failed: ${installResult.exceptionOrNull()?.message}")
                        }
                    } else {
                        Log.d(TAG, "No update available")
                    }
                    Result.success()
                },
                onFailure = { error ->
                    Log.e(TAG, "Update check failed: ${error.message}")
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update check worker failed", e)
            Result.retry()
        }
    }
}
