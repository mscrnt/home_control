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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hue SSE events received from the server
 */
sealed class HueSSEEvent {
    /** Connection established */
    data object Connected : HueSSEEvent()

    /** Connection lost */
    data object Disconnected : HueSSEEvent()

    /** Light state changed */
    data class LightUpdate(val id: String, val on: Boolean?, val brightness: Int?) : HueSSEEvent()

    /** Room/group state changed */
    data class GroupUpdate(val id: String, val on: Boolean?, val brightness: Int?) : HueSSEEvent()

    /** Scene activated */
    data class SceneUpdate(val id: String, val active: Boolean) : HueSSEEvent()

    /** Generic update - triggers data refresh */
    data object DataChanged : HueSSEEvent()
}

/**
 * SSE connection state
 */
enum class HueSSEState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

/**
 * SSE client for real-time Hue bridge events.
 * Connects to /api/hue/events and receives light/scene updates.
 */
@Singleton
class HueEventClient @Inject constructor(
    @com.homecontrol.sensors.di.ServerUrl private val serverUrl: String
) {
    companion object {
        private const val TAG = "HueEventClient"
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val RETRY_MULTIPLIER = 2.0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private var eventSource: EventSource? = null
    private var retryDelayMs = INITIAL_RETRY_DELAY_MS
    private var shouldReconnect = false

    private val _state = MutableStateFlow(HueSSEState.DISCONNECTED)
    val state: StateFlow<HueSSEState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<HueSSEEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<HueSSEEvent> = _events.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for SSE
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    private val listener = object : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            Log.d(TAG, "SSE connected to Hue events")
            _state.value = HueSSEState.CONNECTED
            retryDelayMs = INITIAL_RETRY_DELAY_MS
            scope.launch {
                _events.emit(HueSSEEvent.Connected)
            }
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            Log.d(TAG, "SSE event: type=$type, data=$data")
            try {
                val event = parseEvent(type, data)
                scope.launch {
                    _events.emit(event)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse SSE event: $data", e)
            }
        }

        override fun onClosed(eventSource: EventSource) {
            Log.d(TAG, "SSE connection closed")
            handleDisconnect()
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            Log.e(TAG, "SSE failure: ${t?.message}", t)
            handleDisconnect()
        }
    }

    /**
     * Connect to the SSE server.
     * Will automatically reconnect on disconnection.
     */
    fun connect() {
        if (_state.value == HueSSEState.CONNECTED || _state.value == HueSSEState.CONNECTING) {
            Log.d(TAG, "Already connected or connecting")
            return
        }

        shouldReconnect = true
        doConnect()
    }

    /**
     * Disconnect from the SSE server.
     * Disables automatic reconnection.
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting SSE")
        shouldReconnect = false
        eventSource?.cancel()
        eventSource = null
        _state.value = HueSSEState.DISCONNECTED
    }

    private fun doConnect() {
        val sseUrl = serverUrl.trimEnd('/') + "/api/hue/events"
        Log.d(TAG, "Connecting to SSE: $sseUrl")
        _state.value = HueSSEState.CONNECTING

        val request = Request.Builder()
            .url(sseUrl)
            .header("Accept", "text/event-stream")
            .build()

        eventSource = EventSources.createFactory(client)
            .newEventSource(request, listener)
    }

    private fun handleDisconnect() {
        eventSource = null

        scope.launch {
            _events.emit(HueSSEEvent.Disconnected)
        }

        if (shouldReconnect) {
            _state.value = HueSSEState.RECONNECTING
            scheduleReconnect()
        } else {
            _state.value = HueSSEState.DISCONNECTED
        }
    }

    private fun scheduleReconnect() {
        scope.launch {
            Log.d(TAG, "Reconnecting in ${retryDelayMs}ms")
            delay(retryDelayMs)

            // Exponential backoff
            retryDelayMs = (retryDelayMs * RETRY_MULTIPLIER).toLong()
                .coerceAtMost(MAX_RETRY_DELAY_MS)

            if (shouldReconnect && isActive) {
                doConnect()
            }
        }
    }

    private fun parseEvent(type: String?, data: String): HueSSEEvent {
        // Handle connection event
        if (type == "connected") {
            return HueSSEEvent.Connected
        }

        // Try to parse as HueEvent JSON
        try {
            val jsonElement = json.parseToJsonElement(data)
            val obj = jsonElement as? kotlinx.serialization.json.JsonObject ?: return HueSSEEvent.DataChanged

            val eventType = obj["type"]?.let {
                (it as? kotlinx.serialization.json.JsonPrimitive)?.content
            } ?: type ?: ""

            val id = obj["id"]?.let {
                (it as? kotlinx.serialization.json.JsonPrimitive)?.content
            } ?: ""

            val eventData = obj["data"] as? kotlinx.serialization.json.JsonObject

            return when (eventType) {
                "light", "update" -> {
                    // Check for on/dimming changes in data
                    val on = eventData?.get("on")?.let { onObj ->
                        (onObj as? kotlinx.serialization.json.JsonObject)?.get("on")?.let {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull()
                        }
                    }
                    val brightness = eventData?.get("dimming")?.let { dimObj ->
                        (dimObj as? kotlinx.serialization.json.JsonObject)?.get("brightness")?.let {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
                        }
                    }
                    HueSSEEvent.LightUpdate(id, on, brightness)
                }
                "grouped_light" -> {
                    val on = eventData?.get("on")?.let { onObj ->
                        (onObj as? kotlinx.serialization.json.JsonObject)?.get("on")?.let {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull()
                        }
                    }
                    val brightness = eventData?.get("dimming")?.let { dimObj ->
                        (dimObj as? kotlinx.serialization.json.JsonObject)?.get("brightness")?.let {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
                        }
                    }
                    HueSSEEvent.GroupUpdate(id, on, brightness)
                }
                "scene" -> {
                    val active = eventData?.get("status")?.let { statusObj ->
                        (statusObj as? kotlinx.serialization.json.JsonObject)?.get("active")?.let {
                            val value = (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                            value == "static" || value == "dynamic_palette"
                        }
                    } ?: false
                    HueSSEEvent.SceneUpdate(id, active)
                }
                else -> HueSSEEvent.DataChanged
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse event data", e)
            return HueSSEEvent.DataChanged
        }
    }
}
