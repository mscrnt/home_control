package com.homecontrol.sensors

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
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
    private lateinit var kioskButton: Button
    private lateinit var statusText: TextView
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, DeviceAdminReceiver::class.java)

        serverUrlInput = findViewById(R.id.serverUrl)
        idleTimeoutInput = findViewById(R.id.idleTimeout)
        saveButton = findViewById(R.id.saveButton)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        kioskButton = findViewById(R.id.kioskButton)
        statusText = findViewById(R.id.statusText)

        loadPreferences()

        saveButton.setOnClickListener { savePreferences() }
        startButton.setOnClickListener { startSensorService() }
        stopButton.setOnClickListener { stopSensorService() }
        kioskButton.setOnClickListener { launchKiosk() }

        // Update kiosk button state based on device owner status
        updateKioskButtonState()

        // Start service but DON'T auto-launch kiosk - let user do it manually
        autoStartService()
    }

    private fun autoStartService() {
        val intent = Intent(this, SensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        statusText.text = "Status: Running"
    }

    private fun updateKioskButtonState() {
        // Native app is always available
        kioskButton.isEnabled = true
        kioskButton.text = "Launch App"
    }

    private fun launchKiosk() {
        // Save settings first
        savePreferences()
        // Launch native activity
        startActivity(Intent(this, NativeActivity::class.java))
        finish()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(SensorService.PREF_NAME, Context.MODE_PRIVATE)
        serverUrlInput.setText(prefs.getString(SensorService.PREF_SERVER_URL, SensorService.DEFAULT_SERVER_URL))
        idleTimeoutInput.setText((prefs.getLong(SensorService.PREF_IDLE_TIMEOUT, 180000) / 1000).toString())
    }

    private fun savePreferences() {
        val serverUrl = serverUrlInput.text.toString().trim()
        val idleTimeout = (idleTimeoutInput.text.toString().toLongOrNull() ?: 180) * 1000

        getSharedPreferences(SensorService.PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SensorService.PREF_SERVER_URL, serverUrl)
            .putLong(SensorService.PREF_IDLE_TIMEOUT, idleTimeout)
            .apply()

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun startSensorService() {
        savePreferences()
        val intent = Intent(this, SensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        statusText.text = "Status: Running"
        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show()
    }

    private fun stopSensorService() {
        stopService(Intent(this, SensorService::class.java))
        statusText.text = "Status: Stopped"
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
    }
}
