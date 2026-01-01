package com.homecontrol.sensors.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
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

@Serializable
data class SonySoundSetting(
    val target: String,
    val currentValue: String,
    val title: String? = null,
    val type: String? = null,
    val isAvailable: Boolean = true,
    val candidate: List<SonySoundSettingCandidate> = emptyList()
)

@Serializable
data class SonySoundSettingCandidate(
    val value: String,
    val title: String,
    val isAvailable: Boolean = true,
    val min: Int? = null,
    val max: Int? = null,
    val step: Int? = null
)

@Serializable
data class SoundSettingRequest(
    val target: String,
    val value: String
)

// Nvidia Shield
@Serializable
data class ShieldDevice(
    val name: String,
    val ip: String? = null
)

@Serializable
data class ShieldState(
    val name: String? = null,
    val host: String? = null,
    val online: Boolean = false,
    @SerialName("power_state")
    val powerState: String? = null, // awake, dreaming, asleep, unknown
    @SerialName("current_app")
    val currentApp: String? = null,
    val volume: Int = 0,
    val muted: Boolean = false,
    val brightness: Int = 0,
    val error: String? = null
) {
    // Helper property for backwards compatibility
    val power: Boolean
        get() = online && (powerState == "awake" || powerState == "dreaming")
}

@Serializable
data class ShieldApp(
    @SerialName("package")
    val packageName: String,
    val name: String? = null,
    @SerialName("is_system")
    val isSystem: Boolean = false
)

@Serializable
data class ShieldKeyRequest(
    val key: String
)

@Serializable
data class ShieldCommandRequest(
    val command: String,
    val args: List<String> = emptyList()
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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AppRequest(
    @EncodeDefault
    val action: String = "launch",
    @SerialName("package")
    val packageName: String
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
