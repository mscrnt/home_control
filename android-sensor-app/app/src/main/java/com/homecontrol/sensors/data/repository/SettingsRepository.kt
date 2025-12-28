package com.homecontrol.sensors.data.repository

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class AppMode {
    NATIVE, KIOSK
}

data class AppSettings(
    val serverUrl: String = "http://192.168.69.229:8080/",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appMode: AppMode = AppMode.NATIVE,
    val idleTimeout: Int = 60, // seconds
    val keepScreenOn: Boolean = true,
    val showNotifications: Boolean = true
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_APP_MODE = stringPreferencesKey("app_mode")
        private val KEY_IDLE_TIMEOUT = intPreferencesKey("idle_timeout")
        private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val KEY_SHOW_NOTIFICATIONS = booleanPreferencesKey("show_notifications")

        // SharedPreferences keys (for compatibility with SensorService)
        private const val PREFS_KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "http://192.168.69.229:8080/"
    }

    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            serverUrl = sharedPreferences.getString(PREFS_KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL,
            themeMode = ThemeMode.valueOf(preferences[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name),
            appMode = AppMode.valueOf(preferences[KEY_APP_MODE] ?: AppMode.NATIVE.name),
            idleTimeout = preferences[KEY_IDLE_TIMEOUT] ?: 60,
            keepScreenOn = preferences[KEY_KEEP_SCREEN_ON] ?: true,
            showNotifications = preferences[KEY_SHOW_NOTIFICATIONS] ?: true
        )
    }

    suspend fun setServerUrl(url: String) {
        sharedPreferences.edit().putString(PREFS_KEY_SERVER_URL, url).apply()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setAppMode(mode: AppMode) {
        dataStore.edit { preferences ->
            preferences[KEY_APP_MODE] = mode.name
        }
    }

    suspend fun setIdleTimeout(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_IDLE_TIMEOUT] = seconds
        }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun setShowNotifications(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SHOW_NOTIFICATIONS] = enabled
        }
    }
}
