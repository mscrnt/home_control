package com.homecontrol.sensors.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EntityGroup(
    val name: String,
    val icon: String? = null,
    @SerialName("cards")
    val entities: List<Entity> = emptyList()
)

@Serializable
data class Entity(
    @SerialName("entityId")
    val id: String,
    val name: String,
    val state: String,
    val icon: String? = null,
    val domain: String? = null,
    val attributes: EntityAttributes? = null
)

@Serializable
data class EntityAttributes(
    @SerialName("current_temperature")
    val currentTemperature: Double? = null,
    val temperature: Double? = null,
    @SerialName("target_temp_high")
    val targetTempHigh: Double? = null,
    @SerialName("target_temp_low")
    val targetTempLow: Double? = null,
    @SerialName("hvac_action")
    val hvacAction: String? = null,
    @SerialName("hvac_modes")
    val hvacModes: List<String>? = null,
    @SerialName("fan_mode")
    val fanMode: String? = null,
    @SerialName("fan_modes")
    val fanModes: List<String>? = null,
    val humidity: Double? = null,
    val brightness: Int? = null,
    @SerialName("color_temp")
    val colorTemp: Int? = null,
    @SerialName("rgb_color")
    val rgbColor: List<Int>? = null,
    @SerialName("friendly_name")
    val friendlyName: String? = null,
    @SerialName("device_class")
    val deviceClass: String? = null,
    @SerialName("unit_of_measurement")
    val unitOfMeasurement: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null
)

// Request models
@Serializable
data class TemperatureRequest(
    val temperature: Double? = null,
    @SerialName("target_temp_high")
    val targetTempHigh: Double? = null,
    @SerialName("target_temp_low")
    val targetTempLow: Double? = null
)

@Serializable
data class ModeRequest(
    val mode: String
)

@Serializable
data class FanModeRequest(
    @SerialName("fan_mode")
    val fanMode: String
)

// HA Registry models
@Serializable
data class HADomainInfo(
    val name: String,
    val icon: String,
    val priority: Int
)

@Serializable
data class HADomainSummary(
    val domain: String,
    val info: HADomainInfo,
    val count: Int
)

@Serializable
data class HADomainEntities(
    val domain: String,
    val info: HADomainInfo,
    val entities: List<HAEntity>,
    val count: Int
)

@Serializable
data class HAEntity(
    @SerialName("entity_id")
    val entityId: String,
    val state: String,
    val attributes: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    @SerialName("last_changed")
    val lastChanged: String? = null
) {
    val friendlyName: String
        get() = attributes["friendly_name"]?.let {
            if (it is kotlinx.serialization.json.JsonPrimitive) it.content else entityId
        } ?: entityId

    val domain: String
        get() = entityId.substringBefore(".")
}

@Serializable
data class FilteredAutomation(
    @SerialName("entity_id")
    val entityId: String,          // The trigger entity (button/switch)
    @SerialName("friendly_name")
    val friendlyName: String,      // Friendly name of the trigger
    val state: String,             // Current state of the trigger
    @SerialName("trigger_type")
    val triggerType: String,       // "button" or "switch"
    @SerialName("automation_id")
    val automationId: String,      // The automation this triggers
    @SerialName("automation_name")
    val automationName: String     // Friendly name of the automation
)

@Serializable
data class FanEntity(
    @SerialName("entity_id")
    val entityId: String,
    @SerialName("friendly_name")
    val friendlyName: String,
    val state: String              // "on" or "off"
)

@Serializable
data class ClimateEntity(
    @SerialName("entity_id")
    val entityId: String,
    @SerialName("friendly_name")
    val friendlyName: String,
    val state: String,             // Current HVAC state
    @SerialName("fan_mode")
    val fanMode: String,
    @SerialName("fan_modes")
    val fanModes: List<String>
)
