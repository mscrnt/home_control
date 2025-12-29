package com.homecontrol.sensors.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CalendarEvent(
    val id: String,
    val calendarId: String,
    val title: String,  // API returns "title"
    val description: String? = null,
    val location: String? = null,
    val start: String,  // API returns ISO date string directly
    val end: String,
    val allDay: Boolean = false,
    val colorId: String? = null,
    val color: String? = null,  // API returns "color" for background color
    val foregroundColor: String? = null,
    val status: String? = null,
    val recurrence: List<String>? = null,
    val recurringEventId: String? = null,
    val attendees: List<Attendee>? = null,
    val htmlLink: String? = null,
    val visibility: String? = null,
    val conferenceData: ConferenceData? = null
) {
    // Helper properties for compatibility
    val summary: String get() = title
    val backgroundColor: String? get() = color
}

@Serializable
data class CalendarDateTime(
    val date: String? = null,  // For all-day events: "2024-01-15"
    @SerialName("date_time")
    val dateTime: String? = null,  // For timed events: "2024-01-15T10:00:00-05:00"
    @SerialName("time_zone")
    val timeZone: String? = null
)

@Serializable
data class Attendee(
    val email: String,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("response_status")
    val responseStatus: String? = null,
    val self: Boolean = false,
    val organizer: Boolean = false
)

@Serializable
data class ConferenceData(
    @SerialName("conference_solution")
    val conferenceSolution: ConferenceSolution? = null,
    @SerialName("entry_points")
    val entryPoints: List<EntryPoint>? = null
)

@Serializable
data class ConferenceSolution(
    val name: String,
    @SerialName("icon_uri")
    val iconUri: String? = null
)

@Serializable
data class EntryPoint(
    @SerialName("entry_point_type")
    val entryPointType: String,
    val uri: String,
    val label: String? = null
)

@Serializable
data class Calendar(
    val id: String,
    val name: String,  // API returns "name"
    val color: String? = null,  // API returns "color"
    val visible: Boolean = true  // From /api/calendar/prefs endpoint
) {
    // Helper properties for compatibility
    val summary: String get() = name
    val backgroundColor: String? get() = color
    val selected: Boolean get() = visible
}

@Serializable
data class CalendarColors(
    val calendar: Map<String, ColorPair> = emptyMap(),
    val event: Map<String, ColorPair> = emptyMap()
)

@Serializable
data class ColorPair(
    val background: String,
    val foreground: String
)

// Request models
@Serializable
data class CalendarEventRequest(
    val calendarId: String,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val start: String,  // ISO date string
    val end: String,
    val allDay: Boolean = false,
    val colorId: String? = null,
    val recurrence: List<String>? = null,
    val visibility: String? = null
)

// Tasks models
@Serializable
data class Task(
    val id: String,
    @SerialName("list_id")
    val listId: String,
    val title: String,
    val notes: String? = null,
    val status: String,  // "needsAction" or "completed"
    val due: String? = null,
    val completed: String? = null,
    val position: String? = null,
    val parent: String? = null
)

@Serializable
data class TaskList(
    val id: String,
    val title: String
)

@Serializable
data class TaskRequest(
    @SerialName("list_id")
    val listId: String,
    val title: String,
    val notes: String? = null,
    val due: String? = null
)
