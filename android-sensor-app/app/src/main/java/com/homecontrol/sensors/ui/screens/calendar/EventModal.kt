package com.homecontrol.sensors.ui.screens.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homecontrol.sensors.data.model.Calendar
import com.homecontrol.sensors.data.model.CalendarDateTime
import com.homecontrol.sensors.data.model.CalendarEvent
import com.homecontrol.sensors.data.model.CalendarEventRequest
import com.homecontrol.sensors.ui.theme.HomeControlColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventModal(
    isCreateMode: Boolean,
    event: CalendarEvent?,
    calendars: List<Calendar>,
    initialDate: LocalDate,
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onCreate: (CalendarEventRequest) -> Unit,
    onUpdate: (CalendarEvent, CalendarEventRequest) -> Unit,
    onDelete: (CalendarEvent) -> Unit
) {
    // Form state
    var title by remember { mutableStateOf(event?.summary ?: "") }
    var description by remember { mutableStateOf(event?.description ?: "") }
    var location by remember { mutableStateOf(event?.location ?: "") }
    var isAllDay by remember { mutableStateOf(event?.allDay ?: false) }
    var startDate by remember { mutableStateOf(parseEventDate(event) ?: initialDate) }
    var startTime by remember { mutableStateOf(parseEventTime(event) ?: initialTime) }
    var endDate by remember { mutableStateOf(parseEventEndDate(event) ?: initialDate) }
    var endTime by remember { mutableStateOf(parseEventEndTime(event) ?: initialTime.plusHours(1)) }
    var selectedCalendarId by remember { mutableStateOf(event?.calendarId ?: calendars.firstOrNull()?.id ?: "") }

    // Dialog state
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var calendarDropdownExpanded by remember { mutableStateOf(false) }

    // Full-screen overlay approach for better control with DPI scaling
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        // Prevent clicks on the card from dismissing
        Surface(
            modifier = Modifier
                .clickable(enabled = false, onClick = {})
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }

                    Text(
                        text = if (isCreateMode) "New Event" else "Edit Event",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = {
                            val request = createEventRequest(
                                title = title,
                                description = description,
                                location = location,
                                isAllDay = isAllDay,
                                startDate = startDate,
                                startTime = startTime,
                                endDate = endDate,
                                endTime = endTime,
                                calendarId = selectedCalendarId
                            )
                            if (isCreateMode) {
                                onCreate(request)
                            } else {
                                event?.let { onUpdate(it, request) }
                            }
                        },
                        enabled = title.isNotBlank()
                    ) {
                        Text(if (isCreateMode) "Create" else "Save")
                    }
                }

                // Form content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        placeholder = { Text("Event title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Calendar selector
                    if (calendars.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = calendarDropdownExpanded,
                            onExpandedChange = { calendarDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = calendars.find { it.id == selectedCalendarId }?.summary ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Calendar") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = calendarDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = calendarDropdownExpanded,
                                onDismissRequest = { calendarDropdownExpanded = false }
                            ) {
                                calendars.forEach { calendar ->
                                    DropdownMenuItem(
                                        text = { Text(calendar.summary) },
                                        onClick = {
                                            selectedCalendarId = calendar.id
                                            calendarDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // All day toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isAllDay,
                            onCheckedChange = { isAllDay = it }
                        )
                        Text(
                            text = "All day",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Date/Time section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = HomeControlColors.cardBackground()
                        ),
                        border = BorderStroke(1.dp, HomeControlColors.cardBorder())
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Start
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Start", style = MaterialTheme.typography.bodyMedium)
                                }

                                Row {
                                    DateChip(
                                        date = startDate,
                                        onClick = { showStartDatePicker = true }
                                    )
                                    if (!isAllDay) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TimeChip(
                                            time = startTime,
                                            onClick = { showStartTimePicker = true }
                                        )
                                    }
                                }
                            }

                            // End
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(32.dp))
                                    Text("End", style = MaterialTheme.typography.bodyMedium)
                                }

                                Row {
                                    DateChip(
                                        date = endDate,
                                        onClick = { showEndDatePicker = true }
                                    )
                                    if (!isAllDay) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TimeChip(
                                            time = endTime,
                                            onClick = { showEndTimePicker = true }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Location
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Location") },
                        placeholder = { Text("Add location") },
                        leadingIcon = {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        placeholder = { Text("Add description") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )

                    // Delete button (only for edit mode)
                    if (!isCreateMode && event != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Event")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Date pickers
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        startDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        if (endDate.isBefore(startDate)) {
                            endDate = startDate
                        }
                    }
                    showStartDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val newEndDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        if (!newEndDate.isBefore(startDate)) {
                            endDate = newEndDate
                        }
                    }
                    showEndDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time pickers
    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = startTime,
            onConfirm = { time ->
                startTime = time
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialTime = endTime,
            onConfirm = { time ->
                endTime = time
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm && event != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Event") },
            text = { Text("Are you sure you want to delete \"${event.summary}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(event)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DateChip(
    date: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = HomeControlColors.cardBackground(),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TimeChip(
    time: LocalTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = HomeControlColors.cardBackground(),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = time.format(DateTimeFormatter.ofPattern("h:mm a")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}

private fun parseEventDate(event: CalendarEvent?): LocalDate? {
    if (event == null) return null
    return try {
        // start is now a plain ISO date string like "2025-12-29T00:00:00-08:00"
        val dtStr = event.start.substringBefore("+").substringBefore("-0").substringBefore("T")
        if (dtStr.contains("T")) {
            LocalDateTime.parse(event.start.substringBefore("+").substringBefore("-0")).toLocalDate()
        } else {
            LocalDate.parse(dtStr)
        }
    } catch (e: Exception) {
        try {
            // Try parsing as full datetime
            val dtStr = event.start.substringBefore("+")
            // Handle timezone offset like -08:00
            val cleanDtStr = dtStr.replace(Regex("-\\d{2}:\\d{2}$"), "")
            LocalDateTime.parse(cleanDtStr).toLocalDate()
        } catch (e2: Exception) {
            null
        }
    }
}

private fun parseEventTime(event: CalendarEvent?): LocalTime? {
    if (event == null || event.allDay) return null
    return try {
        val dtStr = event.start.substringBefore("+")
        val cleanDtStr = dtStr.replace(Regex("-\\d{2}:\\d{2}$"), "")
        LocalDateTime.parse(cleanDtStr).toLocalTime()
    } catch (e: Exception) {
        null
    }
}

private fun parseEventEndDate(event: CalendarEvent?): LocalDate? {
    if (event == null) return null
    return try {
        val dtStr = event.end.substringBefore("+")
        val cleanDtStr = dtStr.replace(Regex("-\\d{2}:\\d{2}$"), "")
        LocalDateTime.parse(cleanDtStr).toLocalDate()
    } catch (e: Exception) {
        null
    }
}

private fun parseEventEndTime(event: CalendarEvent?): LocalTime? {
    if (event == null || event.allDay) return null
    return try {
        val dtStr = event.end.substringBefore("+")
        val cleanDtStr = dtStr.replace(Regex("-\\d{2}:\\d{2}$"), "")
        LocalDateTime.parse(cleanDtStr).toLocalTime()
    } catch (e: Exception) {
        null
    }
}

private fun createEventRequest(
    title: String,
    description: String,
    location: String,
    isAllDay: Boolean,
    startDate: LocalDate,
    startTime: LocalTime,
    endDate: LocalDate,
    endTime: LocalTime,
    calendarId: String
): CalendarEventRequest {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    return if (isAllDay) {
        CalendarEventRequest(
            calendarId = calendarId,
            title = title,
            description = description.takeIf { it.isNotBlank() },
            location = location.takeIf { it.isNotBlank() },
            date = startDate.format(dateFormatter),
            time = null,  // null time indicates all-day event
            endDate = endDate.format(dateFormatter),
            endTime = null
        )
    } else {
        CalendarEventRequest(
            calendarId = calendarId,
            title = title,
            description = description.takeIf { it.isNotBlank() },
            location = location.takeIf { it.isNotBlank() },
            date = startDate.format(dateFormatter),
            time = startTime.format(timeFormatter),
            endDate = endDate.format(dateFormatter),
            endTime = endTime.format(timeFormatter)
        )
    }
}
