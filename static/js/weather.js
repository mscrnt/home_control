// ===== Weather Module =====
let weatherData = null;

// Format time according to user's time format setting
function formatWeatherTime(date, includeMinutes = true) {
    const format = typeof getTimeFormat === 'function' ? getTimeFormat() : '24';
    const hours = date.getHours();
    const minutes = date.getMinutes();

    if (format === '12') {
        const period = hours >= 12 ? 'PM' : 'AM';
        const displayHour = hours % 12 || 12;
        if (includeMinutes) {
            return `${displayHour}:${minutes.toString().padStart(2, '0')} ${period}`;
        }
        return `${displayHour} ${period}`;
    } else {
        if (includeMinutes) {
            return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}`;
        }
        return `${hours.toString().padStart(2, '0')}:00`;
    }
}

async function loadWeather() {
    try {
        const resp = await fetch('/api/weather');
        if (!resp.ok) {
            throw new Error('Weather not available');
        }
        weatherData = await resp.json();
        updateWeatherWidget();
    } catch (err) {
        console.log('Weather not configured or unavailable');
        // Hide weather widgets if not available
        document.querySelectorAll('.weather-widget').forEach(el => {
            el.style.display = 'none';
        });
    }
}

function updateWeatherWidget() {
    if (!weatherData || !weatherData.current) return;

    const temp = Math.round(weatherData.current.temp);
    const icon = getWeatherEmoji(weatherData.current.icon);

    // Update all weather widgets (one per view)
    ['Month', 'Week', 'Day'].forEach(view => {
        const iconEl = document.getElementById('weatherIcon' + view);
        const tempEl = document.getElementById('weatherTemp' + view);
        if (iconEl) iconEl.textContent = icon;
        if (tempEl) tempEl.textContent = temp + '°';
    });
}

function getWeatherEmoji(iconCode) {
    const icons = {
        'sun': '☀️',
        'moon': '🌙',
        'cloud-sun': '⛅',
        'cloud': '☁️',
        'clouds': '☁️',
        'cloud-rain': '🌧️',
        'cloud-showers': '🌧️',
        'bolt': '⚡',
        'snowflake': '❄️',
        'smog': '🌫️'
    };
    return icons[iconCode] || '🌤️';
}

function getMoonPhase(date = new Date()) {
    // Calculate moon phase (0-29.53 day cycle)
    // Reference: January 6, 2000 was a new moon
    const reference = new Date(2000, 0, 6, 18, 14, 0);
    const diff = date - reference;
    const days = diff / (1000 * 60 * 60 * 24);
    const lunarCycle = 29.53058770576;
    const phase = ((days % lunarCycle) + lunarCycle) % lunarCycle;

    // Return phase info
    if (phase < 1.85) return { emoji: '🌑', name: 'New Moon' };
    if (phase < 5.53) return { emoji: '🌒', name: 'Waxing Crescent' };
    if (phase < 9.22) return { emoji: '🌓', name: 'First Quarter' };
    if (phase < 12.91) return { emoji: '🌔', name: 'Waxing Gibbous' };
    if (phase < 16.61) return { emoji: '🌕', name: 'Full Moon' };
    if (phase < 20.30) return { emoji: '🌖', name: 'Waning Gibbous' };
    if (phase < 23.99) return { emoji: '🌗', name: 'Last Quarter' };
    if (phase < 27.68) return { emoji: '🌘', name: 'Waning Crescent' };
    return { emoji: '🌑', name: 'New Moon' };
}

function openWeatherModal() {
    if (!weatherData) {
        alert('Weather data not available');
        return;
    }

    const modal = document.getElementById('weatherModal');
    const content = document.getElementById('weatherModalContent');

    content.innerHTML = renderWeatherContent();
    modal.classList.add('active');
}

function closeWeatherModal() {
    document.getElementById('weatherModal').classList.remove('active');
}

function renderWeatherContent() {
    if (!weatherData) return '<div class="weather-error">Weather data not available</div>';

    const current = weatherData.current;
    const hourly = weatherData.hourly || [];
    const daily = weatherData.daily || [];
    const moonPhase = getMoonPhase();

    // Format sunrise/sunset times using user's time format preference
    const sunrise = formatWeatherTime(new Date(current.sunrise * 1000), true);
    const sunset = formatWeatherTime(new Date(current.sunset * 1000), true);

    let html = `
        <div class="weather-current">
            <div class="weather-current-main">
                <span class="weather-current-icon">${getWeatherEmoji(current.icon)}</span>
                <div class="weather-current-info">
                    <span class="weather-current-temp">${Math.round(current.temp)}°</span>
                    <span class="weather-current-condition">${current.condition}</span>
                </div>
            </div>
            <div class="weather-current-details">
                <div class="weather-detail">
                    <span class="weather-detail-icon">🌡️</span>
                    <span class="weather-detail-value">${Math.round(current.feelsLike)}°</span>
                    <span class="weather-detail-label">Feels like</span>
                </div>
                <div class="weather-detail">
                    <span class="weather-detail-icon">💧</span>
                    <span class="weather-detail-value">${current.humidity}%</span>
                    <span class="weather-detail-label">Humidity</span>
                </div>
                <div class="weather-detail">
                    <span class="weather-detail-icon">💨</span>
                    <span class="weather-detail-value">${Math.round(current.windSpeed)}</span>
                    <span class="weather-detail-label">mph</span>
                </div>
                <div class="weather-detail">
                    <span class="weather-detail-icon">☁️</span>
                    <span class="weather-detail-value">${current.clouds}%</span>
                    <span class="weather-detail-label">Clouds</span>
                </div>
            </div>
            <div class="weather-sun-moon">
                <div class="weather-sun"><span class="sun-icon">🌅</span><span class="sun-time">${sunrise}</span></div>
                <div class="weather-sun"><span class="sun-icon">🌇</span><span class="sun-time">${sunset}</span></div>
                <div class="weather-moon"><span class="moon-icon">${moonPhase.emoji}</span><span class="moon-phase">${moonPhase.name}</span></div>
            </div>
        </div>
    `;

    // Hourly forecast - filter to only current day
    const today = new Date();
    const todayStr = today.toDateString();
    const todayHourly = hourly.filter(hour => {
        const hourDate = new Date(hour.time * 1000);
        return hourDate.toDateString() === todayStr;
    });

    if (todayHourly.length > 0) {
        html += '<div class="weather-section">';
        html += '<div class="weather-section-title">Today</div>';
        html += '<div class="weather-hourly">';
        todayHourly.forEach((hour) => {
            const time = new Date(hour.time * 1000);
            const timeStr = formatWeatherTime(time, false);
            html += `
                <div class="weather-hourly-item">
                    <div class="hourly-time">${timeStr}</div>
                    <div class="hourly-icon">${getWeatherEmoji(hour.icon)}</div>
                    <div class="hourly-temp">${Math.round(hour.temp)}°</div>
                    ${hour.pop > 0.1 ? `<div class="hourly-pop">${Math.round(hour.pop * 100)}%</div>` : ''}
                </div>
            `;
        });
        html += '</div>';
        html += '</div>';
    }

    // Daily forecast
    if (daily.length > 0) {
        html += '<div class="weather-section">';
        html += '<div class="weather-section-title">5-Day Forecast</div>';
        html += '<div class="weather-daily">';
        daily.forEach((day, i) => {
            const date = new Date(day.time * 1000);
            const dayName = i === 0 ? 'Today' : date.toLocaleDateString('en-US', { weekday: 'short' });
            html += `
                <div class="weather-daily-item">
                    <div class="daily-day">${dayName}</div>
                    <div class="daily-icon">${getWeatherEmoji(day.icon)}</div>
                    ${day.pop > 0.1 ? `<div class="daily-pop">💧 ${Math.round(day.pop * 100)}%</div>` : '<div class="daily-pop"></div>'}
                    <div class="daily-temps">
                        <span class="daily-high">${Math.round(day.tempMax)}°</span>
                        <span class="daily-low">${Math.round(day.tempMin)}°</span>
                    </div>
                </div>
            `;
        });
        html += '</div>';
        html += '</div>';
    }

    return html;
}

// Initialize weather on DOM ready
document.addEventListener('DOMContentLoaded', () => {
    loadWeather();
});
