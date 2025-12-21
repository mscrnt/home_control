/**
 * Screensaver Module
 * Handles inactivity-based screensaver with photo slideshow, clock display,
 * and Spotify now-playing overlay.
 */
const Screensaver = (function() {
    // Private state
    let config = { timeout: 300, hasPhotosFolder: false };
    let inactivityTimer = null;
    let photoTimer = null;
    let backgroundTimer = null;
    let clockTimer = null;
    let isActive = false;
    let photos = [];
    let currentPhotoIndex = 0;
    let currentBgElement = 0;

    // Check if a video modal is currently open (don't activate screensaver over videos)
    function isVideoModalOpen() {
        return document.querySelector('#cameraModal.active, #camerasModal.active, #cameraViewModal.active') !== null;
    }

    // Load screensaver config from server
    async function loadConfig() {
        try {
            const resp = await fetch('/api/screensaver/config');
            if (resp.ok) {
                config = await resp.json();
                console.log('Screensaver config loaded:', config);

                if (config.hasPhotosFolder) {
                    loadPhotos();
                    loadBackgroundPhoto();
                    // Rotate background every 60 seconds
                    backgroundTimer = setInterval(loadBackgroundPhoto, 60000);
                }

                // Start inactivity tracking if timeout is configured
                if (config.timeout > 0) {
                    startInactivityTracking();
                    console.log(`Screensaver will activate after ${config.timeout} seconds of inactivity`);
                }
            }
        } catch (err) {
            console.log('Screensaver not configured:', err);
        }
    }

    // Load all photos for screensaver
    async function loadPhotos() {
        try {
            const resp = await fetch('/api/drive/photos');
            if (resp.ok) {
                photos = await resp.json();
                console.log(`Loaded ${photos.length} photos`);
                // Shuffle the photos
                photos.sort(() => Math.random() - 0.5);
            }
        } catch (err) {
            console.error('Failed to load photos:', err);
        }
    }

    // Load a random background photo for the page
    async function loadBackgroundPhoto() {
        try {
            const resp = await fetch('/api/drive/photos/random');
            if (resp.ok) {
                const photo = await resp.json();
                const bg = document.getElementById('pageBackground');
                if (bg && photo.id) {
                    const photoUrl = `/api/drive/photo/${photo.id}`;
                    bg.style.backgroundImage = `url(${photoUrl})`;
                    bg.classList.add('active');
                    console.log(`Background photo loaded: ${photo.name}`);
                }
            }
        } catch (err) {
            console.log('No background photo available');
        }
    }

    // Start tracking user inactivity
    function startInactivityTracking() {
        const events = ['mousedown', 'mousemove', 'keydown', 'touchstart', 'scroll', 'click'];

        function resetTimer() {
            if (isActive) {
                hide();
            }

            clearTimeout(inactivityTimer);
            inactivityTimer = setTimeout(() => {
                show();
            }, config.timeout * 1000);
        }

        events.forEach(event => {
            document.addEventListener(event, resetTimer, { passive: true });
        });

        // Start the initial timer
        resetTimer();
    }

    // Show the screensaver
    function show() {
        if (isActive) return;

        // Don't activate if a video modal is open
        if (isVideoModalOpen()) {
            console.log('Screensaver skipped: video modal is open');
            return;
        }

        isActive = true;

        const screensaver = document.getElementById('screensaver');
        if (screensaver) {
            screensaver.classList.add('active');
        }

        // Update clock immediately and start interval
        updateClock();
        clockTimer = setInterval(updateClock, 1000);

        // Show first photo and start cycling (60 second intervals)
        if (photos.length > 0) {
            showNextPhoto();
            photoTimer = setInterval(showNextPhoto, 60000);
        }
    }

    // Hide the screensaver
    function hide() {
        if (!isActive) return;
        isActive = false;

        const screensaver = document.getElementById('screensaver');
        if (screensaver) {
            screensaver.classList.remove('active');
        }

        // Stop photo cycling
        if (photoTimer) {
            clearInterval(photoTimer);
            photoTimer = null;
        }

        // Stop clock updates
        if (clockTimer) {
            clearInterval(clockTimer);
            clockTimer = null;
        }
    }

    // Update the screensaver clock display
    function updateClock() {
        const now = new Date();
        const timeEl = document.getElementById('screensaverTime');
        const dateEl = document.getElementById('screensaverDate');

        if (timeEl) {
            timeEl.textContent = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        }
        if (dateEl) {
            dateEl.textContent = now.toLocaleDateString([], {
                weekday: 'long',
                month: 'long',
                day: 'numeric'
            });
        }
    }

    // Show next photo with crossfade effect
    function showNextPhoto() {
        if (photos.length === 0) return;

        const photo = photos[currentPhotoIndex];
        const photoUrl = `/api/drive/photo/${photo.id}`;

        console.log(`Loading screensaver photo: ${photo.name} (${photo.id})`);

        const bgs = document.querySelectorAll('.screensaver-bg');
        if (bgs.length < 2) return;

        const currentBg = bgs[currentBgElement];
        const nextBg = bgs[1 - currentBgElement];

        // Preload the image before showing
        const img = new Image();
        img.onload = () => {
            nextBg.style.backgroundImage = `url(${photoUrl})`;
            nextBg.classList.add('active');
            setTimeout(() => {
                currentBg.classList.remove('active');
            }, 1500);
            currentBgElement = 1 - currentBgElement;
        };
        img.onerror = () => {
            console.error(`Failed to load photo: ${photo.name}`);
        };
        img.src = photoUrl;

        currentPhotoIndex = (currentPhotoIndex + 1) % photos.length;
    }

    // Update Spotify display on screensaver
    function updateSpotifyDisplay(playbackData) {
        const container = document.getElementById('screensaverSpotify');
        if (!container) return;

        // Hide if no playback or paused
        if (!playbackData || !playbackData.item || !playbackData.is_playing) {
            container.style.display = 'none';
            return;
        }

        container.style.display = 'flex';

        const track = playbackData.item;
        const album = track.album || {};
        const artists = track.artists ? track.artists.map(a => a.name).join(', ') : '';
        const albumArt = album.images && album.images.length > 0
            ? album.images[0].url
            : '';

        // Update album art
        const artEl = document.getElementById('screensaverSpotifyArt');
        if (artEl) {
            artEl.innerHTML = albumArt ? `<img src="${albumArt}" alt="">` : '';
        }

        // Update track name
        const trackEl = document.getElementById('screensaverSpotifyTrack');
        if (trackEl) {
            trackEl.textContent = track.name;
        }

        // Update artist
        const artistEl = document.getElementById('screensaverSpotifyArtist');
        if (artistEl) {
            artistEl.textContent = artists;
        }
    }

    // Setup click/touch to dismiss
    function setupDismissHandlers() {
        const screensaver = document.getElementById('screensaver');
        if (screensaver) {
            screensaver.addEventListener('click', hide);
            screensaver.addEventListener('touchstart', hide);
        }
    }

    // Listen for proximity wake events from WebSocket module
    function setupProximityWakeListener() {
        window.addEventListener('ws:proximity_wake', function() {
            console.log('Received proximity_wake event, screensaver active:', isActive);
            if (isActive) {
                console.log('Screensaver dismissed by proximity wake');
                hide();
            }
        });

        // When WebSocket reconnects, check if someone is already near
        window.addEventListener('ws:connected', function() {
            if (isActive) {
                checkProximityState();
            }
        });

        // When page becomes visible (e.g., screen turns on), check proximity
        document.addEventListener('visibilitychange', function() {
            if (document.visibilityState === 'visible' && isActive) {
                checkProximityState();
            }
        });
    }

    // Check current proximity state from server (with retries for slow wake-up)
    async function checkProximityState() {
        // Retry up to 5 times with 500ms delays (2.5 seconds total)
        for (let attempt = 0; attempt < 5 && isActive; attempt++) {
            try {
                const resp = await fetch('/api/tablet/sensor/state');
                if (resp.ok) {
                    const state = await resp.json();
                    if (state.proximityNear && isActive) {
                        console.log('Screensaver dismissed: proximity detected (attempt ' + (attempt + 1) + ')');
                        hide();
                        return;
                    }
                }
            } catch (err) {
                // Network might not be ready yet, retry
            }
            if (attempt < 4 && isActive) {
                await new Promise(r => setTimeout(r, 500));
            }
        }
    }

    // Initialize the screensaver module
    function init() {
        setupDismissHandlers();
        setupProximityWakeListener();
        loadConfig();
    }

    // Public API
    return {
        init: init,
        dismiss: hide,
        updateSpotify: updateSpotifyDisplay,
        isActive: function() { return isActive; }
    };
})();

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', Screensaver.init);

// Expose dismiss function globally for doorbell events
window.dismissScreensaver = Screensaver.dismiss;
