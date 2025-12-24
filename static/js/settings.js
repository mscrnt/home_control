/**
 * Settings Module
 * Handles theme selection, time format preferences, and settings modal.
 */
const Settings = (function() {
    // Default values
    const DEFAULT_THEME = 'dark';
    const DEFAULT_TIME_FORMAT = '12';

    // Apply theme to document
    function applyTheme(theme) {
        if (theme === 'auto') {
            const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
            document.documentElement.setAttribute('data-theme', prefersDark ? 'dark' : 'light');
        } else {
            document.documentElement.setAttribute('data-theme', theme);
        }
    }

    // Get current theme from localStorage
    function getTheme() {
        return localStorage.getItem('theme') || DEFAULT_THEME;
    }

    // Set theme and update UI
    function setTheme(theme) {
        localStorage.setItem('theme', theme);
        applyTheme(theme);

        // Update button states
        document.querySelectorAll('.theme-option').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.theme === theme);
        });
    }

    // Load theme setting into UI (for modal)
    function loadThemeSetting() {
        const theme = getTheme();
        document.querySelectorAll('.theme-option').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.theme === theme);
        });
    }

    // Get current time format from localStorage
    function getTimeFormat() {
        return localStorage.getItem('timeFormat') || DEFAULT_TIME_FORMAT;
    }

    // Set time format and update UI
    function setTimeFormat(format) {
        const oldFormat = localStorage.getItem('timeFormat');
        localStorage.setItem('timeFormat', format);
        document.querySelectorAll('.format-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.format === format);
        });
        // Reload page if format actually changed to apply new formatting
        if (oldFormat !== format) {
            location.reload();
        }
    }

    // Load time format setting into UI (for modal)
    function loadTimeFormatSetting() {
        const format = getTimeFormat();
        document.querySelectorAll('.format-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.format === format);
        });
    }

    // Open settings modal
    function open() {
        const modal = document.getElementById('settingsModal');
        if (modal) {
            modal.classList.add('active');
            loadThemeSetting();
            loadTimeFormatSetting();
        }
    }

    // Close settings modal
    function close() {
        const modal = document.getElementById('settingsModal');
        if (modal) {
            modal.classList.remove('active');
        }
    }

    // Initialize - apply theme on load and listen for system changes
    function init() {
        // Apply saved theme immediately
        applyTheme(getTheme());

        // Listen for system theme changes when in auto mode
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
            if (getTheme() === 'auto') {
                applyTheme('auto');
            }
        });
    }

    // Public API
    return {
        init: init,
        open: open,
        close: close,
        getTheme: getTheme,
        setTheme: setTheme,
        getTimeFormat: getTimeFormat,
        setTimeFormat: setTimeFormat
    };
})();

// Initialize immediately (theme should apply before DOM ready for no flash)
Settings.init();

// Global function aliases for onclick handlers in HTML
function openSettings() { Settings.open(); }
function closeSettings() { Settings.close(); }
function setTheme(theme) { Settings.setTheme(theme); }
function setTimeFormat(format) { Settings.setTimeFormat(format); }
function getTimeFormat() { return Settings.getTimeFormat(); }

// Tablet control functions
async function reloadTablet() {
    try {
        const resp = await fetch('/api/tablet/reload', { method: 'POST' });
        if (resp.ok) {
            console.log('Tablet reload triggered');
        } else {
            console.error('Failed to reload tablet');
        }
    } catch (err) {
        console.error('Error reloading tablet:', err);
    }
}

async function exitKiosk() {
    if (!confirm('Exit kiosk mode? This will unlock the tablet.')) {
        return;
    }
    try {
        const resp = await fetch('/api/tablet/kiosk/exit', { method: 'POST' });
        if (resp.ok) {
            console.log('Kiosk exit triggered');
        } else {
            console.error('Failed to exit kiosk mode');
        }
    } catch (err) {
        console.error('Error exiting kiosk:', err);
    }
}
