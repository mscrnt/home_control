package com.homecontrol.sensors.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HueRoom(
    val id: String,
    val name: String,
    val on: Boolean,
    val brightness: Int,
    val lights: List<HueLight>,
    val scenes: List<HueScene>? = null
)

@Serializable
data class HueLight(
    val id: String,
    val name: String,
    val on: Boolean,
    val brightness: Int,
    val reachable: Boolean = true,
    val type: String? = null,
    @SerialName("color_temp")
    val colorTemp: Int? = null,
    val hue: Int? = null,
    val saturation: Int? = null
)

@Serializable
data class HueScene(
    val id: String,
    val name: String,
    val group: String? = null
)

@Serializable
data class EntertainmentStatus(
    val active: Boolean,
    @SerialName("active_group")
    val activeGroup: String? = null
)

@Serializable
data class BrightnessRequest(
    val brightness: Int
)

// Sync Box models
@Serializable
data class SyncBox(
    val name: String,
    val index: Int
)

@Serializable
data class SyncBoxStatus(
    val on: Boolean,
    val mode: String,
    val brightness: Int,
    val input: String,
    @SerialName("sync_active")
    val syncActive: Boolean,
    @SerialName("hdmi_source")
    val hdmiSource: String? = null,
    val intensity: String? = null,
    val inputs: List<SyncBoxInput>? = null
)

@Serializable
data class SyncBoxInput(
    val id: String,
    val name: String,
    val type: String
)

@Serializable
data class SyncRequest(
    val sync: Boolean
)

@Serializable
data class InputRequest(
    val input: String
)
