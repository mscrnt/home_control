package com.homecontrol.sensors.ui.screens.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor

data class Holiday(
    val date: LocalDate,
    val name: String,
    val icon: String? = null,
    val isMoonPhase: Boolean = false
)

object Holidays {
    private const val LUNAR_CYCLE = 29.53058770576

    /**
     * Get all US holidays and moon phases for a given year
     */
    fun getUSHolidays(year: Int): List<Holiday> {
        val holidays = mutableListOf<Holiday>()

        // New Year's Day - January 1
        holidays.add(Holiday(LocalDate.of(year, 1, 1), "New Year's Day"))

        // Martin Luther King Jr. Day - 3rd Monday of January
        holidays.add(Holiday(getNthWeekdayOfMonth(year, 1, DayOfWeek.MONDAY, 3), "MLK Day"))

        // Valentine's Day - February 14
        holidays.add(Holiday(LocalDate.of(year, 2, 14), "Valentine's Day"))

        // Presidents' Day - 3rd Monday of February
        holidays.add(Holiday(getNthWeekdayOfMonth(year, 2, DayOfWeek.MONDAY, 3), "Presidents' Day"))

        // Daylight Saving Time Starts - 2nd Sunday of March
        holidays.add(Holiday(getNthWeekdayOfMonth(year, 3, DayOfWeek.SUNDAY, 2), "DST Starts"))

        // St. Patrick's Day - March 17
        holidays.add(Holiday(LocalDate.of(year, 3, 17), "St. Patrick's Day"))

        // Easter Sunday
        holidays.add(Holiday(getEasterSunday(year), "Easter"))

        // Mother's Day - 2nd Sunday of May
        holidays.add(Holiday(getNthWeekdayOfMonth(year, 5, DayOfWeek.SUNDAY, 2), "Mother's Day"))

        // Memorial Day - Last Monday of May
        holidays.add(Holiday(getLastWeekdayOfMonth(year, 5, DayOfWeek.MONDAY), "Memorial Day"))

        // Father's Day - 3rd Sunday of June
        holidays.add(Holiday(getNthWeekdayOfMonth(year, 6, DayOfWeek.SUNDAY, 3), "Father's Day"))

        // Juneteenth - June 19
        holidays.add(Holiday(LocalDate.of(year, 6, 19), "Juneteenth"))

        // Independence Day - July 4
        holidays.add(Holiday(LocalDate.of(year, 7, 4), "Independence Day"))

        // Labor Day - 1st Monday of September
        holidays.add(Holiday(getNthWeekdayOfMonth(year, 9, DayOfWeek.MONDAY, 1), "Labor Day"))

        // Columbus Day - 2nd Monday of October
        holidays.add(Holiday(getNthWeekdayOfMonth(year, 10, DayOfWeek.MONDAY, 2), "Columbus Day"))

        // Halloween - October 31
        holidays.add(Holiday(LocalDate.of(year, 10, 31), "Halloween"))

        // Daylight Saving Time Ends - 1st Sunday of November
        holidays.add(Holiday(getNthWeekdayOfMonth(year, 11, DayOfWeek.SUNDAY, 1), "DST Ends"))

        // Veterans Day - November 11
        holidays.add(Holiday(LocalDate.of(year, 11, 11), "Veterans Day"))

        // Thanksgiving - 4th Thursday of November
        val thanksgiving = getNthWeekdayOfMonth(year, 11, DayOfWeek.THURSDAY, 4)
        holidays.add(Holiday(thanksgiving, "Thanksgiving"))

        // Black Friday - Day after Thanksgiving
        holidays.add(Holiday(thanksgiving.plusDays(1), "Black Friday"))

        // Christmas Eve - December 24
        holidays.add(Holiday(LocalDate.of(year, 12, 24), "Christmas Eve"))

        // Christmas Day - December 25
        holidays.add(Holiday(LocalDate.of(year, 12, 25), "Christmas"))

        // New Year's Eve - December 31
        holidays.add(Holiday(LocalDate.of(year, 12, 31), "New Year's Eve"))

        // Add moon phases
        holidays.addAll(getMoonPhases(year))

        return holidays
    }

    /**
     * Get holidays for a specific date
     */
    fun getHolidaysForDate(date: LocalDate): List<Holiday> {
        return getUSHolidays(date.year).filter { it.date == date }
    }

    /**
     * Get only regular holidays (not moon phases) for a date
     */
    fun getRegularHolidaysForDate(date: LocalDate): List<Holiday> {
        return getHolidaysForDate(date).filter { !it.isMoonPhase }
    }

    /**
     * Get moon phases for a date
     */
    fun getMoonPhasesForDate(date: LocalDate): List<Holiday> {
        return getHolidaysForDate(date).filter { it.isMoonPhase }
    }

    /**
     * Get nth weekday of a month (e.g., 4th Thursday of November)
     */
    private fun getNthWeekdayOfMonth(year: Int, month: Int, weekday: DayOfWeek, n: Int): LocalDate {
        val firstOfMonth = LocalDate.of(year, month, 1)
        val firstWeekday = firstOfMonth.with(TemporalAdjusters.firstInMonth(weekday))
        return firstWeekday.plusWeeks((n - 1).toLong())
    }

    /**
     * Get last weekday of a month (e.g., last Monday of May)
     */
    private fun getLastWeekdayOfMonth(year: Int, month: Int, weekday: DayOfWeek): LocalDate {
        val lastOfMonth = LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth())
        return lastOfMonth.with(TemporalAdjusters.previousOrSame(weekday))
    }

    /**
     * Calculate Easter Sunday using the Anonymous Gregorian algorithm
     */
    private fun getEasterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    /**
     * Calculate moon phase value for a given date (returns phase 0-29.53)
     */
    private fun getMoonPhaseValue(date: LocalDate): Double {
        // Known new moon reference: January 6, 2000
        val referenceNewMoon = LocalDate.of(2000, 1, 6)
        val daysSinceReference = date.toEpochDay() - referenceNewMoon.toEpochDay()
        val phase = (daysSinceReference % LUNAR_CYCLE)
        return if (phase < 0) phase + LUNAR_CYCLE else phase
    }

    /**
     * Get all moon phases for a year
     */
    private fun getMoonPhases(year: Int): List<Holiday> {
        val phases = mutableListOf<Holiday>()

        // All 8 moon phase definitions (in days from new moon)
        val phaseTypes = listOf(
            Triple(0.0, "New Moon", "\uD83C\uDF11"),           // 🌑
            Triple(LUNAR_CYCLE / 8, "Waxing Crescent", "\uD83C\uDF12"),  // 🌒
            Triple(LUNAR_CYCLE / 4, "First Quarter", "\uD83C\uDF13"),    // 🌓
            Triple(3 * LUNAR_CYCLE / 8, "Waxing Gibbous", "\uD83C\uDF14"), // 🌔
            Triple(LUNAR_CYCLE / 2, "Full Moon", "\uD83C\uDF15"),        // 🌕
            Triple(5 * LUNAR_CYCLE / 8, "Waning Gibbous", "\uD83C\uDF16"), // 🌖
            Triple(3 * LUNAR_CYCLE / 4, "Last Quarter", "\uD83C\uDF17"),  // 🌗
            Triple(7 * LUNAR_CYCLE / 8, "Waning Crescent", "\uD83C\uDF18") // 🌘
        )

        // Find a reference new moon close to the start of the year
        val startOfYear = LocalDate.of(year, 1, 1)
        val startPhase = getMoonPhaseValue(startOfYear)

        // Days until next new moon from start of year
        val daysToNewMoon = if (startPhase == 0.0) 0.0 else LUNAR_CYCLE - startPhase
        val firstNewMoon = startOfYear.plusDays(daysToNewMoon.toLong())

        // Generate all phases for the year
        for (cycle in -1..13) {
            for ((offset, name, icon) in phaseTypes) {
                val totalDays = cycle * LUNAR_CYCLE + offset
                val phaseDate = firstNewMoon.plusDays(totalDays.toLong())

                // Only include phases within the year
                if (phaseDate.year == year) {
                    phases.add(Holiday(
                        date = phaseDate,
                        name = name,
                        icon = icon,
                        isMoonPhase = true
                    ))
                }
            }
        }

        return phases
    }

    /**
     * Get the current moon phase for a date
     */
    fun getMoonPhaseIcon(date: LocalDate): String {
        val phase = getMoonPhaseValue(date)
        val phaseIndex = floor(phase / (LUNAR_CYCLE / 8)).toInt() % 8
        return when (phaseIndex) {
            0 -> "\uD83C\uDF11" // 🌑 New Moon
            1 -> "\uD83C\uDF12" // 🌒 Waxing Crescent
            2 -> "\uD83C\uDF13" // 🌓 First Quarter
            3 -> "\uD83C\uDF14" // 🌔 Waxing Gibbous
            4 -> "\uD83C\uDF15" // 🌕 Full Moon
            5 -> "\uD83C\uDF16" // 🌖 Waning Gibbous
            6 -> "\uD83C\uDF17" // 🌗 Last Quarter
            7 -> "\uD83C\uDF18" // 🌘 Waning Crescent
            else -> "\uD83C\uDF15" // 🌕 Default to full moon
        }
    }
}
