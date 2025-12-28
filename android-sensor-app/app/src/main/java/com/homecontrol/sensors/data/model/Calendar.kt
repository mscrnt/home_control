package com.homecontrol.sensors.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CalendarEvent(
    val id: String,
    @SerialName("calendar_id")
    val calendarId: String,
    val summary: String,
    val description: String? = null,
    val location: String? = null,
    val start: CalendarDateTime,
    val end: CalendarDateTime,
    @SerialName("all_day")
    val allDay: Boolean = false,
    @SerialName("color_id")
    val colorId: String? = null,
    @SerialName("background_color")
    val backgroundColor: String? = null,
    @SerialName("foreground_color")
    val foregroundColor: String? = null,
    val status: String? = null,
    val recurrence: List<String>? = null,
    @SerialName("recurring_event_id")
    val recurringEventId: String? = null,
    val attendees: List<Attendee>? = null,
    @SerialName("html_link")
    val htmlLink: String? = null,
    val visibility: String? = null,
    @SerialName("conference_data")
    val conferenceData: ConferenceData? = null
)

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
    val summary: String,
    val description: String? = null,
    @SerialName("background_color")
    val backgroundColor: String? = null,
    @SerialName("foreground_color")
    val foregroundColor: String? = null,
    val primary: Boolean = false,
    val selected: Boolean = true,
    @SerialName("access_role")
    val accessRole: String? = null
)

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
    @SerialName("calendar_id")
    val calendarId: String,
    val summary: String,
    val description: String? = null,
    val location: String? = null,
    val start: CalendarDateTime,
    val end: CalendarDateTime,
    @SerialName("all_day")
    val allDay: Boolean = false,
    @SerialName("color_id")
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
