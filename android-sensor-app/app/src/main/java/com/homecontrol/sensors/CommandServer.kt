package com.homecontrol.sensors

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import fi.iki.elonen.NanoHTTPD
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
        val params = session.parms
        val lines = params["lines"]?.toIntOrNull() ?: 100
        val filter = params["filter"] ?: ""

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
        Log.d(TAG, "WebView reload requested")

        // Send broadcast to KioskActivity to reload WebView
        val intent = Intent(KioskActivity.ACTION_RELOAD)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            """{"status": "reload_triggered"}"""
        )
    }

    private fun handleExitKiosk(): Response {
        Log.d(TAG, "Exit kiosk mode requested")

        // Send broadcast to KioskActivity to exit kiosk mode
        val intent = Intent(KioskActivity.ACTION_EXIT_KIOSK)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            """{"status": "exit_kiosk_triggered"}"""
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
