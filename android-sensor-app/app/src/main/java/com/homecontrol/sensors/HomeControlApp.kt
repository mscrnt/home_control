package com.homecontrol.sensors

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HomeControlApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // App initialization happens here
        // Hilt will handle dependency injection setup automatically
    }
}
