package com.homecontrol.sensors

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.homecontrol.sensors.data.sync.PhotoSyncManager
import com.homecontrol.sensors.data.sync.PhotoSyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HomeControlApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var photoSyncManager: PhotoSyncManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // App initialization happens here
        // Hilt will handle dependency injection setup automatically

        // Schedule hourly photo sync
        PhotoSyncWorker.scheduleHourlySync(this)

        // Trigger initial sync on app start
        applicationScope.launch {
            Log.d("HomeControlApp", "Starting initial photo sync...")
            photoSyncManager.syncPhotos()
        }
    }
}
