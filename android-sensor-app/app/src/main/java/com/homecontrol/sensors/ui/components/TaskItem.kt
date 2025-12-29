package com.homecontrol.sensors.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homecontrol.sensors.data.model.Task
import com.homecontrol.sensors.ui.theme.HomeControlColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TaskItem(
    task: Task,
    onToggle: () -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isCompleted = task.status == "completed"

    val textColor by animateColorAsState(
        targetValue = if (isCompleted) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "textColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isCompleted) {
            HomeControlColors.stateOn()
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "iconColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = HomeControlColors.cardBackgroundSolid()
        ),
        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (isCompleted) "Mark incomplete" else "Mark complete",
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Due date
                task.due?.let { dueDate ->
                    Spacer(modifier = Modifier.width(4.dp))
                    val formattedDate = formatDueDate(dueDate)
                    val isOverdue = isOverdue(dueDate) && !isCompleted

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (isOverdue) HomeControlColors.stateUnavailable() else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOverdue) HomeControlColors.stateUnavailable() else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Notes preview
                task.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TaskItemCompact(
    task: Task,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompleted = task.status == "completed"

    val textColor by animateColorAsState(
        targetValue = if (isCompleted) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "textColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isCompleted) {
            HomeControlColors.stateOn()
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "iconColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = if (isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (isCompleted) "Mark incomplete" else "Mark complete",
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = task.title,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val dueDateFormatter = DateTimeFormatter.ofPattern("MMM d")
private val dueDateWithYearFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun formatDueDate(dueDate: String): String {
    return try {
        val date = LocalDate.parse(dueDate.substringBefore("T"))
        val today = LocalDate.now()

        when {
            date == today -> "Today"
            date == today.plusDays(1) -> "Tomorrow"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> date.format(dueDateFormatter)
            else -> date.format(dueDateWithYearFormatter)
        }
    } catch (e: Exception) {
        dueDate
    }
}

private fun isOverdue(dueDate: String): Boolean {
    return try {
        val date = LocalDate.parse(dueDate.substringBefore("T"))
        date.isBefore(LocalDate.now())
    } catch (e: Exception) {
        false
    }
}
