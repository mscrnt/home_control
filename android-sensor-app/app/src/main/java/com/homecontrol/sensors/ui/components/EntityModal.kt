package com.homecontrol.sensors.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homecontrol.sensors.data.model.Entity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityModal(
    entity: Entity,
    onDismiss: () -> Unit,
    onTemperatureChange: (Double) -> Unit,
    onModeChange: (String) -> Unit,
    onFanModeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Title
            Text(
                text = entity.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Content based on entity type
            when (entity.domain) {
                "climate" -> {
                    ClimateControl(
                        entity = entity,
                        onTemperatureChange = onTemperatureChange,
                        onModeChange = onModeChange,
                        onFanModeChange = onFanModeChange
                    )
                }
                else -> {
                    Text(
                        text = "State: ${entity.state}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
