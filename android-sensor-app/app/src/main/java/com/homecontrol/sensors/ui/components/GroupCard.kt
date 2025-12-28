package com.homecontrol.sensors.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.homecontrol.sensors.data.model.Entity
import com.homecontrol.sensors.data.model.EntityGroup
import com.homecontrol.sensors.ui.theme.HomeControlColors

@Composable
fun GroupCard(
    group: EntityGroup,
    onEntityToggle: (Entity) -> Unit,
    onEntityClick: (Entity) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackground()
        ),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getGroupIcon(group.name),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f).padding(start = 12.dp))
                Column(
                    modifier = Modifier.weight(8f)
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = getGroupSummary(group),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    group.entities.forEach { entity ->
                        EntityCard(
                            entity = entity,
                            onToggle = { onEntityToggle(entity) },
                            onClick = if (entity.domain == "climate") {
                                { onEntityClick(entity) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

private fun getGroupIcon(groupName: String): ImageVector {
    return when (groupName.lowercase()) {
        "security" -> Icons.Filled.Security
        "climate" -> Icons.Filled.AcUnit
        "lights" -> Icons.Filled.Lightbulb
        "hue" -> Icons.Filled.Lightbulb
        "spotify", "music" -> Icons.Filled.MusicNote
        else -> Icons.Filled.Home
    }
}

private fun getGroupSummary(group: EntityGroup): String {
    val total = group.entities.size
    val onCount = group.entities.count { entity ->
        entity.state.lowercase() in listOf("on", "locked", "armed_home", "armed_away", "armed_night")
    }

    return when {
        group.entities.any { it.domain == "climate" } -> {
            val climate = group.entities.find { it.domain == "climate" }
            climate?.attributes?.currentTemperature?.let { "${it.toInt()}°" } ?: "$total devices"
        }
        onCount == 0 -> "All off"
        onCount == total -> "All on"
        else -> "$onCount of $total on"
    }
}
