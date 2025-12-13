// Entity/Groups Module
const Entities = (function() {
    // State variables
    let groupsData = {};
    let camerasData = [];

    // Initialize data from embedded JSON
    function init() {
        // Parse groups data
        try {
            const dataEl = document.getElementById('groupsData');
            if (dataEl) {
                const jsonStr = '[' + dataEl.textContent.trim() + ']';
                const groups = JSON.parse(jsonStr);
                groups.forEach(g => {
                    groupsData[g.name] = g;
                });
            }
        } catch (e) {
            console.error('Failed to parse groups data:', e);
        }

        // Parse cameras data
        try {
            const camerasEl = document.getElementById('camerasData');
            if (camerasEl) {
                camerasData = JSON.parse(camerasEl.textContent.trim());
            }
        } catch (e) {
            console.error('Failed to parse cameras data:', e);
        }

        // Update summaries on load
        updateGroupSummaries();

        // Start refresh interval
        setInterval(refreshEntityStates, 30000);
    }

    // Update group summaries on load
    function updateGroupSummaries() {
        Object.keys(groupsData).forEach(groupName => {
            const group = groupsData[groupName];
            const summaryEl = document.getElementById('summary-' + groupName);
            if (!summaryEl) return;

            const onCount = group.cards.filter(c => c.isOn).length;
            const total = group.cards.length;

            if (groupName === 'Lights') {
                if (onCount === 0) {
                    summaryEl.textContent = 'All off';
                } else if (onCount === total) {
                    summaryEl.textContent = 'All on';
                } else {
                    summaryEl.textContent = onCount + ' on';
                }
            } else if (groupName === 'Security') {
                const lockCard = group.cards.find(c => c.type === 'lock');
                const lockStatus = lockCard ? (lockCard.state === 'locked' ? 'Locked' : 'Unlocked') : '';
                const camCount = camerasData.length;
                if (lockStatus && camCount > 0) {
                    summaryEl.textContent = `${lockStatus} · ${camCount} camera${camCount !== 1 ? 's' : ''}`;
                } else if (lockStatus) {
                    summaryEl.textContent = lockStatus;
                } else if (camCount > 0) {
                    summaryEl.textContent = `${camCount} camera${camCount !== 1 ? 's' : ''}`;
                }
            } else if (groupName === 'Climate') {
                // Find thermostat or show temp
                const climate = group.cards.find(c => c.type === 'climate');
                if (climate) {
                    summaryEl.textContent = climate.state;
                } else {
                    const temp = group.cards.find(c => c.unit === '°F' || c.unit === '°C');
                    if (temp) {
                        summaryEl.textContent = temp.state + temp.unit;
                    }
                }
            } else {
                summaryEl.textContent = total + ' device' + (total !== 1 ? 's' : '');
            }
        });
    }

    // Group Modal
    function openGroupModal(groupName) {
        const group = groupsData[groupName];
        if (!group) return;

        document.getElementById('groupModalIcon').textContent = group.icon;
        document.getElementById('groupModalTitle').textContent = group.name;

        const content = document.getElementById('groupModalContent');
        content.innerHTML = renderGroupContent(group);

        document.getElementById('groupModal').classList.add('active');
    }

    function closeGroupModal() {
        document.getElementById('groupModal').classList.remove('active');
    }

    // Cameras Modal
    function openCamerasModal() {
        const content = document.getElementById('camerasModalContent');
        content.innerHTML = renderCamerasContent();
        document.getElementById('camerasModal').classList.add('active');
    }

    function closeCamerasModal() {
        document.getElementById('camerasModal').classList.remove('active');
    }

    function renderCamerasContent() {
        if (camerasData.length === 0) {
            return '<div class="no-cameras">No cameras configured</div>';
        }
        return camerasData.map(cam => `
            <div class="camera-row" onclick="openCameraView('${cam.name}', '${cam.label}')">
                <div class="camera-icon"><img src="/icon/video" class="camera-icon-img" alt=""></div>
                <div class="camera-info">
                    <div class="camera-name">${cam.label}</div>
                    <div class="camera-status">Tap to view</div>
                </div>
                <div class="camera-arrow">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="9 18 15 12 9 6"/>
                    </svg>
                </div>
            </div>
        `).join('');
    }

    function openCameraView(cameraName, cameraLabel) {
        const modal = document.getElementById('cameraViewModal');
        const title = document.getElementById('cameraViewTitle');
        const stream = document.getElementById('cameraViewStream');

        title.textContent = cameraLabel;

        // Load the camera stream
        stream.src = `/api/camera/${cameraName}/stream`;
        stream.onerror = () => {
            stream.src = `/api/camera/${cameraName}/snapshot`;
        };

        modal.classList.add('active');
    }

    function closeCameraViewModal() {
        const modal = document.getElementById('cameraViewModal');
        const stream = document.getElementById('cameraViewStream');

        modal.classList.remove('active');
        stream.src = ''; // Stop the stream
    }

    function renderGroupContent(group) {
        // Security group gets special 2x2 grid layout with lock and cameras
        if (group.name === 'Security') {
            return renderSecurityGrid(group);
        }

        return group.cards.map(card => {
            // Climate entities get special rendering
            if (card.type === 'climate') {
                return renderClimateCard(card);
            }

            // Light groups get expandable rendering with member controls
            if (card.type === 'light' && card.isLightGroup && card.members && card.members.length > 0) {
                return renderLightGroupCard(card);
            }

            const isToggleable = ['light', 'switch', 'fan', 'lock'].includes(card.type);
            const toggleAttr = isToggleable ? `onclick="toggleEntity('${card.entityId}')"` : '';
            const cursorClass = isToggleable ? 'entity-toggleable' : '';
            const onClass = card.isOn ? 'entity-on' : '';

            return `
                <div class="entity-row ${cursorClass} ${onClass}" data-entity="${card.entityId}" ${toggleAttr}>
                    <div class="entity-icon">${card.icon}</div>
                    <div class="entity-info">
                        <div class="entity-name">${escapeHtml(card.name)}</div>
                        <div class="entity-state">${card.state}${card.unit ? ' ' + card.unit : ''}</div>
                    </div>
                    ${isToggleable ? `
                    <div class="entity-toggle">
                        <div class="toggle-switch ${card.isOn ? 'on' : ''}">
                            <div class="toggle-slider"></div>
                        </div>
                    </div>
                    ` : ''}
                </div>
            `;
        }).join('');
    }

    function renderSecurityGrid(group) {
        // Find lock card
        const lockCard = group.cards.find(c => c.type === 'lock');

        let html = '<div class="security-grid">';

        // Lock card first
        if (lockCard) {
            const isLocked = lockCard.state === 'locked';
            html += `
                <div class="security-grid-item lock-item ${isLocked ? 'locked' : 'unlocked'}"
                     data-entity="${lockCard.entityId}"
                     onclick="toggleEntity('${lockCard.entityId}')">
                    <div class="security-item-icon">${lockCard.icon}</div>
                    <div class="security-item-name">${escapeHtml(lockCard.name)}</div>
                    <div class="security-item-state">${lockCard.state}</div>
                </div>
            `;
        }

        // Camera cards
        camerasData.forEach(cam => {
            html += `
                <div class="security-grid-item camera-item"
                     onclick="openCameraView('${cam.name}', '${cam.label}')">
                    <div class="security-item-icon"><img src="/icon/video" class="security-icon-img" alt=""></div>
                    <div class="security-item-name">${escapeHtml(cam.label)}</div>
                    <div class="security-item-state">Tap to view</div>
                </div>
            `;
        });

        html += '</div>';
        return html;
    }

    function renderLightGroupCard(card) {
        const onCount = card.members.filter(m => m.isOn).length;
        const totalCount = card.members.length;
        const groupState = onCount === 0 ? 'All off' : onCount === totalCount ? 'All on' : `${onCount} on`;

        return `
            <div class="light-group-card" data-entity="${card.entityId}">
                <div class="light-group-header entity-row entity-toggleable ${card.isOn ? 'entity-on' : ''}"
                     onclick="toggleEntity('${card.entityId}')">
                    <div class="entity-icon">${card.icon}</div>
                    <div class="entity-info">
                        <div class="entity-name">${escapeHtml(card.name)}</div>
                        <div class="entity-state">${groupState}</div>
                    </div>
                    <div class="entity-toggle">
                        <div class="toggle-switch ${card.isOn ? 'on' : ''}">
                            <div class="toggle-slider"></div>
                        </div>
                    </div>
                    <button class="light-group-expand" onclick="event.stopPropagation(); toggleLightGroupExpand('${card.entityId}')">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <polyline points="6 9 12 15 18 9"/>
                        </svg>
                    </button>
                </div>
                <div class="light-group-members" id="members-${card.entityId.replace('.', '-')}">
                    ${card.members.map(member => `
                        <div class="entity-row entity-toggleable light-member ${member.isOn ? 'entity-on' : ''}"
                             data-entity="${member.entityId}"
                             onclick="toggleEntity('${member.entityId}')">
                            <div class="entity-icon">${member.icon}</div>
                            <div class="entity-info">
                                <div class="entity-name">${escapeHtml(member.name)}</div>
                                <div class="entity-state">${member.state}</div>
                            </div>
                            <div class="entity-toggle">
                                <div class="toggle-switch ${member.isOn ? 'on' : ''}">
                                    <div class="toggle-slider"></div>
                                </div>
                            </div>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;
    }

    function toggleLightGroupExpand(entityId) {
        const membersId = 'members-' + entityId.replace('.', '-');
        const members = document.getElementById(membersId);
        const card = members.closest('.light-group-card');

        card.classList.toggle('expanded');
    }

    function renderClimateCard(card) {
        const attrs = card.attributes || {};
        const currentTemp = attrs.current_temperature || '--';
        const hvacModes = attrs.hvac_modes || ['off', 'heat', 'cool', 'auto'];
        const currentMode = card.state || 'off';
        const hvacAction = attrs.hvac_action || '';
        const minTemp = attrs.min_temp || 45;
        const maxTemp = attrs.max_temp || 95;
        const fanModes = attrs.fan_modes || [];
        const currentFanMode = attrs.fan_mode || '';

        // Handle dual setpoints for heat_cool/auto mode
        const isDualMode = currentMode === 'heat_cool' || currentMode === 'auto';
        const targetTempLow = attrs.target_temp_low || attrs.temperature || 70;
        const targetTempHigh = attrs.target_temp_high || attrs.temperature || 75;
        const targetTemp = attrs.temperature || 72;

        // Determine action display
        let actionText = currentMode === 'heat_cool' ? 'Auto' : currentMode;
        if (hvacAction && hvacAction !== 'idle' && hvacAction !== 'off') {
            actionText = hvacAction.charAt(0).toUpperCase() + hvacAction.slice(1);
        }

        // Build fan mode control HTML if fan modes are available
        let fanModeHtml = '';
        if (fanModes.length > 0) {
            fanModeHtml = `
                <div class="climate-fan-control">
                    <span class="climate-fan-label">Fan:</span>
                    <select class="climate-fan-select" onchange="setClimateFanMode('${card.entityId}', this.value)" onclick="event.stopPropagation()">
                        ${fanModes.map(mode => `
                            <option value="${mode}" ${mode === currentFanMode ? 'selected' : ''}>
                                ${mode.charAt(0).toUpperCase() + mode.slice(1)}
                            </option>
                        `).join('')}
                    </select>
                </div>
            `;
        }

        return `
            <div class="entity-row climate-card" data-entity="${card.entityId}"
                 data-min="${minTemp}" data-max="${maxTemp}"
                 data-target-low="${targetTempLow}" data-target-high="${targetTempHigh}"
                 data-target="${targetTemp}" data-dual="${isDualMode}">
                <div class="thermostat-main">
                    <div class="thermostat-display">
                        <div class="thermostat-action ${hvacAction || currentMode}">${actionText}</div>
                        <div class="thermostat-current">
                            <span class="current-icon">🌡</span>
                            <span class="current-temp">${typeof currentTemp === 'number' ? Math.round(currentTemp) : currentTemp}°F</span>
                        </div>
                    </div>

                    ${isDualMode ? `
                    <div class="thermostat-controls-right">
                        <div class="thermostat-setpoint-vertical heat">
                            <button class="thermostat-btn-sm plus" onclick="event.stopPropagation(); adjustClimateSetpoint('${card.entityId}', 'low', 1, ${minTemp}, ${maxTemp})">
                                <svg viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" fill="currentColor"/></svg>
                            </button>
                            <div class="setpoint-value">
                                <span class="setpoint-temp temp-low">${Math.round(targetTempLow)}°</span>
                                <span class="setpoint-label">Heat</span>
                            </div>
                            <button class="thermostat-btn-sm minus" onclick="event.stopPropagation(); adjustClimateSetpoint('${card.entityId}', 'low', -1, ${minTemp}, ${maxTemp})">
                                <svg viewBox="0 0 24 24"><path d="M19 13H5v-2h14v2z" fill="currentColor"/></svg>
                            </button>
                        </div>
                        <div class="thermostat-setpoint-vertical cool">
                            <button class="thermostat-btn-sm plus" onclick="event.stopPropagation(); adjustClimateSetpoint('${card.entityId}', 'high', 1, ${minTemp}, ${maxTemp})">
                                <svg viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" fill="currentColor"/></svg>
                            </button>
                            <div class="setpoint-value">
                                <span class="setpoint-temp temp-high">${Math.round(targetTempHigh)}°</span>
                                <span class="setpoint-label">Cool</span>
                            </div>
                            <button class="thermostat-btn-sm minus" onclick="event.stopPropagation(); adjustClimateSetpoint('${card.entityId}', 'high', -1, ${minTemp}, ${maxTemp})">
                                <svg viewBox="0 0 24 24"><path d="M19 13H5v-2h14v2z" fill="currentColor"/></svg>
                            </button>
                        </div>
                    </div>
                    ` : `
                    <div class="thermostat-controls-right single">
                        <button class="thermostat-btn plus" onclick="event.stopPropagation(); adjustClimateTemp('${card.entityId}', 1, ${minTemp}, ${maxTemp})">
                            <svg viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" fill="currentColor"/></svg>
                        </button>
                        <span class="temp-target">${Math.round(targetTemp)}<sup>°</sup></span>
                        <button class="thermostat-btn minus" onclick="event.stopPropagation(); adjustClimateTemp('${card.entityId}', -1, ${minTemp}, ${maxTemp})">
                            <svg viewBox="0 0 24 24"><path d="M19 13H5v-2h14v2z" fill="currentColor"/></svg>
                        </button>
                    </div>
                    `}
                </div>

                <div class="thermostat-footer">
                    <div class="climate-mode-row">
                        <select class="climate-mode-select" onchange="setClimateMode('${card.entityId}', this.value)" onclick="event.stopPropagation()">
                            ${hvacModes.map(mode => `
                                <option value="${mode}" ${mode === currentMode ? 'selected' : ''}>
                                    ${mode === 'heat_cool' ? 'Auto' : mode.charAt(0).toUpperCase() + mode.slice(1)}
                                </option>
                            `).join('')}
                        </select>
                        ${fanModeHtml}
                    </div>
                </div>
            </div>
        `;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    async function toggleEntity(entityID) {
        const entityRow = document.querySelector(`.entity-row[data-entity="${entityID}"]`);

        if (entityRow) {
            entityRow.classList.add('entity-loading');
        }

        try {
            const resp = await fetch(`/api/toggle/${entityID}`, { method: 'POST' });
            if (resp.ok) {
                const data = await resp.json();

                // Update the entity row in modal
                if (entityRow) {
                    entityRow.classList.toggle('entity-on', data.IsOn);
                    entityRow.querySelector('.entity-state').textContent = data.State;
                    const toggle = entityRow.querySelector('.toggle-switch');
                    if (toggle) {
                        toggle.classList.toggle('on', data.IsOn);
                    }
                }

                // Update the local data
                Object.keys(groupsData).forEach(groupName => {
                    const card = groupsData[groupName].cards.find(c => c.entityId === entityID);
                    if (card) {
                        card.isOn = data.IsOn;
                        card.state = data.State;
                    }
                });

                // Update group summaries
                updateGroupSummaries();
            }
        } catch (err) {
            console.error('Toggle failed:', err);
        } finally {
            if (entityRow) {
                entityRow.classList.remove('entity-loading');
            }
        }
    }

    // Climate control functions - single temperature mode
    async function adjustClimateTemp(entityID, delta, minTemp, maxTemp) {
        const climateCard = document.querySelector(`.climate-card[data-entity="${entityID}"]`);
        if (!climateCard) return;

        let targetTemp = parseFloat(climateCard.dataset.target);
        targetTemp = Math.max(minTemp, Math.min(maxTemp, targetTemp + delta));

        // Optimistically update UI
        const tempEl = climateCard.querySelector('.temp-target');
        if (tempEl) tempEl.innerHTML = Math.round(targetTemp) + '<sup>°</sup>';
        climateCard.dataset.target = targetTemp;

        try {
            const resp = await fetch(`/api/climate/${entityID}/temperature`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ temperature: targetTemp })
            });

            if (resp.ok) {
                const data = await resp.json();
                updateClimateCardData(entityID, data);
                // Re-render to update the arc
                reRenderClimateCard(entityID);
            }
        } catch (err) {
            console.error('Failed to set temperature:', err);
        }
    }

    // Climate control - adjust individual setpoint (heat low or cool high)
    async function adjustClimateSetpoint(entityID, which, delta, minTemp, maxTemp) {
        const climateCard = document.querySelector(`.climate-card[data-entity="${entityID}"]`);
        if (!climateCard) return;

        let targetLow = parseFloat(climateCard.dataset.targetLow);
        let targetHigh = parseFloat(climateCard.dataset.targetHigh);

        if (which === 'low') {
            targetLow = Math.max(minTemp, Math.min(maxTemp, targetLow + delta));
            // Ensure low doesn't exceed high - 2
            if (targetLow > targetHigh - 2) {
                targetLow = targetHigh - 2;
            }
        } else {
            targetHigh = Math.max(minTemp, Math.min(maxTemp, targetHigh + delta));
            // Ensure high doesn't go below low + 2
            if (targetHigh < targetLow + 2) {
                targetHigh = targetLow + 2;
            }
        }

        // Optimistically update UI - update all temp displays
        climateCard.querySelectorAll('.temp-low').forEach(el => {
            el.innerHTML = Math.round(targetLow) + (el.querySelector('sup') ? '<sup>°</sup>' : '°');
        });
        climateCard.querySelectorAll('.temp-high').forEach(el => {
            el.innerHTML = Math.round(targetHigh) + (el.querySelector('sup') ? '<sup>°</sup>' : '°');
        });
        climateCard.dataset.targetLow = targetLow;
        climateCard.dataset.targetHigh = targetHigh;

        try {
            const resp = await fetch(`/api/climate/${entityID}/temperature`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ target_temp_low: targetLow, target_temp_high: targetHigh })
            });

            if (resp.ok) {
                const data = await resp.json();
                updateClimateCardData(entityID, data);
                // Re-render to update the arc
                reRenderClimateCard(entityID);
            }
        } catch (err) {
            console.error('Failed to set temperature:', err);
        }
    }

    // Helper to re-render climate card after updates
    function reRenderClimateCard(entityID) {
        const climateCard = document.querySelector(`.climate-card[data-entity="${entityID}"]`);
        if (!climateCard) return;

        const group = Object.values(groupsData).find(g =>
            g.cards.some(c => c.entityId === entityID)
        );
        if (group) {
            const card = group.cards.find(c => c.entityId === entityID);
            if (card) {
                climateCard.outerHTML = renderClimateCard(card);
            }
        }
    }

    async function setClimateMode(entityID, mode) {
        const climateCard = document.querySelector(`.climate-card[data-entity="${entityID}"]`);
        if (climateCard) {
            climateCard.classList.add('entity-loading');
        }

        try {
            const resp = await fetch(`/api/climate/${entityID}/mode`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ mode: mode })
            });

            if (resp.ok) {
                const data = await resp.json();
                // Update local data and re-render
                updateClimateCardData(entityID, data);

                // Re-render the climate card
                const group = Object.values(groupsData).find(g =>
                    g.cards.some(c => c.entityId === entityID)
                );
                if (group) {
                    const card = group.cards.find(c => c.entityId === entityID);
                    if (card && climateCard) {
                        climateCard.outerHTML = renderClimateCard(card);
                    }
                }
            }
        } catch (err) {
            console.error('Failed to set HVAC mode:', err);
        } finally {
            if (climateCard) {
                climateCard.classList.remove('entity-loading');
            }
        }
    }

    async function setClimateFanMode(entityID, fanMode) {
        // Fan mode changes don't need a full re-render - just update local data
        try {
            const resp = await fetch(`/api/climate/${entityID}/fan`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ fan_mode: fanMode })
            });

            if (resp.ok) {
                const data = await resp.json();
                // Just update local data, no re-render needed for fan mode
                updateClimateCardData(entityID, data);
            }
        } catch (err) {
            console.error('Failed to set fan mode:', err);
        }
    }

    function updateClimateCardData(entityID, data) {
        // Update local groupsData
        Object.keys(groupsData).forEach(groupName => {
            const card = groupsData[groupName].cards.find(c => c.entityId === entityID);
            if (card) {
                card.state = data.State || data.state;
                if (data.Attributes) {
                    card.attributes = data.Attributes;
                } else if (data.attributes) {
                    card.attributes = data.attributes;
                }
            }
        });
        updateGroupSummaries();
    }

    // Notifications Functions (placeholder)
    function openNotifications() {
        // TODO: Implement notifications panel
        console.log('Notifications clicked - placeholder');
    }

    function updateNotificationBadge(count) {
        const badge = document.getElementById('notificationBadge');
        if (!badge) return;

        if (count > 0) {
            badge.style.display = 'flex';
            badge.textContent = count > 99 ? '99+' : count;
        } else {
            badge.style.display = 'none';
        }
    }

    // Refresh entity states every 30 seconds via AJAX (no page reload)
    async function refreshEntityStates() {
        try {
            const resp = await fetch('/api/entities');
            if (!resp.ok) return;

            const groups = await resp.json();

            // Update groupsData with new states (normalize API response to match local format)
            groups.forEach(apiGroup => {
                // API returns capitalized Go struct names, local uses lowercase
                const groupName = apiGroup.Name || apiGroup.name;
                if (groupsData[groupName]) {
                    // Normalize to lowercase property names
                    groupsData[groupName] = {
                        name: groupName,
                        icon: apiGroup.Icon || apiGroup.icon,
                        cards: (apiGroup.Cards || apiGroup.cards || []).map(card => ({
                            entityId: card.EntityID || card.entityId,
                            name: card.Name || card.name,
                            state: card.State || card.state,
                            icon: card.Icon || card.icon,
                            type: card.Type || card.type,
                            unit: card.Unit || card.unit,
                            isOn: card.IsOn !== undefined ? card.IsOn : card.isOn,
                            isLightGroup: card.IsLightGroup !== undefined ? card.IsLightGroup : card.isLightGroup,
                            members: (card.Members || card.members || []).map(m => ({
                                entityId: m.EntityID || m.entityId,
                                name: m.Name || m.name,
                                state: m.State || m.state,
                                icon: m.Icon || m.icon,
                                type: m.Type || m.type,
                                isOn: m.IsOn !== undefined ? m.IsOn : m.isOn
                            })),
                            attributes: card.Attributes || card.attributes || {}
                        }))
                    };
                }
            });

            // Update group card summaries
            updateGroupSummaries();

            // If a group modal is open, refresh its content
            const activeModal = document.querySelector('#groupModal.active');
            if (activeModal) {
                const titleEl = document.getElementById('groupModalTitle');
                if (titleEl) {
                    const groupName = titleEl.textContent;
                    const group = groupsData[groupName];
                    if (group) {
                        document.getElementById('groupModalContent').innerHTML = renderGroupContent(group);
                    }
                }
            }
        } catch (err) {
            console.error('Failed to refresh entity states:', err);
        }
    }

    // Public API
    return {
        init,
        openGroupModal,
        closeGroupModal,
        openCamerasModal,
        closeCamerasModal,
        openCameraView,
        closeCameraViewModal,
        toggleEntity,
        toggleLightGroupExpand,
        adjustClimateTemp,
        adjustClimateSetpoint,
        setClimateMode,
        setClimateFanMode,
        openNotifications,
        updateNotificationBadge,
        refreshEntityStates,
        updateGroupSummaries
    };
})();

// Initialize on DOMContentLoaded
document.addEventListener('DOMContentLoaded', function() {
    Entities.init();
});

// Global function aliases for onclick handlers in HTML
function openGroupModal(groupName) { Entities.openGroupModal(groupName); }
function closeGroupModal() { Entities.closeGroupModal(); }
function openCamerasModal() { Entities.openCamerasModal(); }
function closeCamerasModal() { Entities.closeCamerasModal(); }
function openCameraView(cameraName, cameraLabel) { Entities.openCameraView(cameraName, cameraLabel); }
function closeCameraViewModal() { Entities.closeCameraViewModal(); }
function toggleEntity(entityID) { Entities.toggleEntity(entityID); }
function toggleLightGroupExpand(entityId) { Entities.toggleLightGroupExpand(entityId); }
function adjustClimateTemp(entityID, delta, minTemp, maxTemp) { Entities.adjustClimateTemp(entityID, delta, minTemp, maxTemp); }
function adjustClimateSetpoint(entityID, which, delta, minTemp, maxTemp) { Entities.adjustClimateSetpoint(entityID, which, delta, minTemp, maxTemp); }
function setClimateMode(entityID, mode) { Entities.setClimateMode(entityID, mode); }
function setClimateFanMode(entityID, fanMode) { Entities.setClimateFanMode(entityID, fanMode); }
function openNotifications() { Entities.openNotifications(); }
function updateNotificationBadge(count) { Entities.updateNotificationBadge(count); }
