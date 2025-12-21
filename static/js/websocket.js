/**
 * WebSocket Module
 * Generic WebSocket handler that dispatches custom events for different message types.
 * Other modules can listen for specific events without being coupled to this module.
 */
const WS = (function() {
    let ws = null;
    let reconnectTimer = null;

    function connect() {
        if (ws && ws.readyState === WebSocket.OPEN) return;

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        ws = new WebSocket(`${protocol}//${window.location.host}/ws`);

        ws.onopen = () => {
            console.log('WebSocket connected');
            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
                reconnectTimer = null;
            }
        };

        ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                // Dispatch a custom event for the message type
                // e.g., 'doorbell' -> 'ws:doorbell', 'proximity_wake' -> 'ws:proximity_wake'
                if (data.type) {
                    window.dispatchEvent(new CustomEvent('ws:' + data.type, {
                        detail: data.payload || {}
                    }));
                }
            } catch (e) {
                console.error('WebSocket message error:', e);
            }
        };

        ws.onclose = () => {
            console.log('WebSocket disconnected');
            // Reconnect after 5 seconds
            reconnectTimer = setTimeout(connect, 5000);
        };

        ws.onerror = (error) => {
            console.error('WebSocket error:', error);
        };
    }

    function init() {
        connect();

        // Reconnect immediately when page becomes visible (screen wake)
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible') {
                // If disconnected or connecting, force reconnect
                if (!ws || ws.readyState !== WebSocket.OPEN) {
                    console.log('Page visible, reconnecting WebSocket...');
                    if (reconnectTimer) {
                        clearTimeout(reconnectTimer);
                        reconnectTimer = null;
                    }
                    if (ws) {
                        ws.close();
                        ws = null;
                    }
                    connect();
                }
            }
        });
    }

    return {
        init,
        connect
    };
})();

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', WS.init);
