package com.homecontrol.sensors.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homecontrol.sensors.data.model.Entity
import com.homecontrol.sensors.ui.theme.HomeControlColors

@Composable
fun EntityCard(
    entity: Entity,
    onToggle: () -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isOn = entity.state.lowercase() in listOf("on", "locked", "armed_home", "armed_away", "armed_night")
    val isToggleable = entity.domain in listOf("switch", "light", "lock", "input_boolean", "alarm_control_panel")
    val isClimate = entity.domain == "climate"

    // Entity cards use solid background (not glass) since they're nested inside GroupCards
    val borderColor = if (isOn) MaterialTheme.colorScheme.primary else HomeControlColors.cardBorder()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null || isClimate) {
                    Modifier.clickable { onClick?.invoke() }
                } else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackgroundSolid()
        ),
        border = BorderStroke(if (isOn) 3.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getEntityIcon(entity),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = getEntityIconColor(entity)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entity.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = getEntityStateText(entity),
                    style = MaterialTheme.typography.bodySmall,
                    color = getEntityStateColor(entity)
                )
            }
            if (isToggleable) {
                Switch(
                    checked = isOn,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun getEntityIcon(entity: Entity): ImageVector {
    return when (entity.domain) {
        "climate" -> {
            when (entity.attributes?.hvacAction) {
                "heating" -> Icons.Filled.DeviceThermostat
                "cooling" -> Icons.Filled.AcUnit
                else -> Icons.Filled.DeviceThermostat
            }
        }
        "light" -> Icons.Filled.Lightbulb
        "switch" -> if (entity.state == "on") Icons.Filled.ToggleOn else Icons.Filled.ToggleOff
        "lock" -> if (entity.state == "locked") Icons.Filled.Lock else Icons.Filled.LockOpen
        "binary_sensor" -> {
            when (entity.attributes?.deviceClass) {
                "door" -> Icons.Filled.DoorFront
                "moisture" -> Icons.Filled.WaterDrop
                "motion" -> Icons.Filled.Sensors
                else -> Icons.Filled.Sensors
            }
        }
        "alarm_control_panel" -> Icons.Filled.Shield
        else -> Icons.Filled.PowerSettingsNew
    }
}

@Composable
private fun getEntityIconColor(entity: Entity): androidx.compose.ui.graphics.Color {
    if (entity.state == "unavailable") return HomeControlColors.stateUnavailable()

    return when (entity.domain) {
        "climate" -> {
            when (entity.attributes?.hvacAction) {
                "heating" -> HomeControlColors.climateHeating()
                "cooling" -> HomeControlColors.climateCooling()
                else -> HomeControlColors.climateIdle()
            }
        }
        else -> {
            val isOn = entity.state.lowercase() in listOf("on", "locked", "armed_home", "armed_away", "armed_night")
            if (isOn) HomeControlColors.stateOn() else HomeControlColors.stateOff()
        }
    }
}

@Composable
private fun getEntityStateText(entity: Entity): String {
    if (entity.state == "unavailable") return "Unavailable"

    return when (entity.domain) {
        "climate" -> {
            val temp = entity.attributes?.currentTemperature?.let { "${it.toInt()}°" } ?: ""
            val action = entity.attributes?.hvacAction?.replaceFirstChar { it.uppercase() } ?: entity.state.replaceFirstChar { it.uppercase() }
            if (temp.isNotEmpty()) "$action - $temp" else action
        }
        "lock" -> if (entity.state == "locked") "Locked" else "Unlocked"
        "alarm_control_panel" -> entity.state.replace("_", " ").replaceFirstChar { it.uppercase() }
        "binary_sensor" -> {
            when (entity.attributes?.deviceClass) {
                "door" -> if (entity.state == "on") "Open" else "Closed"
                "motion" -> if (entity.state == "on") "Motion" else "Clear"
                "moisture" -> if (entity.state == "on") "Wet" else "Dry"
                else -> if (entity.state == "on") "On" else "Off"
            }
        }
        else -> entity.state.replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun getEntityStateColor(entity: Entity): androidx.compose.ui.graphics.Color {
    if (entity.state == "unavailable") return HomeControlColors.stateUnavailable()

    return when (entity.domain) {
        "climate" -> {
            when (entity.attributes?.hvacAction) {
                "heating" -> HomeControlColors.climateHeating()
                "cooling" -> HomeControlColors.climateCooling()
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
        "binary_sensor" -> {
            when (entity.attributes?.deviceClass) {
                "door", "motion" -> if (entity.state == "on") HomeControlColors.stateOn() else MaterialTheme.colorScheme.onSurfaceVariant
                "moisture" -> if (entity.state == "on") HomeControlColors.stateUnavailable() else MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
