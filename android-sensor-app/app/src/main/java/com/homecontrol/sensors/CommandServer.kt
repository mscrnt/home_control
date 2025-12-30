package com.homecontrol.sensors

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.PowerManager
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class CommandServer(
    private val context: Context,
    port: Int = 8888
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "CommandServer"
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var screenWakeLock: PowerManager.WakeLock? = null

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(TAG, "Request: $method $uri")

        return try {
            when {
                uri == "/status" && method == Method.GET -> handleStatus()
                uri == "/exec" && method == Method.POST -> handleExec(session)
                uri == "/logcat" && method == Method.GET -> handleLogcat(session)
                uri == "/screenshot" && method == Method.GET -> handleScreenshot()
                uri == "/reboot" && method == Method.POST -> handleReboot()
                uri == "/reload" && method == Method.POST -> handleReload()
                uri == "/kiosk/exit" && method == Method.POST -> handleExitKiosk()
                uri == "/theme" && method == Method.GET -> handleTheme()
                uri == "/apps" && method == Method.GET -> handleListApps(session)
                uri == "/apps/hide" && method == Method.POST -> handleHideApp(session)
                uri == "/apps/unhide" && method == Method.POST -> handleUnhideApp(session)
                uri == "/bloatware" && method == Method.GET -> handleGetBlocklist()
                uri == "/bloatware/remove" && method == Method.POST -> handleRemoveBloatware()
                uri == "/managed-apps" && method == Method.GET -> handleGetManagedApps()
                uri == "/managed-apps/update" && method == Method.POST -> handleUpdateManagedApps()
                uri == "/wake" && method == Method.POST -> handleWake()
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "application/json",
                    """{"error": "Not found"}"""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request: ${e.message}")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error": "${e.message?.replace("\"", "'")}"}"""
            )
        }
    }

    private fun handleStatus(): Response {
        val json = JSONObject().apply {
            put("status", "ok")
            put("device", android.os.Build.MODEL)
            put("android", android.os.Build.VERSION.RELEASE)
            put("sdk", android.os.Build.VERSION.SDK_INT)
            put("ip", getWifiIpAddress())
        }
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.toString()
        )
    }

    private fun handleExec(session: IHTTPSession): Response {
        // Read the POST body
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        val buffer = ByteArray(contentLength)
        session.inputStream.read(buffer, 0, contentLength)
        val body = String(buffer)

        val json = JSONObject(body)
        val command = json.optString("command", "")
        val timeout = json.optLong("timeout", 30000)

        if (command.isEmpty()) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                """{"error": "Missing 'command' parameter"}"""
            )
        }

        Log.d(TAG, "Executing command: $command")

        val result = executeCommand(command, timeout)
        val response = JSONObject().apply {
            put("command", command)
            put("exitCode", result.exitCode)
            put("stdout", result.stdout)
            put("stderr", result.stderr)
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            response.toString()
        )
    }

    private fun handleLogcat(session: IHTTPSession): Response {
        val params = session.parameters
        val lines = params["lines"]?.firstOrNull()?.toIntOrNull() ?: 100
        val filter = params["filter"]?.firstOrNull() ?: ""

        val command = if (filter.isNotEmpty()) {
            "logcat -d -t $lines | grep -i '$filter'"
        } else {
            "logcat -d -t $lines"
        }

        val result = executeCommand(command, 10000)
        val response = JSONObject().apply {
            put("logcat", result.stdout)
            put("lines", lines)
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            response.toString()
        )
    }

    private fun handleScreenshot(): Response {
        // Take a screenshot using screencap command
        val timestamp = System.currentTimeMillis()
        val filename = "/sdcard/screenshot_$timestamp.png"

        val result = executeCommand("screencap -p $filename", 10000)

        if (result.exitCode == 0) {
            val file = java.io.File(filename)
            if (file.exists()) {
                val bytes = file.readBytes()
                file.delete()

                return newFixedLengthResponse(
                    Response.Status.OK,
                    "image/png",
                    java.io.ByteArrayInputStream(bytes),
                    bytes.size.toLong()
                )
            }
        }

        return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR,
            "application/json",
            """{"error": "Failed to take screenshot: ${result.stderr}"}"""
        )
    }

    private fun handleReboot(): Response {
        Log.w(TAG, "Reboot requested")

        // Schedule reboot in 2 seconds to allow response to be sent
        Thread {
            Thread.sleep(2000)
            executeCommand("reboot", 5000)
        }.start()

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            """{"status": "rebooting"}"""
        )
    }

    private fun handleReload(): Response {
        Log.d(TAG, "WebView reload requested - not supported in native mode")
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            """{"status": "not_supported", "message": "WebView reload not available in native mode"}"""
        )
    }

    private fun handleExitKiosk(): Response {
        Log.d(TAG, "Exit kiosk mode requested - not supported in native mode")
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            """{"status": "not_supported", "message": "Kiosk mode not available in native mode"}"""
        )
    }

    private fun handleTheme(): Response {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = nightMode == Configuration.UI_MODE_NIGHT_YES
        Log.d(TAG, "Theme query: ${if (isDark) "dark" else "light"}")

        val json = JSONObject().apply {
            put("dark", isDark)
            put("theme", if (isDark) "dark" else "light")
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.toString()
        )
    }

    private fun handleListApps(session: IHTTPSession): Response {
        val params = session.parameters
        val showSystem = params["system"]?.firstOrNull()?.toBoolean() ?: true
        val showHidden = params["hidden"]?.firstOrNull()?.toBoolean() ?: false

        val pm = context.packageManager
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)

        // Check if we're device owner - only then can we query/set hidden status
        val isDeviceOwner = try {
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check device owner status: ${e.message}")
            false
        }

        val apps = JSONArray()
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (appInfo in packages) {
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            // Skip system apps if not requested
            if (isSystem && !showSystem) continue

            // Skip our own app
            if (appInfo.packageName == context.packageName) continue

            // Only check hidden status if we're device owner
            val isHidden = if (isDeviceOwner) {
                try {
                    dpm.isApplicationHidden(adminComponent, appInfo.packageName)
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }

            // Skip hidden apps if not requested
            if (isHidden && !showHidden) continue

            val appJson = JSONObject().apply {
                put("package", appInfo.packageName)
                put("name", pm.getApplicationLabel(appInfo).toString())
                put("system", isSystem)
                put("hidden", isHidden)
            }
            apps.put(appJson)
        }

        val response = JSONObject().apply {
            put("apps", apps)
            put("count", apps.length())
            put("isDeviceOwner", isDeviceOwner)
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            response.toString()
        )
    }

    private fun handleHideApp(session: IHTTPSession): Response {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        val buffer = ByteArray(contentLength)
        session.inputStream.read(buffer, 0, contentLength)
        val body = String(buffer)

        val json = JSONObject(body)
        val packageName = json.optString("package", "")

        if (packageName.isEmpty()) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                """{"error": "Missing 'package' parameter"}"""
            )
        }

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)

        // Check if we're device owner
        val isDeviceOwner = try {
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }

        if (!isDeviceOwner) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "application/json",
                """{"error": "Not device owner - cannot hide apps"}"""
            )
        }

        // Don't allow hiding our own app or critical system components
        val protectedPackages = listOf(
            context.packageName,
            "com.android.settings",
            "com.android.systemui"
        )
        if (packageName in protectedPackages) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                """{"error": "Cannot hide protected package: $packageName"}"""
            )
        }

        return try {
            val success = dpm.setApplicationHidden(adminComponent, packageName, true)
            Log.d(TAG, "Hide app $packageName: $success")

            val response = JSONObject().apply {
                put("package", packageName)
                put("hidden", success)
            }
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                response.toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide app $packageName: ${e.message}")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error": "${e.message?.replace("\"", "'")}"}"""
            )
        }
    }

    private fun handleUnhideApp(session: IHTTPSession): Response {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        val buffer = ByteArray(contentLength)
        session.inputStream.read(buffer, 0, contentLength)
        val body = String(buffer)

        val json = JSONObject(body)
        val packageName = json.optString("package", "")

        if (packageName.isEmpty()) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                """{"error": "Missing 'package' parameter"}"""
            )
        }

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)

        // Check if we're device owner
        val isDeviceOwner = try {
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }

        if (!isDeviceOwner) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "application/json",
                """{"error": "Not device owner - cannot unhide apps"}"""
            )
        }

        return try {
            val success = dpm.setApplicationHidden(adminComponent, packageName, false)
            Log.d(TAG, "Unhide app $packageName: $success")

            val response = JSONObject().apply {
                put("package", packageName)
                put("hidden", !success) // If unhide succeeded, hidden is now false
            }
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                response.toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unhide app $packageName: ${e.message}")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error": "${e.message?.replace("\"", "'")}"}"""
            )
        }
    }

    private fun handleGetBlocklist(): Response {
        val blocklist = BloatwareManager.getBlocklist()
        val json = JSONObject().apply {
            put("blocklist", JSONArray(blocklist))
            put("count", blocklist.size)
        }
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.toString()
        )
    }

    private fun handleRemoveBloatware(): Response {
        Log.d(TAG, "Manual bloatware removal triggered")

        // Run in background and return immediately
        Thread {
            BloatwareManager.removeAllBloatware(context)
        }.start()

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            """{"status": "removal_started"}"""
        )
    }

    private fun handleGetManagedApps(): Response {
        val managedApps = ManagedAppsManager.getManagedApps()
        val pm = context.packageManager

        val apps = JSONArray()
        for ((packageName, url) in managedApps) {
            val installedVersion = try {
                pm.getPackageInfo(packageName, 0).versionName
            } catch (e: Exception) {
                null
            }

            val appJson = JSONObject().apply {
                put("package", packageName)
                put("url", url)
                put("installed", installedVersion != null)
                put("version", installedVersion ?: "not installed")
            }
            apps.put(appJson)
        }

        val response = JSONObject().apply {
            put("apps", apps)
            put("count", apps.length())
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            response.toString()
        )
    }

    private fun handleUpdateManagedApps(): Response {
        Log.d(TAG, "Manual managed apps update triggered")

        ManagedAppsManager.forceUpdate(context)

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            """{"status": "update_started"}"""
        )
    }

    @Suppress("DEPRECATION")
    private fun handleWake(): Response {
        Log.d(TAG, "Wake/doorbell request received")

        try {
            // Wake the screen using PowerManager wake lock (works without root/shell)
            screenWakeLock?.release()
            screenWakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "HomeControlSensors::DoorbellWakeLock"
            ).apply {
                acquire(10000) // Hold for 10 seconds, then release
            }
            Log.d(TAG, "Screen woken via wake lock")

            // Also send proximity broadcast to dismiss screensaver
            val intent = Intent(SensorService.ACTION_PROXIMITY).apply {
                putExtra(SensorService.EXTRA_NEAR, true)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)

            Log.d(TAG, "Proximity broadcast sent to dismiss screensaver")

            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                """{"status": "wake_triggered"}"""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Wake failed: ${e.message}")
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error": "${e.message?.replace("\"", "'")}"}"""
            )
        }
    }

    private data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    private fun executeCommand(command: String, timeoutMs: Long): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            val stdout = StringBuilder()
            val stderr = StringBuilder()

            // Read output in separate threads
            val stdoutThread = Thread {
                try {
                    stdoutReader.forEachLine { stdout.appendLine(it) }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            val stderrThread = Thread {
                try {
                    stderrReader.forEachLine { stderr.appendLine(it) }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            stdoutThread.start()
            stderrThread.start()

            // Wait for process with timeout
            val completed = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)

            if (!completed) {
                process.destroyForcibly()
                return CommandResult(-1, stdout.toString(), "Command timed out after ${timeoutMs}ms")
            }

            stdoutThread.join(1000)
            stderrThread.join(1000)

            CommandResult(
                process.exitValue(),
                stdout.toString().trim(),
                stderr.toString().trim()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: ${e.message}")
            CommandResult(-1, "", e.message ?: "Unknown error")
        }
    }

    private fun getWifiIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.name.startsWith("wlan") || iface.name.startsWith("eth")) {
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            return addr.hostAddress ?: ""
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get WiFi IP: ${e.message}")
        }
        return "unknown"
    }
}
