/**
 * WebSocket Module
 * Generic WebSocket handler that dispatches custom events for different message types.
 * Other modules can listen for specific events without being coupled to this module.
 */
const WS = (function() {
    let ws = null;
    let reconnectTimer = null;
    let keepaliveTimer = null;
    let connectAttempts = 0;
    let lastConnectTime = null;

    const stateNames = ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'];

    function log(msg) {
        const now = new Date().toLocaleTimeString();
        console.log(`[WS ${now}] ${msg}`);
    }

    function getStatus() {
        return {
            state: ws ? stateNames[ws.readyState] : 'NULL',
            attempts: connectAttempts,
            lastConnect: lastConnectTime
        };
    }

    function connect() {
        if (ws && ws.readyState === WebSocket.OPEN) {
            log('Already connected, skipping');
            return;
        }

        connectAttempts++;
        const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`;
        log(`Connecting to ${wsUrl} (attempt ${connectAttempts})`);

        try {
            ws = new WebSocket(wsUrl);
        } catch (e) {
            log(`Failed to create WebSocket: ${e.message}`);
            reconnectTimer = setTimeout(connect, 2000);
            return;
        }

        ws.onopen = () => {
            lastConnectTime = new Date().toISOString();
            log(`Connected! (after ${connectAttempts} attempts)`);
            connectAttempts = 0;
            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
                reconnectTimer = null;
            }
            // Start keepalive pings to prevent connection from dropping
            startKeepalive();
            // Dispatch connected event for modules that need to know
            window.dispatchEvent(new CustomEvent('ws:connected'));
        };

        ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                log(`Received: ${data.type}`);
                // Dispatch a custom event for the message type
                // e.g., 'doorbell' -> 'ws:doorbell', 'proximity_wake' -> 'ws:proximity_wake'
                if (data.type) {
                    window.dispatchEvent(new CustomEvent('ws:' + data.type, {
                        detail: data.payload || {}
                    }));
                }
            } catch (e) {
                log(`Message parse error: ${e.message}`);
            }
        };

        ws.onclose = (event) => {
            log(`Disconnected: code=${event.code}, reason=${event.reason || 'none'}, clean=${event.wasClean}`);
            stopKeepalive();
            // Reconnect after 2 seconds (faster reconnect)
            reconnectTimer = setTimeout(connect, 2000);
        };

        ws.onerror = (error) => {
            log(`Error: ${error.message || 'unknown'}`);
        };
    }

    // Send periodic pings to keep WebSocket alive (prevents browser from closing idle connections)
    function startKeepalive() {
        stopKeepalive();
        keepaliveTimer = setInterval(() => {
            if (ws && ws.readyState === WebSocket.OPEN) {
                try {
                    ws.send(JSON.stringify({ type: 'ping' }));
                } catch (e) {
                    console.error('Keepalive ping failed:', e);
                }
            }
        }, 30000); // Ping every 30 seconds
    }

    function stopKeepalive() {
        if (keepaliveTimer) {
            clearInterval(keepaliveTimer);
            keepaliveTimer = null;
        }
    }

    function init() {
        log('Initializing WebSocket module');
        connect();

        // Reconnect immediately when page becomes visible (screen wake)
        document.addEventListener('visibilitychange', () => {
            const state = ws ? stateNames[ws.readyState] : 'NULL';
            log(`Visibility changed to ${document.visibilityState}, WS state: ${state}`);
            if (document.visibilityState === 'visible') {
                // If disconnected or connecting, force reconnect
                if (!ws || ws.readyState !== WebSocket.OPEN) {
                    log('Page visible but not connected, forcing reconnect');
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

        // Periodic connection check - ensure we're always connected
        setInterval(() => {
            if (!ws || ws.readyState !== WebSocket.OPEN) {
                log(`Connection check: not connected (state: ${ws ? stateNames[ws.readyState] : 'NULL'}), reconnecting...`);
                if (!reconnectTimer) {
                    connect();
                }
            }
        }, 5000);
    }

    return {
        init,
        connect,
        getStatus
    };
})();

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', WS.init);
