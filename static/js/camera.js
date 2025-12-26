// Camera/Doorbell Module
const Camera = (function() {
    // State variables
    let cameraModalTimeout = null;
    let doorbellAudioCtx = null;
    let audioEnabled = false;

    // Push-to-talk state
    let currentCameraName = null;
    let mediaRecorder = null;
    let audioChunks = [];
    let isTalking = false;
    let micStream = null;

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

    function showCameraModal(cameraName) {
        // Dismiss screensaver if active (doorbell takes priority)
        if (window.dismissScreensaver) {
            window.dismissScreensaver();
        }

        const modal = document.getElementById('cameraModal');
        const title = document.getElementById('cameraModalTitle');
        const stream = document.getElementById('cameraStream');

        // Store current camera name for push-to-talk
        currentCameraName = cameraName;

        title.textContent = 'Doorbell';

        // Reset talk button state
        resetTalkButton();

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

        // Clean up talk state
        stopTalking();
        currentCameraName = null;

        if (cameraModalTimeout) {
            clearTimeout(cameraModalTimeout);
            cameraModalTimeout = null;
        }
    }

    // Reset talk button to initial state
    function resetTalkButton() {
        const btn = document.getElementById('talkBtn');
        const text = document.getElementById('talkBtnText');
        if (btn) {
            btn.classList.remove('talking', 'sending', 'error');
        }
        if (text) {
            text.textContent = 'Hold to Talk';
        }
    }

    // Start recording audio for push-to-talk
    async function startTalking() {
        if (isTalking || !currentCameraName) return;

        const btn = document.getElementById('talkBtn');
        const text = document.getElementById('talkBtnText');

        try {
            // Request microphone permission
            micStream = await navigator.mediaDevices.getUserMedia({
                audio: {
                    sampleRate: 8000,
                    channelCount: 1,
                    echoCancellation: true,
                    noiseSuppression: true
                }
            });

            // Create AudioContext for processing
            const audioCtx = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 8000 });
            const source = audioCtx.createMediaStreamSource(micStream);
            const processor = audioCtx.createScriptProcessor(4096, 1, 1);

            audioChunks = [];

            processor.onaudioprocess = function(e) {
                if (!isTalking) return;
                const inputData = e.inputBuffer.getChannelData(0);
                // Convert Float32 to Int16 PCM
                const pcm = new Int16Array(inputData.length);
                for (let i = 0; i < inputData.length; i++) {
                    const s = Math.max(-1, Math.min(1, inputData[i]));
                    pcm[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
                }
                audioChunks.push(new Uint8Array(pcm.buffer));
            };

            source.connect(processor);
            processor.connect(audioCtx.destination);

            // Store for cleanup
            window._talkAudioCtx = audioCtx;
            window._talkProcessor = processor;
            window._talkSource = source;

            isTalking = true;
            btn.classList.add('talking');
            text.textContent = 'Recording...';

            console.log('Push-to-talk started for camera:', currentCameraName);

        } catch (err) {
            console.error('Failed to start recording:', err);
            btn.classList.add('error');
            text.textContent = 'Mic Error';
            setTimeout(resetTalkButton, 2000);
        }
    }

    // Stop recording and send audio
    async function stopTalking() {
        if (!isTalking) return;
        isTalking = false;

        const btn = document.getElementById('talkBtn');
        const text = document.getElementById('talkBtnText');

        // Stop microphone
        if (micStream) {
            micStream.getTracks().forEach(track => track.stop());
            micStream = null;
        }

        // Disconnect audio processing
        if (window._talkProcessor) {
            window._talkProcessor.disconnect();
            window._talkProcessor = null;
        }
        if (window._talkSource) {
            window._talkSource.disconnect();
            window._talkSource = null;
        }
        if (window._talkAudioCtx) {
            window._talkAudioCtx.close();
            window._talkAudioCtx = null;
        }

        if (audioChunks.length === 0) {
            resetTalkButton();
            return;
        }

        // Combine audio chunks
        const totalLength = audioChunks.reduce((acc, chunk) => acc + chunk.length, 0);
        const combinedAudio = new Uint8Array(totalLength);
        let offset = 0;
        for (const chunk of audioChunks) {
            combinedAudio.set(chunk, offset);
            offset += chunk.length;
        }
        audioChunks = [];

        console.log('Sending', combinedAudio.length, 'bytes of audio to camera:', currentCameraName);

        // Show sending state
        btn.classList.remove('talking');
        btn.classList.add('sending');
        text.textContent = 'Sending...';

        try {
            const response = await fetch(`/api/camera/${currentCameraName}/talk`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/octet-stream'
                },
                body: combinedAudio
            });

            if (!response.ok) {
                throw new Error(await response.text());
            }

            console.log('Audio sent successfully');
            text.textContent = 'Sent!';
            setTimeout(resetTalkButton, 1000);

        } catch (err) {
            console.error('Failed to send audio:', err);
            btn.classList.remove('sending');
            btn.classList.add('error');
            text.textContent = 'Failed';
            setTimeout(resetTalkButton, 2000);
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

        // Listen for doorbell events from WebSocket module
        window.addEventListener('ws:doorbell', function(e) {
            showCameraModal(e.detail.camera || 'doorbell');
        });
    }

    // Public API
    return {
        init,
        showCameraModal,
        closeCameraModal,
        playDoorbellSound,
        startTalking,
        stopTalking
    };
})();

// Initialize on DOMContentLoaded
document.addEventListener('DOMContentLoaded', function() {
    Camera.init();
});

// Global function aliases for onclick handlers in HTML
function showCameraModal(cameraName) { Camera.showCameraModal(cameraName); }
function closeCameraModal() { Camera.closeCameraModal(); }
function startTalking() { Camera.startTalking(); }
function stopTalking() { Camera.stopTalking(); }
