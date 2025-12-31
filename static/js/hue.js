/**
 * Hue Lights Module
 * Handles Philips Hue integration including rooms, lights, scenes,
 * entertainment areas, and Sync Box control.
 */
const Hue = (function() {
    // State
    let hueRooms = [];
    let activeHueTab = null;
    let selectedEntertainmentArea = null;
    let syncBoxes = [];
    let syncBoxStatuses = {};
    let selectedSyncBox = 0;
    let currentBrightnessLightId = null;
    let currentBrightnessLightIsOn = false;
    let currentSceneRoomId = null;

    // SSE state
    let eventSource = null;
    let sseConnected = false;
    let lastReloadTime = 0;
    let pendingReload = null;
    const SSE_DEBOUNCE_MS = 1000;

    // Helper function (uses global escapeHtml)
    function escape(text) {
        return typeof escapeHtml === 'function' ? escapeHtml(text) : text;
    }

    // ===== Room Data Loading =====
    async function loadRooms() {
        lastReloadTime = Date.now();
        try {
            const resp = await fetch('/api/hue/rooms');
            if (!resp.ok) {
                console.log('Hue not configured');
                updateSummary(null);
                return;
            }
            hueRooms = await resp.json();
            updateSummary(hueRooms);

            // Only re-render on entertainment tab (for streaming status updates)
            const modal = document.getElementById('hueModal');
            if (modal && modal.classList.contains('active') && activeHueTab === 'entertainment') {
                renderContent();
            }
        } catch (err) {
            console.error('Failed to load Hue rooms:', err);
            updateSummary(null);
        }
    }

    function updateSummary(rooms) {
        const summaryEl = document.getElementById('summary-Hue');
        if (!summaryEl) return;

        if (!rooms || rooms.length === 0) {
            summaryEl.textContent = 'Not configured';
            return;
        }

        const onCount = rooms.filter(r => r.isOn).length;
        if (onCount === 0) {
            summaryEl.textContent = 'All off';
        } else if (onCount === rooms.length) {
            summaryEl.textContent = 'All on';
        } else {
            summaryEl.textContent = `${onCount} room${onCount !== 1 ? 's' : ''} on`;
        }
    }

    // ===== Modal Control =====
    function openModal() {
        document.getElementById('hueModal').classList.add('active');
        renderContent();
    }

    function closeModal() {
        document.getElementById('hueModal').classList.remove('active');
    }

    // ===== Light Syncing Detection =====
    function isLightSyncing(lightId) {
        if (!hueRooms) return false;
        const entertainment = hueRooms.filter(r => r.type === 'Entertainment' && r.streamingActive);
        for (const area of entertainment) {
            if (area.lights && area.lights.some(l => l.id === lightId)) {
                return true;
            }
        }
        return false;
    }

    // ===== Main Content Rendering =====
    function renderContent() {
        const content = document.getElementById('hueModalContent');

        if (!hueRooms || hueRooms.length === 0) {
            content.innerHTML = '<div class="hue-loading">No Hue rooms found. Check your Hue bridge configuration.</div>';
            return;
        }

        const rooms = hueRooms.filter(r => r.type === 'Room' || r.type === 'Zone');
        const entertainment = hueRooms.filter(r => r.type === 'Entertainment');

        const tabs = rooms.map(r => ({
            id: r.id,
            name: r.name,
            icon: getRoomIcon(r.class || r.type),
            type: 'room'
        }));

        if (entertainment.length > 0) {
            tabs.push({
                id: 'entertainment',
                name: 'Entertainment Areas',
                icon: '🎬',
                type: 'entertainment'
            });
        }

        if (!activeHueTab || !tabs.find(t => t.id === activeHueTab)) {
            activeHueTab = tabs[0]?.id || null;
        }

        if (!selectedEntertainmentArea && entertainment.length > 0) {
            selectedEntertainmentArea = entertainment[0].id;
        }

        const tabsHtml = `
            <div class="hue-tabs">
                ${tabs.map(tab => `
                    <button class="hue-tab ${activeHueTab === tab.id ? 'active' : ''} ${tab.type === 'entertainment' ? 'entertainment' : ''}"
                            onclick="Hue.switchTab('${tab.id}')">
                        <span class="hue-tab-icon">${tab.icon}</span>
                        <span class="hue-tab-name">${escape(tab.name)}</span>
                    </button>
                `).join('')}
            </div>
        `;

        let contentHtml = '';
        if (activeHueTab === 'entertainment') {
            contentHtml = `
                <div class="hue-tab-content">
                    ${renderEntertainmentContent(entertainment)}
                </div>
            `;
        } else {
            const activeRoom = rooms.find(r => r.id === activeHueTab);
            if (activeRoom) {
                contentHtml = `
                    <div class="hue-tab-content">
                        ${renderRoomContent(activeRoom)}
                    </div>
                `;
            }
        }

        content.innerHTML = tabsHtml + contentHtml;
    }

    function switchTab(tabId) {
        activeHueTab = tabId;
        renderContent();
    }

    function selectEntertainmentArea(areaId) {
        selectedEntertainmentArea = areaId;
        renderContent();
    }

    // ===== Sync Box Functions =====
    async function loadSyncBoxes() {
        try {
            const resp = await fetch('/api/syncbox');
            if (resp.ok) {
                syncBoxes = await resp.json();
                for (const box of syncBoxes) {
                    loadSyncBoxStatus(box.index);
                }
            }
        } catch (err) {
            console.log('Sync boxes not available:', err);
        }
    }

    async function loadSyncBoxStatus(index) {
        try {
            const resp = await fetch(`/api/syncbox/${index}/status`);
            if (resp.ok) {
                syncBoxStatuses[index] = await resp.json();
                const modal = document.getElementById('hueModal');
                if (modal && modal.classList.contains('active') && activeHueTab === 'entertainment') {
                    renderContent();
                }
            }
        } catch (err) {
            console.log(`Failed to load sync box ${index} status:`, err);
        }
    }

    async function toggleSyncBox(index, active) {
        try {
            const resp = await fetch(`/api/syncbox/${index}/sync`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ active })
            });
            if (resp.ok) {
                await loadSyncBoxStatus(index);
            }
        } catch (err) {
            console.error('Failed to toggle sync:', err);
        }
    }

    async function setSyncBoxArea(index, groupId) {
        try {
            const resp = await fetch(`/api/syncbox/${index}/area`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ groupId })
            });
            if (resp.ok) {
                await loadSyncBoxStatus(index);
                await loadRooms();
            }
        } catch (err) {
            console.error('Failed to set entertainment area:', err);
        }
    }

    async function setSyncBoxMode(index, mode) {
        try {
            const resp = await fetch(`/api/syncbox/${index}/mode`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ mode })
            });
            if (resp.ok) {
                await loadSyncBoxStatus(index);
            }
        } catch (err) {
            console.error('Failed to set mode:', err);
        }
    }

    async function setSyncBoxInput(index, hdmiSource) {
        try {
            const resp = await fetch(`/api/syncbox/${index}/input`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ hdmiSource })
            });
            if (resp.ok) {
                await loadSyncBoxStatus(index);
            }
        } catch (err) {
            console.error('Failed to set HDMI input:', err);
        }
    }

    function selectSyncBox(index) {
        selectedSyncBox = index;
        renderContent();
    }

    async function stopSyncFromBanner() {
        for (let i = 0; i < syncBoxes.length; i++) {
            try {
                await fetch(`/api/syncbox/${i}/sync`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ active: false })
                });
            } catch (err) {
                console.error(`Failed to stop sync on box ${i}:`, err);
            }
        }
        await loadRooms();
        for (const box of syncBoxes) {
            loadSyncBoxStatus(box.index);
        }
        renderContent();
    }

    // ===== Entertainment Content Rendering =====
    function renderEntertainmentContent(areas) {
        const activeArea = areas.find(a => a.streamingActive);

        if (syncBoxes.length > 0) {
            return renderSyncBoxContent(areas, activeArea);
        }

        return `
            <div class="hue-entertainment-content">
                ${activeArea ? `
                <div class="hue-entertainment-active-banner">
                    <span class="active-indicator"></span>
                    <span>Currently Streaming: <strong>${escape(activeArea.name)}</strong></span>
                </div>
                ` : '<div class="hue-entertainment-notice">No Sync Boxes configured. Add SYNC_BOXES to .env to control streaming.</div>'}

                <div class="hue-entertainment-selector">
                    <label>Entertainment Areas</label>
                    <div class="hue-entertainment-areas">
                        ${areas.map(area => `
                            <div class="hue-entertainment-area-btn ${area.streamingActive ? 'activated' : ''}">
                                <span class="area-icon">🎬</span>
                                <span class="area-name">${escape(area.name)}</span>
                                <span class="area-lights">${area.lights?.length || 0} lights</span>
                                ${area.streamingActive ? '<span class="area-active-badge">STREAMING</span>' : ''}
                            </div>
                        `).join('')}
                    </div>
                </div>
            </div>
        `;
    }

    function renderSyncBoxContent(areas, activeArea) {
        const box = syncBoxes[selectedSyncBox];
        const status = syncBoxStatuses[selectedSyncBox] || {};
        const exec = status.execution || {};
        const hue = status.hue || {};
        const groups = hue.groups || {};
        const currentGroupId = exec.hueTarget || '';
        const isSyncing = exec.syncActive;
        const currentMode = exec.mode || 'video';
        const currentInput = exec.hdmiSource || 'input1';
        const hdmi = status.hdmi || {};

        const getHdmiName = (inputKey, defaultNum) => {
            const input = hdmi[inputKey];
            return input?.name || `HDMI ${defaultNum}`;
        };

        const boxStatuses = syncBoxes.map((sb, i) => {
            const s = syncBoxStatuses[i] || {};
            return s.execution?.syncActive || false;
        });

        return `
            <div class="hue-entertainment-content">
                <div class="hue-syncbox-panel">
                    <div class="hue-syncbox-header">
                        <div class="hue-syncbox-selector">
                            ${syncBoxes.map((sb, i) => {
                                const boxSyncing = boxStatuses[i];
                                return `
                                <label class="hue-syncbox-radio ${i === selectedSyncBox ? 'selected' : ''} ${boxSyncing ? 'syncing' : ''}"
                                       onclick="Hue.selectSyncBox(${i})">
                                    <input type="radio" name="syncbox" ${i === selectedSyncBox ? 'checked' : ''}>
                                    <span class="radio-name">${escape(sb.name)}</span>
                                    ${boxSyncing ? '<span class="radio-status">Syncing</span>' : ''}
                                </label>
                            `}).join('')}
                        </div>
                        <button class="hue-syncbox-toggle-btn ${isSyncing ? 'streaming' : ''}"
                                onclick="Hue.toggleSyncBox(${selectedSyncBox}, ${!isSyncing})">
                            ${isSyncing ? `
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <rect x="6" y="4" width="4" height="16"/>
                                <rect x="14" y="4" width="4" height="16"/>
                            </svg>
                            Stop Sync
                            ` : `
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <polygon points="5 3 19 12 5 21 5 3"/>
                            </svg>
                            Start Sync
                            `}
                        </button>
                    </div>

                    <div class="hue-syncbox-split-layout">
                        <div class="hue-syncbox-left-column">
                            <div class="hue-syncbox-grid-section">
                                <label>Entertainment Area</label>
                                <div class="hue-syncbox-grid-4col">
                                    ${Object.entries(groups).map(([id, group]) => `
                                        <button class="hue-syncbox-tile ${id === currentGroupId ? 'selected' : ''}"
                                                onclick="Hue.setSyncBoxArea(${selectedSyncBox}, '${id}')">
                                            <span class="tile-icon">🎬</span>
                                            <span class="tile-name">${escape(group.name)}</span>
                                            <span class="tile-info">${group.numLights} lights</span>
                                        </button>
                                    `).join('')}
                                </div>
                            </div>
                        </div>

                        <div class="hue-syncbox-right-column">
                            <div class="hue-syncbox-grid-section">
                                <label>HDMI Input</label>
                                <div class="hue-syncbox-grid-4col">
                                    ${['input1', 'input2', 'input3', 'input4'].map((input, i) => `
                                        <button class="hue-syncbox-tile ${input === currentInput ? 'selected' : ''}"
                                                onclick="Hue.setSyncBoxInput(${selectedSyncBox}, '${input}')">
                                            <span class="tile-icon">📺</span>
                                            <span class="tile-name">${escape(getHdmiName(input, i + 1))}</span>
                                            <span class="tile-info">HDMI ${i + 1}</span>
                                        </button>
                                    `).join('')}
                                </div>
                            </div>

                            <div class="hue-syncbox-grid-section">
                                <label>Sync Mode</label>
                                <div class="hue-syncbox-grid-4col">
                                    ${['video', 'music', 'game'].map(mode => `
                                        <button class="hue-syncbox-tile ${mode === currentMode ? 'selected' : ''}"
                                                onclick="Hue.setSyncBoxMode(${selectedSyncBox}, '${mode}')">
                                            <span class="tile-icon">${mode === 'video' ? '🎬' : mode === 'music' ? '🎵' : '🎮'}</span>
                                            <span class="tile-name">${mode.charAt(0).toUpperCase() + mode.slice(1)}</span>
                                        </button>
                                    `).join('')}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    // ===== Sync Mode Modal =====
    function openSyncModeModal() {
        const status = syncBoxStatuses[selectedSyncBox] || {};
        const exec = status.execution || {};
        const currentMode = exec.mode || 'video';

        const modal = document.getElementById('syncModeModal');
        const content = modal.querySelector('.syncbox-modal-options');
        content.innerHTML = ['video', 'music', 'game'].map(mode => `
            <button class="syncbox-modal-option ${mode === currentMode ? 'active' : ''}"
                    onclick="Hue.setSyncBoxMode(${selectedSyncBox}, '${mode}'); Hue.closeSyncModeModal();">
                <span class="option-icon">${mode === 'video' ? '🎬' : mode === 'music' ? '🎵' : '🎮'}</span>
                <span class="option-label">${mode.charAt(0).toUpperCase() + mode.slice(1)}</span>
            </button>
        `).join('');
        modal.classList.add('active');
    }

    function closeSyncModeModal() {
        document.getElementById('syncModeModal').classList.remove('active');
    }

    // ===== HDMI Input Modal =====
    function openHdmiInputModal() {
        const status = syncBoxStatuses[selectedSyncBox] || {};
        const exec = status.execution || {};
        const hdmi = status.hdmi || {};
        const currentInput = exec.hdmiSource || 'input1';

        const getHdmiName = (inputKey, defaultNum) => {
            const input = hdmi[inputKey];
            return input?.name || `HDMI ${defaultNum}`;
        };

        const modal = document.getElementById('hdmiInputModal');
        const content = modal.querySelector('.syncbox-modal-options');
        content.innerHTML = ['input1', 'input2', 'input3', 'input4'].map((input, i) => `
            <button class="syncbox-modal-option ${input === currentInput ? 'active' : ''}"
                    onclick="Hue.setSyncBoxInput(${selectedSyncBox}, '${input}'); Hue.closeHdmiInputModal();">
                <span class="option-label">${escape(getHdmiName(input, i + 1))}</span>
            </button>
        `).join('');
        modal.classList.add('active');
    }

    function closeHdmiInputModal() {
        document.getElementById('hdmiInputModal').classList.remove('active');
    }

    // ===== Room Content Rendering =====
    function renderRoomContent(room) {
        const lightsOnCount = room.lights ? room.lights.filter(l => l.state && l.state.on).length : 0;
        const totalLights = room.lights ? room.lights.length : 0;
        const toggleText = room.isOn ? `Turn off ${escape(room.name)}` : `Turn on ${escape(room.name)}`;
        const hasSyncingLights = room.lights && room.lights.some(l => isLightSyncing(l.id));
        const allLightsSyncing = room.lights && room.lights.length > 0 && room.lights.every(l => isLightSyncing(l.id));

        return `
            <div class="hue-room-content" data-room-id="${room.id}">
                ${hasSyncingLights ? `
                <div class="hue-sync-warning-banner">
                    <div class="sync-warning-text">
                        <div class="sync-warning-title">Hue Sync is active</div>
                        <div class="sync-warning-subtitle">Stop syncing to control the lights</div>
                    </div>
                    <button class="sync-warning-stop-btn" onclick="Hue.stopSyncFromBanner()">Stop Sync</button>
                </div>
                ` : ''}
                <div class="hue-room-layout">
                    <div class="hue-room-left-column">
                        <div class="hue-room-main-toggle" onclick="Hue.toggleRoom('${room.id}')">
                            <div class="hue-room-power ${room.isOn ? 'on' : ''}">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                    <path d="M18.36 6.64a9 9 0 1 1-12.73 0"/>
                                    <line x1="12" y1="2" x2="12" y2="12"/>
                                </svg>
                            </div>
                            <div class="hue-room-power-label">${toggleText}</div>
                        </div>

                        <div class="hue-room-brightness-section ${allLightsSyncing ? 'disabled' : ''}">
                            <label>Brightness</label>
                            <div class="hue-brightness-control">
                                <input type="range" min="1" max="254" value="${getAverageRoomBrightness(room)}"
                                       ${allLightsSyncing ? 'disabled' : ''}
                                       oninput="this.nextElementSibling.textContent = Math.round(this.value / 254 * 100) + '%'"
                                       onchange="Hue.setRoomBrightness('${room.id}', this.value)">
                                <span class="hue-brightness-value">${Math.round(getAverageRoomBrightness(room) / 254 * 100)}%</span>
                            </div>
                        </div>
                    </div>

                    <div class="hue-room-right-column">
                        ${room.scenes && room.scenes.length > 0 ? `
                        <div class="hue-room-scenes-section">
                            <button class="hue-scenes-open-btn" onclick="Hue.openSceneModal('${room.id}', '${escape(room.name)}')">
                                <span class="scenes-btn-icon">🎬</span>
                                <span class="scenes-btn-text">Scenes</span>
                                <span class="scenes-btn-count">${room.scenes.length}</span>
                            </button>
                        </div>
                        ` : ''}

                        <div class="hue-room-lights-section">
                            <label>Lights</label>
                            <div class="hue-lights-grid">
                            ${room.lights && room.lights.length > 0 ? room.lights.map(light => {
                                const syncing = isLightSyncing(light.id);
                                return `
                                <div class="hue-light-row ${light.state && light.state.on ? 'light-on' : ''} ${syncing ? 'syncing' : ''}"
                                     data-light-id="${light.id}"
                                     ${syncing ? '' : `onclick="Hue.openLightBrightnessPopup('${light.id}', '${escape(light.name)}', ${light.state?.bri || 127}, ${light.state?.on || false})"`}>
                                    <div class="hue-light-icon">${syncing ? '🎬' : '💡'}</div>
                                    <div class="hue-light-info">
                                        <div class="hue-light-name">${escape(light.name)}</div>
                                        <div class="hue-light-state ${syncing ? 'sync-active' : ''}">${syncing ? 'Sync Active' : (light.state && light.state.on ? Math.round(light.state.bri / 254 * 100) + '%' : 'Off')}</div>
                                    </div>
                                    ${syncing ? '' : `
                                    <div class="hue-light-toggle" onclick="event.stopPropagation(); Hue.toggleLight('${light.id}')">
                                        <div class="toggle-switch ${light.state && light.state.on ? 'on' : ''}">
                                            <div class="toggle-slider"></div>
                                        </div>
                                    </div>
                                    `}
                                </div>
                            `}).join('') : '<div class="no-lights">No lights in this room</div>'}
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    // ===== Room Card Rendering (for list view) =====
    function renderRoom(room) {
        const lightsOnCount = room.lights ? room.lights.filter(l => l.state && l.state.on).length : 0;
        const totalLights = room.lights ? room.lights.length : 0;
        const statusText = lightsOnCount === 0 ? 'Off' : lightsOnCount === totalLights ? 'All on' : `${lightsOnCount} on`;
        const isEntertainment = room.type === 'Entertainment';

        return `
            <div class="hue-room-card ${room.isOn ? 'room-on' : ''} ${isEntertainment ? 'entertainment-area' : ''}" data-room-id="${room.id}">
                <div class="hue-room-header" onclick="${isEntertainment ? `Hue.activateEntertainment('${room.id}')` : `Hue.toggleRoom('${room.id}')`}">
                    <div class="hue-room-icon">${getRoomIcon(room.class || room.type)}</div>
                    <div class="hue-room-info">
                        <div class="hue-room-name">${escape(room.name)}</div>
                        <div class="hue-room-status">${isEntertainment ? 'Tap to activate' : statusText}</div>
                    </div>
                    ${!isEntertainment ? `
                    <div class="hue-room-toggle">
                        <div class="toggle-switch ${room.isOn ? 'on' : ''}">
                            <div class="toggle-slider"></div>
                        </div>
                    </div>
                    <button class="hue-room-expand" onclick="event.stopPropagation(); Hue.toggleRoomExpand('${room.id}')">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <polyline points="6 9 12 15 18 9"/>
                        </svg>
                    </button>
                    ` : `
                    <div class="entertainment-activate-icon">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <polygon points="5 3 19 12 5 21 5 3"/>
                        </svg>
                    </div>
                    `}
                </div>
                ${!isEntertainment ? `
                <div class="hue-room-details" id="hue-room-details-${room.id}">
                    <div class="hue-room-brightness">
                        <label>Brightness</label>
                        <input type="range" min="1" max="254" value="${getAverageRoomBrightness(room)}"
                               onchange="Hue.setRoomBrightness('${room.id}', this.value)"
                               onclick="event.stopPropagation()">
                    </div>
                    ${room.scenes && room.scenes.length > 0 ? `
                    <div class="hue-room-scenes">
                        <label>Scenes</label>
                        <div class="hue-scenes-grid">
                            ${room.scenes.slice(0, 8).map(scene => `
                                <button class="hue-scene-btn" onclick="event.stopPropagation(); Hue.activateScene('${scene.id}')">
                                    ${escape(scene.name)}
                                </button>
                            `).join('')}
                        </div>
                    </div>
                    ` : ''}
                    <div class="hue-room-lights">
                        <label>Individual Lights</label>
                        ${room.lights && room.lights.length > 0 ? room.lights.map(light => `
                            <div class="hue-light-row ${light.state && light.state.on ? 'light-on' : ''}"
                                 data-light-id="${light.id}"
                                 onclick="event.stopPropagation(); Hue.toggleLight('${light.id}')">
                                <div class="hue-light-icon">💡</div>
                                <div class="hue-light-info">
                                    <div class="hue-light-name">${escape(light.name)}</div>
                                    <div class="hue-light-state">${light.state && light.state.on ? 'On' : 'Off'}</div>
                                </div>
                                <div class="hue-light-toggle">
                                    <div class="toggle-switch ${light.state && light.state.on ? 'on' : ''}">
                                        <div class="toggle-slider"></div>
                                    </div>
                                </div>
                            </div>
                        `).join('') : '<div class="no-lights">No lights in this room</div>'}
                    </div>
                </div>
                ` : ''}
            </div>
        `;
    }

    // ===== Helper Functions =====
    function getRoomIcon(roomClass) {
        const icons = {
            'Living room': '🛋️',
            'Bedroom': '🛏️',
            'Office': '💻',
            'Kitchen': '🍳',
            'Bathroom': '🚿',
            'Hallway': '🚪',
            'Garage': '🚗',
            'Balcony': '🌅',
            'Other': '💡',
            'Room': '🏠',
            'Zone': '📍',
            'TV': '📺',
            'Entertainment': '🎬'
        };
        return icons[roomClass] || '💡';
    }

    function getAverageRoomBrightness(room) {
        if (!room.lights || room.lights.length === 0) return 127;
        const onLights = room.lights.filter(l => l.state && l.state.on);
        if (onLights.length === 0) return 127;
        const sum = onLights.reduce((acc, l) => acc + (l.state.bri || 127), 0);
        return Math.round(sum / onLights.length);
    }

    function toggleRoomExpand(roomId) {
        const roomCard = document.querySelector(`.hue-room-card[data-room-id="${roomId}"]`);
        if (roomCard) {
            roomCard.classList.toggle('expanded');
        }
    }

    // ===== Room Control =====
    async function toggleRoom(roomId) {
        const roomCard = document.querySelector(`.hue-room-card[data-room-id="${roomId}"]`);
        if (roomCard) roomCard.classList.add('loading');

        try {
            const resp = await fetch(`/api/hue/group/${roomId}/toggle`, { method: 'POST' });
            if (resp.ok) {
                hueRooms = await resp.json();
                updateSummary(hueRooms);
                renderContent();
            }
        } catch (err) {
            console.error('Failed to toggle room:', err);
        } finally {
            if (roomCard) roomCard.classList.remove('loading');
        }
    }

    async function setRoomBrightness(roomId, brightness) {
        try {
            const resp = await fetch(`/api/hue/group/${roomId}/brightness`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ brightness: parseInt(brightness) })
            });
            if (resp.ok) {
                hueRooms = await resp.json();
                updateSummary(hueRooms);
            }
        } catch (err) {
            console.error('Failed to set brightness:', err);
        }
    }

    // ===== Light Control =====
    async function toggleLight(lightId) {
        const lightRow = document.querySelector(`.hue-light-row[data-light-id="${lightId}"]`);
        if (lightRow) lightRow.classList.add('loading');

        try {
            const resp = await fetch(`/api/hue/light/${lightId}/toggle`, { method: 'POST' });
            if (resp.ok) {
                await loadRooms();
                renderContent();
            }
        } catch (err) {
            console.error('Failed to toggle light:', err);
        } finally {
            if (lightRow) lightRow.classList.remove('loading');
        }
    }

    // ===== Light Brightness Popup =====
    function openLightBrightnessPopup(lightId, lightName, currentBri, isOn) {
        currentBrightnessLightId = lightId;
        currentBrightnessLightIsOn = isOn;
        document.getElementById('lightBrightnessTitle').textContent = lightName;
        const slider = document.getElementById('lightBrightnessSlider');
        slider.value = currentBri || 127;
        updateLightBrightnessPreview(slider.value);
        const toggle = document.getElementById('lightBrightnessToggle');
        if (isOn) {
            toggle.classList.add('on');
        } else {
            toggle.classList.remove('on');
        }
        document.getElementById('lightBrightnessModal').classList.add('active');
    }

    function closeLightBrightnessPopup() {
        document.getElementById('lightBrightnessModal').classList.remove('active');
        currentBrightnessLightId = null;
        currentBrightnessLightIsOn = false;
    }

    async function toggleBrightnessPopupLight() {
        if (!currentBrightnessLightId) return;

        try {
            const resp = await fetch(`/api/hue/light/${currentBrightnessLightId}/toggle`, { method: 'POST' });
            if (resp.ok) {
                currentBrightnessLightIsOn = !currentBrightnessLightIsOn;
                const toggle = document.getElementById('lightBrightnessToggle');
                if (currentBrightnessLightIsOn) {
                    toggle.classList.add('on');
                } else {
                    toggle.classList.remove('on');
                }
                await loadRooms();
                renderContent();
            }
        } catch (err) {
            console.error('Failed to toggle light:', err);
        }
    }

    function updateLightBrightnessPreview(value) {
        const percent = Math.round(value / 254 * 100);
        document.getElementById('lightBrightnessPercent').textContent = percent + '%';
    }

    async function setLightBrightness(value) {
        if (!currentBrightnessLightId) return;

        try {
            const resp = await fetch(`/api/hue/light/${currentBrightnessLightId}/brightness`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ brightness: parseInt(value) })
            });
            if (resp.ok) {
                await loadRooms();
                renderContent();
            }
        } catch (err) {
            console.error('Failed to set brightness:', err);
        }
    }

    // ===== Scene Control =====
    async function activateScene(sceneId) {
        try {
            const resp = await fetch(`/api/hue/scene/${sceneId}/activate`, { method: 'POST' });
            if (resp.ok) {
                hueRooms = await resp.json();
                updateSummary(hueRooms);
                renderContent();
                closeSceneModal();
            }
        } catch (err) {
            console.error('Failed to activate scene:', err);
        }
    }

    function openSceneModal(roomId, roomName) {
        currentSceneRoomId = roomId;
        document.getElementById('sceneModalTitle').textContent = `${roomName} Scenes`;

        const room = hueRooms.find(r => r.id === roomId);
        if (room && room.scenes) {
            const scenesList = document.getElementById('sceneModalList');
            scenesList.innerHTML = room.scenes.map(scene => `
                <button class="scene-modal-btn ${scene.active ? 'active' : ''}" onclick="Hue.activateScene('${scene.id}')">
                    ${scene.active ? '<span class="scene-active-icon">✓</span>' : ''}
                    ${escape(scene.name)}
                </button>
            `).join('');
        }

        document.getElementById('sceneModal').classList.add('active');
    }

    function closeSceneModal() {
        document.getElementById('sceneModal').classList.remove('active');
        currentSceneRoomId = null;
    }

    // ===== SSE Connection =====
    function connectSSE() {
        if (eventSource) {
            eventSource.close();
        }

        console.log('Connecting to Hue SSE...');
        eventSource = new EventSource('/api/hue/events');

        eventSource.onopen = function() {
            console.log('Hue SSE connected');
            sseConnected = true;
            debouncedReload(); // Initial load on connect
        };

        eventSource.onmessage = function(e) {
            try {
                const event = JSON.parse(e.data);
                console.log('Hue SSE event:', event.type);
                // Debounced reload on any update
                if (event.type !== 'connected') {
                    debouncedReload();
                }
            } catch (err) {
                console.log('SSE message:', e.data);
            }
        };

        eventSource.onerror = function(err) {
            console.log('Hue SSE error, will reconnect');
            sseConnected = false;
            eventSource.close();
            // Reconnect after 5 seconds
            setTimeout(connectSSE, 5000);
        };
    }

    function debouncedReload() {
        const now = Date.now();

        // Cancel any pending reload
        if (pendingReload) {
            clearTimeout(pendingReload);
            pendingReload = null;
        }

        // If we recently loaded, schedule a delayed reload
        const timeSinceLastReload = now - lastReloadTime;
        if (timeSinceLastReload < SSE_DEBOUNCE_MS) {
            pendingReload = setTimeout(() => {
                console.log('Debounced Hue reload');
                loadRooms();
            }, SSE_DEBOUNCE_MS - timeSinceLastReload);
        } else {
            // Enough time has passed, reload immediately
            loadRooms();
        }
    }

    // ===== Initialization =====
    function init() {
        // Load initial data
        loadRooms();
        loadSyncBoxes();

        // Connect to SSE for real-time updates
        connectSSE();

        // Fallback: refresh every 10 seconds if SSE is not connected
        setInterval(() => {
            if (!sseConnected) {
                console.log('SSE not connected, polling fallback');
                loadRooms();
            }
        }, 10000);

        // Refresh sync box status when entertainment tab is active
        setInterval(() => {
            const modal = document.getElementById('hueModal');
            if (modal && modal.classList.contains('active') && activeHueTab === 'entertainment') {
                for (const box of syncBoxes) {
                    loadSyncBoxStatus(box.index);
                }
            }
        }, 3000);
    }

    // Public API
    return {
        init: init,
        loadRooms: loadRooms,
        openModal: openModal,
        closeModal: closeModal,
        switchTab: switchTab,
        selectEntertainmentArea: selectEntertainmentArea,
        toggleRoom: toggleRoom,
        setRoomBrightness: setRoomBrightness,
        toggleRoomExpand: toggleRoomExpand,
        toggleLight: toggleLight,
        openLightBrightnessPopup: openLightBrightnessPopup,
        closeLightBrightnessPopup: closeLightBrightnessPopup,
        toggleBrightnessPopupLight: toggleBrightnessPopupLight,
        updateLightBrightnessPreview: updateLightBrightnessPreview,
        setLightBrightness: setLightBrightness,
        activateScene: activateScene,
        openSceneModal: openSceneModal,
        closeSceneModal: closeSceneModal,
        selectSyncBox: selectSyncBox,
        toggleSyncBox: toggleSyncBox,
        setSyncBoxArea: setSyncBoxArea,
        setSyncBoxMode: setSyncBoxMode,
        setSyncBoxInput: setSyncBoxInput,
        openSyncModeModal: openSyncModeModal,
        closeSyncModeModal: closeSyncModeModal,
        openHdmiInputModal: openHdmiInputModal,
        closeHdmiInputModal: closeHdmiInputModal,
        stopSyncFromBanner: stopSyncFromBanner
    };
})();

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', Hue.init);

// Global function aliases for onclick handlers
function openHueModal() { Hue.openModal(); }
function closeHueModal() { Hue.closeModal(); }
function switchHueTab(tabId) { Hue.switchTab(tabId); }
function selectEntertainmentArea(areaId) { Hue.selectEntertainmentArea(areaId); }
function toggleHueRoom(roomId) { Hue.toggleRoom(roomId); }
function setHueRoomBrightness(roomId, brightness) { Hue.setRoomBrightness(roomId, brightness); }
function toggleHueRoomExpand(roomId) { Hue.toggleRoomExpand(roomId); }
function toggleHueLight(lightId) { Hue.toggleLight(lightId); }
function openLightBrightnessPopup(lightId, lightName, currentBri, isOn) { Hue.openLightBrightnessPopup(lightId, lightName, currentBri, isOn); }
function closeLightBrightnessPopup() { Hue.closeLightBrightnessPopup(); }
function toggleBrightnessPopupLight() { Hue.toggleBrightnessPopupLight(); }
function updateLightBrightnessPreview(value) { Hue.updateLightBrightnessPreview(value); }
function setLightBrightness(value) { Hue.setLightBrightness(value); }
function activateHueScene(sceneId) { Hue.activateScene(sceneId); }
function openSceneModal(roomId, roomName) { Hue.openSceneModal(roomId, roomName); }
function closeSceneModal() { Hue.closeSceneModal(); }
function selectSyncBox(index) { Hue.selectSyncBox(index); }
function toggleSyncBox(index, active) { Hue.toggleSyncBox(index, active); }
function setSyncBoxArea(index, groupId) { Hue.setSyncBoxArea(index, groupId); }
function setSyncBoxMode(index, mode) { Hue.setSyncBoxMode(index, mode); }
function setSyncBoxInput(index, hdmiSource) { Hue.setSyncBoxInput(index, hdmiSource); }
function openSyncModeModal() { Hue.openSyncModeModal(); }
function closeSyncModeModal() { Hue.closeSyncModeModal(); }
function openHdmiInputModal() { Hue.openHdmiInputModal(); }
function closeHdmiInputModal() { Hue.closeHdmiInputModal(); }
function stopSyncFromBanner() { Hue.stopSyncFromBanner(); }
