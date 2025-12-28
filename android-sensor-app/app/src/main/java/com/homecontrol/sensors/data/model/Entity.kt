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
