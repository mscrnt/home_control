package com.homecontrol.sensors.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EntertainmentDevices(
    val sony: List<SonyDevice> = emptyList(),
    val shield: List<ShieldDevice> = emptyList(),
    val xbox: List<XboxDevice> = emptyList(),
    val ps5: List<PS5Device> = emptyList()
)

// Sony TV
@Serializable
data class SonyDevice(
    val name: String,
    val ip: String? = null
)

@Serializable
data class SonyState(
    val power: Boolean,
    val volume: Int = 0,
    val muted: Boolean = false,
    val input: String? = null,
    val inputs: List<SonyInput> = emptyList()
)

@Serializable
data class SonyInput(
    val uri: String,
    val title: String,
    val label: String? = null
)

// Nvidia Shield
@Serializable
data class ShieldDevice(
    val name: String,
    val ip: String? = null
)

@Serializable
data class ShieldState(
    val power: Boolean,
    @SerialName("current_app")
    val currentApp: String? = null,
    val apps: List<ShieldApp> = emptyList()
)

@Serializable
data class ShieldApp(
    val name: String,
    @SerialName("package")
    val packageName: String
)

// Xbox
@Serializable
data class XboxDevice(
    val name: String,
    @SerialName("live_id")
    val liveId: String? = null
)

@Serializable
data class XboxState(
    val power: Boolean,
    @SerialName("power_state")
    val powerState: String? = null
)

// PS5
@Serializable
data class PS5Device(
    val name: String,
    val host: String? = null
)

@Serializable
data class PS5State(
    val power: Boolean,
    val status: String? = null
)

// Common request models
@Serializable
data class PowerRequest(
    val power: Boolean
)

@Serializable
data class MuteRequest(
    val mute: Boolean
)

@Serializable
data class AppRequest(
    val app: String
)

// Camera models
@Serializable
data class Camera(
    val name: String,
    val url: String? = null,
    @SerialName("snapshot_url")
    val snapshotUrl: String? = null,
    @SerialName("stream_url")
    val streamUrl: String? = null,
    @SerialName("has_audio")
    val hasAudio: Boolean = false,
    @SerialName("has_talk")
    val hasTalk: Boolean = false
)

// Drive/Screensaver models
@Serializable
data class DrivePhoto(
    val id: String,
    val name: String,
    val url: String? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
    @SerialName("mime_type")
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class ScreensaverConfig(
    val enabled: Boolean = true,
    @SerialName("idle_timeout")
    val idleTimeout: Int = 300,
    @SerialName("slide_interval")
    val slideInterval: Int = 90,
    @SerialName("show_clock")
    val showClock: Boolean = true,
    @SerialName("show_weather")
    val showWeather: Boolean = true,
    @SerialName("show_spotify")
    val showSpotify: Boolean = true
)
