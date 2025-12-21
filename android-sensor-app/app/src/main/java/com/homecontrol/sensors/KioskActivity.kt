package com.homecontrol.sensors

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.*
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

class KioskActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    // Secret exit: tap top-left corner 7 times within 3 seconds
    private var exitTapCount = 0
    private var firstTapTime = 0L
    private val EXIT_TAP_ZONE = 100 // pixels from corner
    private val EXIT_TAP_COUNT = 7
    private val EXIT_TAP_TIMEOUT = 3000L // 3 seconds

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, DeviceAdminReceiver::class.java)

        // Configure lock task features BEFORE setting content view
        configureLockTaskFeatures()

        setContentView(R.layout.activity_kiosk)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        // Configure WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                // Retry loading after a delay on error
                if (request?.isForMainFrame == true) {
                    webView.postDelayed({
                        loadUrl()
                    }, 3000)
                }
            }
        }

        webView.webChromeClient = WebChromeClient()

        // Enable immersive fullscreen
        enableImmersiveMode()

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Start lock task mode if we're device owner
        startLockTaskIfOwner()

        // Start the sensor service
        startSensorService()

        // Load the URL
        loadUrl()
    }

    private fun configureLockTaskFeatures() {
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            // Set allowed packages for lock task mode (our app + Settings)
            devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(
                packageName,
                "com.android.settings"
            ))

            // Configure lock task features
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // SYSTEM_INFO shows status bar, NOTIFICATIONS allows pull-down
                // GLOBAL_ACTIONS allows power menu access (includes settings shortcut)
                // Note: NOTIFICATIONS requires HOME which shows nav bar
                devicePolicyManager.setLockTaskFeatures(adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                    DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                )
            }

            // Disable keyguard and hide setup wizard
            try {
                devicePolicyManager.setKeyguardDisabled(adminComponent, true)
                // Hide packages that interfere
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    devicePolicyManager.setApplicationHidden(adminComponent, "com.google.android.setupwizard", true)
                    devicePolicyManager.setApplicationHidden(adminComponent, "com.android.provision", true)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun loadUrl() {
        val prefs = getSharedPreferences(SensorService.PREF_NAME, Context.MODE_PRIVATE)
        val serverUrl = prefs.getString(SensorService.PREF_SERVER_URL, SensorService.DEFAULT_SERVER_URL)
            ?: SensorService.DEFAULT_SERVER_URL

        progressBar.visibility = View.VISIBLE
        webView.loadUrl(serverUrl)
    }

    private fun startSensorService() {
        val intent = Intent(this, SensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startLockTaskIfOwner() {
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            // Start lock task mode
            startLockTask()
        }
    }

    @Suppress("DEPRECATION")
    private fun enableImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                // Only hide navigation bar, keep status bar for notification tray access
                it.hide(WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Only hide navigation bar, keep status bar visible for notifications
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    override fun onBackPressed() {
        // Disable back button in kiosk mode
        if (webView.canGoBack()) {
            webView.goBack()
        }
        // Don't call super - prevent exiting
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
            // Check if tap is in top-left corner
            if (ev.x < EXIT_TAP_ZONE && ev.y < EXIT_TAP_ZONE) {
                val now = System.currentTimeMillis()
                if (now - firstTapTime > EXIT_TAP_TIMEOUT) {
                    // Reset if too much time passed
                    exitTapCount = 0
                    firstTapTime = now
                }
                exitTapCount++
                if (exitTapCount == 1) {
                    firstTapTime = now
                }
                if (exitTapCount >= EXIT_TAP_COUNT) {
                    exitTapCount = 0
                    exitKioskMode()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun exitKioskMode() {
        android.util.Log.d("KioskActivity", "Secret exit triggered - exiting kiosk mode")
        try {
            stopLockTask()
        } catch (e: Exception) {
            // Ignore
        }
        // Go to MainActivity for settings
        val intent = android.content.Intent(this, MainActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop lock task when activity is destroyed
        try {
            stopLockTask()
        } catch (e: Exception) {
            // Ignore if not in lock task mode
        }
    }
}
