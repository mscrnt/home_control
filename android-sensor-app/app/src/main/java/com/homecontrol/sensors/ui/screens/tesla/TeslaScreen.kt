package com.homecontrol.sensors.ui.screens.tesla

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homecontrol.sensors.data.model.HAEntity
import com.homecontrol.sensors.ui.components.ErrorState
import com.homecontrol.sensors.ui.components.LoadingIndicator
import kotlinx.serialization.json.JsonPrimitive

private val TeslaRed = Color(0xFFE82127)
private val StatusGreen = Color(0xFF4CAF50)

@Composable
fun TeslaScreen(
    viewModel: TeslaViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading && uiState.allEntities.isEmpty() -> {
                LoadingIndicator()
            }
            uiState.error != null && uiState.allEntities.isEmpty() -> {
                ErrorState(
                    message = uiState.error ?: "Unknown error",
                    onRetry = { viewModel.loadTeslaEntities() }
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Vehicle Status Header
                    VehicleStatusHeader(
                        uiState = uiState,
                        onRefresh = { viewModel.refresh() }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Quick Status Card
                    QuickStatusCard(uiState = uiState)

                    // Controls Section
                    if (uiState.switches.isNotEmpty() || uiState.covers.isNotEmpty() || uiState.buttons.isNotEmpty() || uiState.locks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(title = "Controls")
                        Spacer(modifier = Modifier.height(12.dp))
                        ControlsSection(
                            uiState = uiState,
                            onToggle = { viewModel.toggleEntity(it) },
                            onPress = { viewModel.pressButton(it) }
                        )
                    }

                    // Climate Section
                    if (uiState.climates.isNotEmpty() || uiState.selects.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(title = "Climate & Comfort")
                        Spacer(modifier = Modifier.height(12.dp))
                        ClimateSection(uiState = uiState)
                    }

                    // Sensors Section
                    if (uiState.sensors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(title = "Sensors")
                        Spacer(modifier = Modifier.height(12.dp))
                        SensorsSection(uiState = uiState)
                    }

                    // Status Section (Binary Sensors)
                    if (uiState.binarySensors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(title = "Status")
                        Spacer(modifier = Modifier.height(12.dp))
                        BinarySensorsSection(uiState = uiState)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun VehicleStatusHeader(
    uiState: TeslaUiState,
    onRefresh: () -> Unit
) {
    val statusColor = when {
        uiState.isOnline && !uiState.isAsleep -> StatusGreen
        uiState.isOnline && uiState.isAsleep -> Color(0xFFFFA726)
        else -> Color(0xFF9E9E9E)
    }
    val statusText = when {
        uiState.isOnline && !uiState.isAsleep -> "Vehicle Awake"
        uiState.isOnline && uiState.isAsleep -> "Vehicle Asleep"
        else -> "Vehicle Offline"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(statusColor, RoundedCornerShape(6.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(
            onClick = onRefresh,
            enabled = !uiState.isRefreshing,
            modifier = Modifier.size(32.dp)
        ) {
            if (uiState.isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickStatusCard(uiState: TeslaUiState) {
    val battery = uiState.batteryEntity
    val range = uiState.rangeEntity
    val insideTemp = uiState.insideTempEntity
    val outsideTemp = uiState.outsideTempEntity
    val charging = uiState.chargingEntity
    val charger = uiState.chargerEntity
    val doors = uiState.doorsEntity

    val batteryValue = battery?.state?.toIntOrNull()
    val rangeValue = range?.state?.toDoubleOrNull()?.toInt()
    val insideTempValue = insideTemp?.state?.toDoubleOrNull()?.toInt()
    val outsideTempValue = outsideTemp?.state?.toDoubleOrNull()?.toInt()

    Card(
        modifier = Modifier.widthIn(max = 500.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickStatusItem(
                    icon = Icons.Default.BatteryChargingFull,
                    label = "Battery",
                    value = batteryValue?.let { "$it%" } ?: "--",
                    iconTint = when {
                        (batteryValue ?: 0) > 50 -> StatusGreen
                        (batteryValue ?: 0) > 20 -> Color(0xFFFFA726)
                        else -> TeslaRed
                    }
                )
                QuickStatusItem(
                    icon = Icons.Default.DirectionsCar,
                    label = "Range",
                    value = rangeValue?.let { "$it mi" } ?: "--",
                    iconTint = TeslaRed
                )
                QuickStatusItem(
                    icon = if (charging?.state == "on") Icons.Default.BatteryChargingFull else Icons.Default.PowerSettingsNew,
                    label = "Charging",
                    value = when {
                        charging?.state == "on" -> "Active"
                        charger?.state == "on" -> "Plugged"
                        else -> "Unplugged"
                    },
                    iconTint = if (charging?.state == "on") StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickStatusItem(
                    icon = Icons.Default.Thermostat,
                    label = "Inside",
                    value = insideTempValue?.let { "$it°F" } ?: "--",
                    iconTint = TeslaRed
                )
                QuickStatusItem(
                    icon = Icons.Default.Thermostat,
                    label = "Outside",
                    value = outsideTempValue?.let { "$it°F" } ?: "--",
                    iconTint = MaterialTheme.colorScheme.primary
                )
                QuickStatusItem(
                    icon = if (doors?.state == "off" || doors?.state == "locked") Icons.Default.Lock else Icons.Default.LockOpen,
                    label = "Doors",
                    value = if (doors?.state == "off" || doors?.state == "locked") "Locked" else "Unlocked",
                    iconTint = if (doors?.state == "off" || doors?.state == "locked") StatusGreen else TeslaRed
                )
            }
        }
    }
}

@Composable
private fun QuickStatusItem(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsSection(
    uiState: TeslaUiState,
    onToggle: (String) -> Unit,
    onPress: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Switches
        uiState.switches.forEach { entity ->
            ControlChip(
                entity = entity,
                isOn = entity.state == "on",
                onToggle = { onToggle(entity.entityId) }
            )
        }

        // Covers
        uiState.covers.forEach { entity ->
            CoverChip(
                entity = entity,
                onToggle = { onToggle(entity.entityId) }
            )
        }

        // Locks
        uiState.locks.forEach { entity ->
            LockChip(
                entity = entity,
                onToggle = { onToggle(entity.entityId) }
            )
        }

        // Buttons
        uiState.buttons.forEach { entity ->
            ButtonChip(
                entity = entity,
                onPress = { onPress(entity.entityId) }
            )
        }
    }
}

@Composable
private fun ControlChip(
    entity: HAEntity,
    isOn: Boolean,
    onToggle: () -> Unit
) {
    val name = entity.friendlyName
        ?.removePrefix("Tessa ")?.removePrefix("tessa ")
        ?: entity.entityId.substringAfter(".")

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isOn) TeslaRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (isOn) TeslaRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isOn,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TeslaRed,
                    checkedTrackColor = TeslaRed.copy(alpha = 0.5f)
                ),
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

@Composable
private fun CoverChip(
    entity: HAEntity,
    onToggle: () -> Unit
) {
    val isOpen = entity.state == "open"
    val name = entity.friendlyName
        ?.removePrefix("Tessa ")?.removePrefix("tessa ")
        ?: entity.entityId.substringAfter(".")

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isOpen) TeslaRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (isOpen) TeslaRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = if (isOpen) "Open" else "Closed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOpen) TeslaRed else StatusGreen
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isOpen,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TeslaRed,
                    checkedTrackColor = TeslaRed.copy(alpha = 0.5f)
                ),
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

@Composable
private fun LockChip(
    entity: HAEntity,
    onToggle: () -> Unit
) {
    val isLocked = entity.state == "locked"
    val name = entity.friendlyName
        ?.removePrefix("Tessa ")?.removePrefix("tessa ")
        ?: entity.entityId.substringAfter(".")

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isLocked) StatusGreen else TeslaRed,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isLocked) "Locked" else "Unlocked",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLocked) StatusGreen else TeslaRed
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isLocked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = StatusGreen,
                    checkedTrackColor = StatusGreen.copy(alpha = 0.5f)
                ),
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

@Composable
private fun ButtonChip(
    entity: HAEntity,
    onPress: () -> Unit
) {
    val name = entity.friendlyName
        ?.removePrefix("Tessa ")?.removePrefix("tessa ")
        ?: entity.entityId.substringAfter(".")

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable(onClick = onPress)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Run",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClimateSection(uiState: TeslaUiState) {
    Card(
        modifier = Modifier.widthIn(max = 600.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Climate entities
            uiState.climates.forEach { entity ->
                val isOn = entity.state != "off"
                val temp = entity.attributes["temperature"]?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() }
                val currentTemp = entity.attributes["current_temperature"]?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = entity.friendlyName?.removePrefix("Tessa ")?.removePrefix("tessa ") ?: "Climate",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = buildString {
                                append(entity.state.replaceFirstChar { it.uppercase() })
                                temp?.let { append(" • Set: $it°F") }
                                currentTemp?.let { append(" • Now: $it°F") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (isOn) "ON" else "OFF",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isOn) TeslaRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Heated Seats
            val heatedSeats = uiState.selects.filter { it.entityId.contains("heated_seat") }
            if (heatedSeats.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = "Heated Seats",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    heatedSeats.forEach { entity ->
                        val name = entity.friendlyName
                            ?.removePrefix("Tessa ")?.removePrefix("tessa ")
                            ?.removePrefix("Heated seat ")?.removePrefix("heated seat ")
                            ?: entity.entityId.substringAfterLast("_")
                        HeatedSeatIndicator(label = name, level = entity.state)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatedSeatIndicator(label: String, level: String) {
    val levelDisplay = when (level.lowercase()) {
        "high" -> "●●●"
        "medium" -> "●●○"
        "low" -> "●○○"
        else -> "○○○"
    }
    val isActive = level.lowercase() != "off"

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = levelDisplay,
            style = MaterialTheme.typography.titleMedium,
            color = if (isActive) TeslaRed else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SensorsSection(uiState: TeslaUiState) {
    Card(
        modifier = Modifier.widthIn(max = 800.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        FlowRow(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 5
        ) {
            uiState.sensors.forEach { entity ->
                SensorItem(entity = entity)
            }
        }
    }
}

@Composable
private fun SensorItem(entity: HAEntity) {
    val name = entity.friendlyName
        ?.removePrefix("Tessa ")?.removePrefix("tessa ")
        ?: entity.entityId.substringAfter("sensor.tessa_")

    val unit = entity.attributes["unit_of_measurement"]?.let { (it as? JsonPrimitive)?.content } ?: ""
    val rawValue = entity.state
    val displayValue = when {
        rawValue == "unknown" || rawValue == "unavailable" -> "--"
        unit.isNotEmpty() -> "$rawValue $unit"
        else -> rawValue
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = displayValue,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BinarySensorsSection(uiState: TeslaUiState) {
    Card(
        modifier = Modifier.widthIn(max = 800.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        FlowRow(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 5
        ) {
            uiState.binarySensors.forEach { entity ->
                BinarySensorItem(entity = entity)
            }
        }
    }
}

@Composable
private fun BinarySensorItem(entity: HAEntity) {
    val name = entity.friendlyName
        ?.removePrefix("Tessa ")?.removePrefix("tessa ")
        ?: entity.entityId.substringAfter("binary_sensor.tessa_")

    val isOn = entity.state == "on"
    val displayValue = when {
        entity.entityId.contains("door") || entity.entityId.contains("window") -> if (isOn) "Open" else "Closed"
        entity.entityId.contains("online") -> if (isOn) "Connected" else "Offline"
        entity.entityId.contains("asleep") -> if (isOn) "Asleep" else "Awake"
        entity.entityId.contains("charging") -> if (isOn) "Charging" else "Not charging"
        entity.entityId.contains("charger") -> if (isOn) "Plugged" else "Unplugged"
        entity.entityId.contains("parking_brake") -> if (isOn) "Engaged" else "Released"
        else -> if (isOn) "On" else "Off"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = displayValue,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isOn) TeslaRed else StatusGreen,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
