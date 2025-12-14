package com.homecontrol.sensors

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var serverUrlInput: EditText
    private lateinit var idleTimeoutInput: EditText
    private lateinit var saveButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverUrlInput = findViewById(R.id.serverUrl)
        idleTimeoutInput = findViewById(R.id.idleTimeout)
        saveButton = findViewById(R.id.saveButton)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusText = findViewById(R.id.statusText)

        loadPreferences()

        saveButton.setOnClickListener { savePreferences() }
        startButton.setOnClickListener { startService() }
        stopButton.setOnClickListener { stopService() }

        // Auto-start service if server URL is configured
        val prefs = getSharedPreferences(SensorService.PREF_NAME, Context.MODE_PRIVATE)
        val serverUrl = prefs.getString(SensorService.PREF_SERVER_URL, "") ?: ""
        if (serverUrl.isNotEmpty() && serverUrl != "http://192.168.1.100:8080") {
            startService()
            // Minimize to background after starting
            moveTaskToBack(true)
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(SensorService.PREF_NAME, Context.MODE_PRIVATE)
        serverUrlInput.setText(prefs.getString(SensorService.PREF_SERVER_URL, "http://192.168.1.100:8080"))
        idleTimeoutInput.setText((prefs.getLong(SensorService.PREF_IDLE_TIMEOUT, 60000) / 1000).toString())
    }

    private fun savePreferences() {
        val serverUrl = serverUrlInput.text.toString().trim()
        val idleTimeout = (idleTimeoutInput.text.toString().toLongOrNull() ?: 60) * 1000

        getSharedPreferences(SensorService.PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SensorService.PREF_SERVER_URL, serverUrl)
            .putLong(SensorService.PREF_IDLE_TIMEOUT, idleTimeout)
            .apply()

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()

        // Restart service if running to pick up new settings
        stopService()
        startService()
    }

    private fun startService() {
        val intent = Intent(this, SensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        statusText.text = "Status: Running"
        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show()
    }

    private fun stopService() {
        stopService(Intent(this, SensorService::class.java))
        statusText.text = "Status: Stopped"
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
    }
}
