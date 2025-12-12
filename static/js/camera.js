// Camera/Doorbell WebSocket Module
const Camera = (function() {
    // State variables
    let ws = null;
    let wsReconnectTimer = null;
    let cameraModalTimeout = null;
    let doorbellAudioCtx = null;
    let audioEnabled = false;

    // Initialize audio context on first user interaction
    function initAudioContext() {
        if (audioEnabled) return;

        if (!doorbellAudioCtx) {
            doorbellAudioCtx = new (window.AudioContext || window.webkitAudioContext)();
            console.log('AudioContext initialized, state:', doorbellAudioCtx.state);
        }
        if (doorbellAudioCtx.state === 'suspended') {
            doorbellAudioCtx.resume().then(() => {
                console.log('AudioContext resumed');
            });
        }

        audioEnabled = true;
    }

    function connectWebSocket() {
        if (ws && ws.readyState === WebSocket.OPEN) return;

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        ws = new WebSocket(`${protocol}//${window.location.host}/ws`);

        ws.onopen = () => {
            console.log('WebSocket connected');
            if (wsReconnectTimer) {
                clearTimeout(wsReconnectTimer);
                wsReconnectTimer = null;
            }
        };

        ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                if (data.type === 'doorbell') {
                    showCameraModal(data.payload.camera || 'doorbell');
                }
            } catch (e) {
                console.error('WebSocket message error:', e);
            }
        };

        ws.onclose = () => {
            console.log('WebSocket disconnected');
            // Reconnect after 5 seconds
            wsReconnectTimer = setTimeout(connectWebSocket, 5000);
        };

        ws.onerror = (error) => {
            console.error('WebSocket error:', error);
        };
    }

    function showCameraModal(cameraName) {
        // Dismiss screensaver if active (doorbell takes priority)
        if (window.dismissScreensaver) {
            window.dismissScreensaver();
        }

        const modal = document.getElementById('cameraModal');
        const title = document.getElementById('cameraModalTitle');
        const stream = document.getElementById('cameraStream');

        title.textContent = 'Doorbell';

        // Use snapshot first, then try stream
        stream.src = `/api/camera/${cameraName}/stream`;
        stream.onerror = () => {
            stream.src = `/api/camera/${cameraName}/snapshot`;
        };

        modal.classList.add('active');

        // Play doorbell sound
        playDoorbellSound();

        // Auto-close after 60 seconds
        if (cameraModalTimeout) {
            clearTimeout(cameraModalTimeout);
        }
        cameraModalTimeout = setTimeout(() => {
            closeCameraModal();
        }, 60000);
    }

    function closeCameraModal() {
        const modal = document.getElementById('cameraModal');
        const stream = document.getElementById('cameraStream');

        modal.classList.remove('active');
        stream.src = '';

        if (cameraModalTimeout) {
            clearTimeout(cameraModalTimeout);
            cameraModalTimeout = null;
        }
    }

    function playDoorbellSound() {
        console.log('playDoorbellSound called');

        // Create AudioContext if needed
        if (!doorbellAudioCtx) {
            doorbellAudioCtx = new (window.AudioContext || window.webkitAudioContext)();
        }

        // Resume if suspended (browser autoplay policy)
        if (doorbellAudioCtx.state === 'suspended') {
            console.log('AudioContext suspended, attempting resume...');
            doorbellAudioCtx.resume();
        }

        try {
            const ctx = doorbellAudioCtx;

            // First ding - higher pitch
            const osc1 = ctx.createOscillator();
            const gain1 = ctx.createGain();
            osc1.connect(gain1);
            gain1.connect(ctx.destination);
            osc1.type = 'sine';
            osc1.frequency.setValueAtTime(932, ctx.currentTime); // Bb5
            gain1.gain.setValueAtTime(0.6, ctx.currentTime);
            gain1.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.8);
            osc1.start(ctx.currentTime);
            osc1.stop(ctx.currentTime + 0.8);

            // Second ding - lower pitch (classic ding-dong)
            const osc2 = ctx.createOscillator();
            const gain2 = ctx.createGain();
            osc2.connect(gain2);
            gain2.connect(ctx.destination);
            osc2.type = 'sine';
            osc2.frequency.setValueAtTime(698, ctx.currentTime + 0.3); // F5
            gain2.gain.setValueAtTime(0, ctx.currentTime);
            gain2.gain.setValueAtTime(0.6, ctx.currentTime + 0.3);
            gain2.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 1.2);
            osc2.start(ctx.currentTime + 0.3);
            osc2.stop(ctx.currentTime + 1.2);

            console.log('Doorbell sound playing, ctx state:', ctx.state);
        } catch (e) {
            console.error('Could not play doorbell sound:', e);
        }
    }

    function init() {
        // Warm up audio on any user interaction
        document.addEventListener('click', initAudioContext, { once: true });
        document.addEventListener('touchstart', initAudioContext, { once: true });

        // Connect WebSocket
        connectWebSocket();
    }

    // Public API
    return {
        init,
        showCameraModal,
        closeCameraModal,
        playDoorbellSound,
        connectWebSocket
    };
})();

// Initialize on DOMContentLoaded
document.addEventListener('DOMContentLoaded', function() {
    Camera.init();
});

// Global function aliases for onclick handlers in HTML
function showCameraModal(cameraName) { Camera.showCameraModal(cameraName); }
function closeCameraModal() { Camera.closeCameraModal(); }
