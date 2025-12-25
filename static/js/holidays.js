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

// Get US Federal Holidays for a given year
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

    // Presidents' Day - 3rd Monday of February
    holidays.push({
        date: getNthWeekdayOfMonth(year, 1, 1, 3),
        name: "Presidents' Day"
    });

    // Memorial Day - Last Monday of May
    holidays.push({
        date: getLastWeekdayOfMonth(year, 4, 1),
        name: "Memorial Day"
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

    // Christmas Day - December 25
    holidays.push({
        date: new Date(year, 11, 25),
        name: "Christmas"
    });

    return holidays;
}

// Get holiday for a specific date (returns holiday object or null)
function getHolidayForDate(date) {
    if (!isHolidaysEnabled()) return null;

    const year = date.getFullYear();
    const holidays = getUSHolidays(year);

    const dateStr = formatDateKey(date);
    for (const holiday of holidays) {
        if (formatDateKey(holiday.date) === dateStr) {
            return holiday;
        }
    }
    return null;
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
        // Remove existing holiday badge
        const existing = dayCell.querySelector('.holiday-badge');
        if (existing) existing.remove();

        const dateStr = dayCell.dataset.date;
        if (!dateStr) return;

        const date = new Date(dateStr + 'T12:00:00');
        const holiday = getHolidayForDate(date);

        if (holiday) {
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

                const badge = document.createElement('span');
                badge.className = 'holiday-badge';
                badge.textContent = holiday.name;
                badge.title = holiday.name;
                dayNumber.appendChild(badge);
            }
        }
    });
}

// Render holidays in week view
function renderWeekViewHolidays() {
    const weekDays = document.querySelectorAll('.week-day');

    weekDays.forEach(dayColumn => {
        // Remove existing holiday badge
        const existing = dayColumn.querySelector('.holiday-badge');
        if (existing) existing.remove();

        const dateStr = dayColumn.dataset.date;
        if (!dateStr) return;

        const date = new Date(dateStr + 'T12:00:00');
        const holiday = getHolidayForDate(date);

        if (holiday) {
            const header = dayColumn.querySelector('.week-day-header');
            if (header) {
                const badge = document.createElement('span');
                badge.className = 'holiday-badge';
                badge.textContent = holiday.name;
                badge.title = holiday.name;
                header.appendChild(badge);
            }
        }
    });
}

// Render holidays in day view
function renderDayViewHolidays() {
    const allDayEvents = document.querySelector('.all-day-events');
    if (!allDayEvents) return;

    // Remove existing holiday event
    const existing = allDayEvents.querySelector('.holiday-event');
    if (existing) existing.remove();

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

    const holiday = getHolidayForDate(date);

    if (holiday) {
        const holidayEvent = document.createElement('div');
        holidayEvent.className = 'holiday-event';
        holidayEvent.textContent = holiday.name;
        allDayEvents.insertBefore(holidayEvent, allDayEvents.firstChild);
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
