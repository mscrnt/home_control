package com.homecontrol.sensors.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HueRoom(
    val id: String,
    val name: String,
    @SerialName("isOn")
    val on: Boolean = false,
    val type: String? = null,
    @SerialName("class")
    val roomClass: String? = null,
    val lights: List<HueLight> = emptyList(),
    val scenes: List<HueScene> = emptyList(),
    val streamingActive: Boolean = false  // For Entertainment areas
) {
    // Calculate average brightness from lights
    val brightness: Int
        get() = lights.filter { it.state?.on == true }
            .mapNotNull { it.state?.bri }
            .takeIf { it.isNotEmpty() }
            ?.average()?.toInt() ?: 0
}

@Serializable
data class HueLight(
    val id: String,
    val name: String,
    val type: String? = null,
    val modelid: String? = null,
    val productname: String? = null,
    val state: HueLightState? = null,
    val capabilities: HueLightCapabilities? = null
) {
    // Convenience accessors
    val on: Boolean get() = state?.on ?: false
    val brightness: Int get() = state?.bri ?: 0
    val reachable: Boolean get() = state?.reachable ?: false
}

@Serializable
data class HueLightState(
    val on: Boolean = false,
    val bri: Int = 0,
    val hue: Int? = null,
    val sat: Int? = null,
    val ct: Int? = null,
    val xy: List<Float>? = null,
    val colormode: String? = null,
    val effect: String? = null,
    val alert: String? = null,
    val reachable: Boolean = true
)

@Serializable
data class HueLightCapabilities(
    val control: HueLightControl? = null
)

@Serializable
data class HueLightControl(
    val colorgamuttype: String? = null,
    val ct: HueLightCtRange? = null
)

@Serializable
data class HueLightCtRange(
    val min: Int = 153,
    val max: Int = 500
)

@Serializable
data class HueScene(
    val id: String,
    val name: String,
    val group: String? = null,
    val active: Boolean = false // V2: whether this scene is currently active
)

@Serializable
data class EntertainmentStatus(
    val active: Boolean,
    @SerialName("active_group")
    val activeGroup: String? = null
)

// SSE Event from Hue bridge
@Serializable
data class HueEvent(
    val type: String,
    val id: String,
    @SerialName("id_v1")
    val idV1: String? = null,
    val data: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    val timestamp: String? = null
)

@Serializable
data class BrightnessRequest(
    val brightness: Int
)

// Sync Box models
@Serializable
data class SyncBox(
    val name: String,
    val index: Int,
    val ip: String? = null
)

@Serializable
data class SyncBoxStatus(
    val device: SyncBoxDevice? = null,
    val execution: SyncBoxExecution? = null,
    val hue: SyncBoxHue? = null,
    val hdmi: SyncBoxHdmi? = null
) {
    // Convenience accessors
    val on: Boolean get() = execution?.syncActive ?: false
    val mode: String get() = execution?.mode ?: "unknown"
    val brightness: Int get() = execution?.brightness ?: 0
    val input: String get() = execution?.hdmiSource ?: ""
    val syncActive: Boolean get() = execution?.syncActive ?: false

    // Get inputs as list
    val inputs: List<SyncBoxInput>
        get() = listOfNotNull(
            hdmi?.input1?.let { SyncBoxInput("input1", it.name, it.type) },
            hdmi?.input2?.let { SyncBoxInput("input2", it.name, it.type) },
            hdmi?.input3?.let { SyncBoxInput("input3", it.name, it.type) },
            hdmi?.input4?.let { SyncBoxInput("input4", it.name, it.type) }
        )
}

@Serializable
data class SyncBoxHdmi(
    val input1: SyncBoxHdmiInput? = null,
    val input2: SyncBoxHdmiInput? = null,
    val input3: SyncBoxHdmiInput? = null,
    val input4: SyncBoxHdmiInput? = null,
    val contentSpecs: String? = null
)

@Serializable
data class SyncBoxDevice(
    val name: String? = null,
    val deviceType: String? = null,
    val uniqueId: String? = null,
    val ipAddress: String? = null,
    val firmwareVersion: String? = null,
    val buildNumber: Long? = null,
    val ledMode: Int? = null
)

@Serializable
data class SyncBoxExecution(
    val syncActive: Boolean = false,
    val hdmiSource: String? = null,
    val hdmiActive: Boolean? = null,
    val mode: String = "powersave",
    val lastSyncMode: String? = null,
    val brightness: Int = 0,
    val hueTarget: String? = null
)

@Serializable
data class SyncBoxHue(
    val connectionState: String? = null,
    val bridgeUniqueId: String? = null,
    val bridgeIpAddress: String? = null,
    val groupId: String? = null,
    val groups: Map<String, SyncBoxHueGroup>? = null
)

@Serializable
data class SyncBoxHueGroup(
    val name: String,
    val numLights: Int = 0,
    val active: Boolean = false
)

@Serializable
data class SyncBoxHdmiInput(
    val name: String,
    val type: String,
    val status: String? = null
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
