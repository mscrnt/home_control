package com.homecontrol.sensors.ui.screens.tesla

import com.homecontrol.sensors.data.model.HAEntity

data class TeslaUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    // Vehicle status
    val isAsleep: Boolean = true,
    val isOnline: Boolean = false,
    // All entities grouped by domain
    val allEntities: List<HAEntity> = emptyList()
) {
    // Group entities by domain for easy display
    val sensors: List<HAEntity> get() = allEntities.filter { it.entityId.startsWith("sensor.") }
    val binarySensors: List<HAEntity> get() = allEntities.filter { it.entityId.startsWith("binary_sensor.") }
    val switches: List<HAEntity> get() = allEntities.filter { it.entityId.startsWith("switch.") }
    val buttons: List<HAEntity> get() = allEntities.filter { it.entityId.startsWith("button.") }
    val covers: List<HAEntity> get() = allEntities.filter { it.entityId.startsWith("cover.") }
    val numbers: List<HAEntity> get() = allEntities.filter { it.entityId.startsWith("number.") }
    val selects: List<HAEntity> get() = allEntities.filter { it.entityId.startsWith("select.") }
    val climates: List<HAEntity> get() = allEntities.filter { it.entityId.startsWith("climate.") }
    val locks: List<HAEntity> get() = allEntities.filter { it.entityId.startsWith("lock.") }
    val deviceTrackers: List<HAEntity> get() = allEntities.filter { it.entityId.startsWith("device_tracker.") }
    val updates: List<HAEntity> get() = allEntities.filter { it.entityId.startsWith("update.") }

    // Key sensors for quick status display
    val batteryEntity: HAEntity? get() = sensors.find { it.entityId.contains("battery") && !it.entityId.contains("charging") }
    val rangeEntity: HAEntity? get() = sensors.find { it.entityId.contains("range") }
    val insideTempEntity: HAEntity? get() = sensors.find { it.entityId.contains("temperature_inside") }
    val outsideTempEntity: HAEntity? get() = sensors.find { it.entityId.contains("temperature_outside") }
    val odometerEntity: HAEntity? get() = sensors.find { it.entityId.contains("odometer") }
    val chargingEntity: HAEntity? get() = binarySensors.find { it.entityId.contains("charging") }
    val chargerEntity: HAEntity? get() = binarySensors.find { it.entityId.contains("charger") && !it.entityId.contains("power") }
    val doorsEntity: HAEntity? get() = binarySensors.find { it.entityId.contains("doors") } ?: locks.firstOrNull()
}
