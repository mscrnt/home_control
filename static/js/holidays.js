// ===== US Holidays Module =====

const HOLIDAYS_STORAGE_KEY = 'showHolidays';

// Check if holidays are enabled
function isHolidaysEnabled() {
    return localStorage.getItem(HOLIDAYS_STORAGE_KEY) !== 'false';
}

// Toggle holidays setting
function setHolidaysEnabled(enabled) {
    localStorage.setItem(HOLIDAYS_STORAGE_KEY, enabled ? 'true' : 'false');
    renderHolidays();
}

// Get nth weekday of a month (e.g., 4th Thursday of November)
function getNthWeekdayOfMonth(year, month, weekday, n) {
    const firstDay = new Date(year, month, 1);
    let dayOfWeek = firstDay.getDay();
    let diff = weekday - dayOfWeek;
    if (diff < 0) diff += 7;
    const firstOccurrence = 1 + diff;
    const nthOccurrence = firstOccurrence + (n - 1) * 7;
    return new Date(year, month, nthOccurrence);
}

// Get last weekday of a month (e.g., last Monday of May)
function getLastWeekdayOfMonth(year, month, weekday) {
    const lastDay = new Date(year, month + 1, 0);
    let diff = lastDay.getDay() - weekday;
    if (diff < 0) diff += 7;
    return new Date(year, month, lastDay.getDate() - diff);
}

// Calculate Easter Sunday using the Anonymous Gregorian algorithm
function getEasterSunday(year) {
    const a = year % 19;
    const b = Math.floor(year / 100);
    const c = year % 100;
    const d = Math.floor(b / 4);
    const e = b % 4;
    const f = Math.floor((b + 8) / 25);
    const g = Math.floor((b - f + 1) / 3);
    const h = (19 * a + b - d - g + 15) % 30;
    const i = Math.floor(c / 4);
    const k = c % 4;
    const l = (32 + 2 * e + 2 * i - h - k) % 7;
    const m = Math.floor((a + 11 * h + 22 * l) / 451);
    const month = Math.floor((h + l - 7 * m + 114) / 31) - 1;
    const day = ((h + l - 7 * m + 114) % 31) + 1;
    return new Date(year, month, day);
}

// Calculate moon phase for a given date (returns phase 0-29.53)
function getMoonPhaseValue(date) {
    const reference = new Date(2000, 0, 6, 18, 14, 0); // Known new moon
    const diff = date - reference;
    const days = diff / (1000 * 60 * 60 * 24);
    const lunarCycle = 29.53058770576;
    return ((days % lunarCycle) + lunarCycle) % lunarCycle;
}

// Get all moon phases for a year (all 8 phases)
function getMoonPhases(year) {
    const phases = [];
    const lunarCycle = 29.53058770576;

    // All 8 moon phase definitions (in days from new moon)
    const phaseTypes = [
        { offset: 0, name: 'New Moon', icon: '🌑' },
        { offset: lunarCycle / 8, name: 'Waxing Crescent', icon: '🌒' },
        { offset: lunarCycle / 4, name: 'First Quarter', icon: '🌓' },
        { offset: 3 * lunarCycle / 8, name: 'Waxing Gibbous', icon: '🌔' },
        { offset: lunarCycle / 2, name: 'Full Moon', icon: '🌕' },
        { offset: 5 * lunarCycle / 8, name: 'Waning Gibbous', icon: '🌖' },
        { offset: 3 * lunarCycle / 4, name: 'Last Quarter', icon: '🌗' },
        { offset: 7 * lunarCycle / 8, name: 'Waning Crescent', icon: '🌘' }
    ];

    // Find a reference new moon close to the start of the year
    const startOfYear = new Date(year, 0, 1);
    const startPhase = getMoonPhaseValue(startOfYear);

    // Days until next new moon from start of year
    const daysToNewMoon = startPhase === 0 ? 0 : lunarCycle - startPhase;
    let firstNewMoon = new Date(startOfYear.getTime() + daysToNewMoon * 24 * 60 * 60 * 1000);

    // Generate all phases for the year
    for (let cycle = -1; cycle < 14; cycle++) {
        for (const phaseType of phaseTypes) {
            const phaseDate = new Date(firstNewMoon.getTime() + (cycle * lunarCycle + phaseType.offset) * 24 * 60 * 60 * 1000);

            // Only include phases within the year
            if (phaseDate.getFullYear() === year) {
                phases.push({
                    date: new Date(phaseDate.getFullYear(), phaseDate.getMonth(), phaseDate.getDate()),
                    name: phaseType.name,
                    icon: phaseType.icon,
                    isMoonPhase: true
                });
            }
        }
    }

    return phases;
}

// Get US Holidays for a given year
function getUSHolidays(year) {
    const holidays = [];

    // New Year's Day - January 1
    holidays.push({
        date: new Date(year, 0, 1),
        name: "New Year's Day"
    });

    // Martin Luther King Jr. Day - 3rd Monday of January
    holidays.push({
        date: getNthWeekdayOfMonth(year, 0, 1, 3),
        name: "MLK Day"
    });

    // Valentine's Day - February 14
    holidays.push({
        date: new Date(year, 1, 14),
        name: "Valentine's Day"
    });

    // Presidents' Day - 3rd Monday of February
    holidays.push({
        date: getNthWeekdayOfMonth(year, 1, 1, 3),
        name: "Presidents' Day"
    });

    // Daylight Saving Time Starts - 2nd Sunday of March
    holidays.push({
        date: getNthWeekdayOfMonth(year, 2, 0, 2),
        name: "DST Starts"
    });

    // St. Patrick's Day - March 17
    holidays.push({
        date: new Date(year, 2, 17),
        name: "St. Patrick's Day"
    });

    // Easter Sunday
    holidays.push({
        date: getEasterSunday(year),
        name: "Easter"
    });

    // Mother's Day - 2nd Sunday of May
    holidays.push({
        date: getNthWeekdayOfMonth(year, 4, 0, 2),
        name: "Mother's Day"
    });

    // Memorial Day - Last Monday of May
    holidays.push({
        date: getLastWeekdayOfMonth(year, 4, 1),
        name: "Memorial Day"
    });

    // Father's Day - 3rd Sunday of June
    holidays.push({
        date: getNthWeekdayOfMonth(year, 5, 0, 3),
        name: "Father's Day"
    });

    // Juneteenth - June 19
    holidays.push({
        date: new Date(year, 5, 19),
        name: "Juneteenth"
    });

    // Independence Day - July 4
    holidays.push({
        date: new Date(year, 6, 4),
        name: "Independence Day"
    });

    // Labor Day - 1st Monday of September
    holidays.push({
        date: getNthWeekdayOfMonth(year, 8, 1, 1),
        name: "Labor Day"
    });

    // Columbus Day - 2nd Monday of October
    holidays.push({
        date: getNthWeekdayOfMonth(year, 9, 1, 2),
        name: "Columbus Day"
    });

    // Halloween - October 31
    holidays.push({
        date: new Date(year, 9, 31),
        name: "Halloween"
    });

    // Daylight Saving Time Ends - 1st Sunday of November
    holidays.push({
        date: getNthWeekdayOfMonth(year, 10, 0, 1),
        name: "DST Ends"
    });

    // Veterans Day - November 11
    holidays.push({
        date: new Date(year, 10, 11),
        name: "Veterans Day"
    });

    // Thanksgiving - 4th Thursday of November
    holidays.push({
        date: getNthWeekdayOfMonth(year, 10, 4, 4),
        name: "Thanksgiving"
    });

    // Black Friday - Day after Thanksgiving
    const thanksgiving = getNthWeekdayOfMonth(year, 10, 4, 4);
    holidays.push({
        date: new Date(year, 10, thanksgiving.getDate() + 1),
        name: "Black Friday"
    });

    // Christmas Eve - December 24
    holidays.push({
        date: new Date(year, 11, 24),
        name: "Christmas Eve"
    });

    // Christmas Day - December 25
    holidays.push({
        date: new Date(year, 11, 25),
        name: "Christmas"
    });

    // New Year's Eve - December 31
    holidays.push({
        date: new Date(year, 11, 31),
        name: "New Year's Eve"
    });

    // Add moon phases
    const moonPhases = getMoonPhases(year);
    holidays.push(...moonPhases);

    return holidays;
}

// Get holiday for a specific date (returns holiday object or null)
// Get first holiday for a date (for backward compatibility)
function getHolidayForDate(date) {
    if (!isHolidaysEnabled()) return null;

    const year = date.getFullYear();
    const holidays = getUSHolidays(year);

    const dateStr = formatDateKey(date);
    for (const holiday of holidays) {
        if (formatDateKey(holiday.date) === dateStr && !holiday.isMoonPhase) {
            return holiday;
        }
    }
    return null;
}

// Get all holidays for a date (including moon phases)
function getHolidaysForDate(date) {
    if (!isHolidaysEnabled()) return [];

    const year = date.getFullYear();
    const holidays = getUSHolidays(year);

    const dateStr = formatDateKey(date);
    return holidays.filter(holiday => formatDateKey(holiday.date) === dateStr);
}

// Format date as YYYY-MM-DD for comparison
function formatDateKey(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

// Render holidays on all calendar views
function renderHolidays() {
    if (!isHolidaysEnabled()) {
        // Remove all holiday elements
        document.querySelectorAll('.holiday-badge, .holiday-event').forEach(el => el.remove());
        return;
    }

    renderMonthViewHolidays();
    renderWeekViewHolidays();
    renderDayViewHolidays();
}

// Render holidays in month view
function renderMonthViewHolidays() {
    const monthDays = document.querySelectorAll('.month-day');

    monthDays.forEach(dayCell => {
        // Remove existing holiday badges and moon icons
        dayCell.querySelectorAll('.holiday-badge, .moon-icon').forEach(el => el.remove());

        const dateStr = dayCell.dataset.date;
        if (!dateStr) return;

        const date = new Date(dateStr + 'T12:00:00');
        const holidays = getHolidaysForDate(date);

        if (holidays.length > 0) {
            const dayNumber = dayCell.querySelector('.day-number');
            if (dayNumber) {
                // Wrap the day number text in a span if not already wrapped
                if (!dayNumber.querySelector('.day-num')) {
                    const numText = dayNumber.textContent.trim();
                    dayNumber.textContent = '';
                    const numSpan = document.createElement('span');
                    numSpan.className = 'day-num';
                    numSpan.textContent = numText;
                    dayNumber.appendChild(numSpan);
                }

                // Add regular holidays as badges
                const regularHolidays = holidays.filter(h => !h.isMoonPhase);
                regularHolidays.forEach(holiday => {
                    const badge = document.createElement('span');
                    badge.className = 'holiday-badge';
                    badge.textContent = holiday.name;
                    badge.title = holiday.name;
                    dayNumber.appendChild(badge);
                });

                // Add moon phases as icons (aligned right)
                const moonPhases = holidays.filter(h => h.isMoonPhase);
                moonPhases.forEach(moon => {
                    const moonIcon = document.createElement('span');
                    moonIcon.className = 'moon-icon';
                    moonIcon.textContent = moon.icon;
                    moonIcon.title = moon.name;
                    dayNumber.appendChild(moonIcon);
                });
            }
        }
    });
}

// Render holidays in week view
function renderWeekViewHolidays() {
    const weekDays = document.querySelectorAll('.week-day');

    weekDays.forEach(dayColumn => {
        // Remove existing holiday events
        dayColumn.querySelectorAll('.holiday-event').forEach(el => el.remove());

        const dateStr = dayColumn.dataset.date;
        if (!dateStr) return;

        const date = new Date(dateStr + 'T12:00:00');
        const holidays = getHolidaysForDate(date);

        if (holidays.length > 0) {
            const eventsContainer = dayColumn.querySelector('.week-day-events');
            if (eventsContainer) {
                // Add each holiday/moon phase
                holidays.forEach(holiday => {
                    const holidayEvent = document.createElement('div');
                    holidayEvent.className = 'holiday-event' + (holiday.isMoonPhase ? ' moon-phase' : '');
                    holidayEvent.textContent = holiday.isMoonPhase ? `${holiday.icon} ${holiday.name}` : holiday.name;
                    holidayEvent.title = holiday.name;
                    // Insert at the beginning of events
                    eventsContainer.insertBefore(holidayEvent, eventsContainer.firstChild);
                });
            }
        }
    });
}

// Render holidays in day view
function renderDayViewHolidays() {
    const allDayEvents = document.querySelector('.all-day-events');
    if (!allDayEvents) return;

    // Remove existing holiday events
    allDayEvents.querySelectorAll('.holiday-event').forEach(el => el.remove());

    // Get current date from URL or page context
    const urlParams = new URLSearchParams(window.location.search);
    const dateParam = urlParams.get('date');

    let date;
    if (dateParam) {
        date = new Date(dateParam + 'T12:00:00');
    } else {
        // Try to get date from day view title
        const titleEl = document.querySelector('.day-view .current-date');
        if (!titleEl) return;
        date = new Date();
    }

    const holidays = getHolidaysForDate(date);

    if (holidays.length > 0) {
        // Add each holiday/moon phase
        holidays.forEach(holiday => {
            const holidayEvent = document.createElement('div');
            holidayEvent.className = 'holiday-event' + (holiday.isMoonPhase ? ' moon-phase' : '');
            holidayEvent.textContent = holiday.isMoonPhase ? `${holiday.icon} ${holiday.name}` : holiday.name;
            allDayEvents.insertBefore(holidayEvent, allDayEvents.firstChild);
        });
    }
}

// Initialize holidays on page load
document.addEventListener('DOMContentLoaded', () => {
    // Update settings toggle state
    updateHolidaysToggle();

    // Render holidays
    setTimeout(renderHolidays, 100);
});

// Update the toggle button state
function updateHolidaysToggle() {
    const toggle = document.getElementById('holidaysToggle');
    if (toggle) {
        toggle.checked = isHolidaysEnabled();
    }
}
