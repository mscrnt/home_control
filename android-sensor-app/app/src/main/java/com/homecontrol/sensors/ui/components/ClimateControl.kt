package com.homecontrol.sensors.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homecontrol.sensors.data.model.Entity
import com.homecontrol.sensors.ui.theme.HomeControlColors

@Composable
fun ClimateControl(
    entity: Entity,
    onTemperatureChange: (Double) -> Unit,
    onModeChange: (String) -> Unit,
    onFanModeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val attrs = entity.attributes ?: return
    val currentTemp = attrs.currentTemperature
    val targetTemp = attrs.temperature
    val hvacAction = attrs.hvacAction
    val hvacModes = attrs.hvacModes ?: emptyList()
    val fanMode = attrs.fanMode
    val fanModes = attrs.fanModes ?: emptyList()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Current temperature display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (hvacAction) {
                    "heating" -> Icons.Filled.DeviceThermostat
                    "cooling" -> Icons.Filled.AcUnit
                    else -> Icons.Filled.DeviceThermostat
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = when (hvacAction) {
                    "heating" -> HomeControlColors.climateHeating()
                    "cooling" -> HomeControlColors.climateCooling()
                    else -> HomeControlColors.climateIdle()
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentTemp?.let { "${it.toInt()}°" } ?: "--°",
                    style = MaterialTheme.typography.displayMedium
                )
                Text(
                    text = hvacAction?.replaceFirstChar { it.uppercase() } ?: entity.state.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (hvacAction) {
                        "heating" -> HomeControlColors.climateHeating()
                        "cooling" -> HomeControlColors.climateCooling()
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Target temperature control
        if (targetTemp != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = { onTemperatureChange(targetTemp - 1) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                }
                Spacer(modifier = Modifier.width(24.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Target",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${targetTemp.toInt()}°",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                Spacer(modifier = Modifier.width(24.dp))
                FilledIconButton(
                    onClick = { onTemperatureChange(targetTemp + 1) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // HVAC Mode selection
        if (hvacModes.isNotEmpty()) {
            Text(
                text = "Mode",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                hvacModes.forEach { mode ->
                    FilterChip(
                        selected = entity.state == mode,
                        onClick = { onModeChange(mode) },
                        label = { Text(mode.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Fan mode selection
        if (fanModes.isNotEmpty()) {
            Text(
                text = "Fan",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fanModes.forEach { mode ->
                    FilterChip(
                        selected = fanMode == mode,
                        onClick = { onFanModeChange(mode) },
                        label = { Text(mode.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }
    }
}
