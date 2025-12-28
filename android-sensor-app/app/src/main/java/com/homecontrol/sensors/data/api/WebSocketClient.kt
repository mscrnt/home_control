package com.homecontrol.sensors.data.api

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket events received from the server
 */
sealed class WebSocketEvent {
    /** Connection established */
    data object Connected : WebSocketEvent()

    /** Connection lost */
    data object Disconnected : WebSocketEvent()

    /** Doorbell ring detected */
    data class Doorbell(val camera: String) : WebSocketEvent()

    /** Proximity sensor triggered wake */
    data object ProximityWake : WebSocketEvent()

    /** Unknown event type */
    data class Unknown(val type: String, val payload: JsonObject?) : WebSocketEvent()
}

/**
 * WebSocket connection state
 */
enum class WebSocketState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

/**
 * WebSocket client for real-time events from the home control server.
 * Handles doorbell events, proximity wake events, and automatic reconnection.
 */
@Singleton
class WebSocketClient @Inject constructor(
    @com.homecontrol.sensors.di.ServerUrl private val serverUrl: String
) {
    companion object {
        private const val TAG = "WebSocketClient"
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val RETRY_MULTIPLIER = 2.0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private var webSocket: WebSocket? = null
    private var retryDelayMs = INITIAL_RETRY_DELAY_MS
    private var shouldReconnect = false

    private val _state = MutableStateFlow(WebSocketState.DISCONNECTED)
    val state: StateFlow<WebSocketState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .build()

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            _state.value = WebSocketState.CONNECTED
            retryDelayMs = INITIAL_RETRY_DELAY_MS
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "WebSocket message: $text")
            try {
                val event = parseEvent(text)
                scope.launch {
                    _events.emit(event)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse WebSocket message: $text", e)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
            handleDisconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}", t)
            handleDisconnect()
        }
    }

    /**
     * Connect to the WebSocket server.
     * Will automatically reconnect on disconnection.
     */
    fun connect() {
        if (_state.value == WebSocketState.CONNECTED || _state.value == WebSocketState.CONNECTING) {
            Log.d(TAG, "Already connected or connecting")
            return
        }

        shouldReconnect = true
        doConnect()
    }

    /**
     * Disconnect from the WebSocket server.
     * Disables automatic reconnection.
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        shouldReconnect = false
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _state.value = WebSocketState.DISCONNECTED
    }

    private fun doConnect() {
        val wsUrl = serverUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/') + "/ws"

        Log.d(TAG, "Connecting to WebSocket: $wsUrl")
        _state.value = WebSocketState.CONNECTING

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, listener)
    }

    private fun handleDisconnect() {
        webSocket = null

        scope.launch {
            _events.emit(WebSocketEvent.Disconnected)
        }

        if (shouldReconnect) {
            _state.value = WebSocketState.RECONNECTING
            scheduleReconnect()
        } else {
            _state.value = WebSocketState.DISCONNECTED
        }
    }

    private fun scheduleReconnect() {
        scope.launch {
            Log.d(TAG, "Reconnecting in ${retryDelayMs}ms")
            delay(retryDelayMs)

            // Exponential backoff
            retryDelayMs = (retryDelayMs * RETRY_MULTIPLIER).toLong()
                .coerceAtMost(MAX_RETRY_DELAY_MS)

            if (shouldReconnect) {
                doConnect()
            }
        }
    }

    private fun parseEvent(text: String): WebSocketEvent {
        val jsonElement = json.parseToJsonElement(text)
        val obj = jsonElement.jsonObject
        val type = obj["type"]?.jsonPrimitive?.content ?: return WebSocketEvent.Unknown("", null)
        val payload = obj["payload"]?.jsonObject

        return when (type) {
            "connected" -> WebSocketEvent.Connected
            "doorbell" -> {
                val camera = payload?.get("camera")?.jsonPrimitive?.content ?: "unknown"
                WebSocketEvent.Doorbell(camera)
            }
            "proximity_wake" -> WebSocketEvent.ProximityWake
            else -> WebSocketEvent.Unknown(type, payload)
        }
    }
}
