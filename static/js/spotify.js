/**
 * Spotify Module
 * Handles Spotify playback control, browsing, and UI rendering.
 */
const Spotify = (function() {
    // State variables
    let spotifyStatus = null;
    let spotifyPlayback = null;
    let spotifyDevices = [];
    let spotifyPlaylists = [];
    let activeSpotifyTab = 'now-playing';
    let activeSpotifyBrowseTab = 'home';
    let spotifyRecentItems = [];
    let spotifyTopArtists = [];
    let spotifyTopTracks = [];
    let spotifyDetailView = null;
    let spotifyDetailHistory = [];
    let spotifySectionData = {};

    // Library tab state
    let libraryFilter = 'all';
    let librarySort = 'recents';
    let librarySearch = '';
    let libraryAlbums = [];
    let libraryArtists = [];
    let libraryShows = [];
    let likedSongsTotal = 0;
    let libraryLoaded = false;

    // Volume and playback state
    let volumeDebounceTimeout = null;
    let isAdjustingVolume = false;
    let preMuteVolume = 50;
    let isMuted = false;

    // Pending play state
    let pendingPlayUri = null;
    let pendingPlayPosition = 0;
    let pendingShufflePlay = false;

    // Album and artist saved/following states
    let albumSavedStates = {};
    let artistFollowingStates = {};

    // Progress update interval
    let miniPlayerProgressInterval = null;

    // ===== Status and Playback Functions =====

    async function checkSpotifyStatus() {
        try {
            const resp = await fetch('/api/spotify/status');
            if (resp.ok) {
                spotifyStatus = await resp.json();
                const card = document.getElementById('spotifyCard');
                if (spotifyStatus.configured) {
                    card.style.display = '';
                    if (spotifyStatus.authenticated) {
                        loadSpotifyPlayback();
                    } else {
                        document.getElementById('summary-Spotify').textContent = 'Not connected';
                    }
                }
            }
        } catch (err) {
            console.log('Spotify not available:', err);
        }
    }

    async function loadSpotifyPlayback() {
        if (!spotifyStatus || !spotifyStatus.authenticated) return;

        try {
            const resp = await fetch('/api/spotify/playback');
            if (resp.ok) {
                const newPlayback = await resp.json();

                // Preserve local progress if same track is playing to prevent jumping
                const sameTrack = spotifyPlayback?.item?.id === newPlayback?.item?.id;
                const wasPlaying = spotifyPlayback?.is_playing;
                const nowPlaying = newPlayback?.is_playing;

                if (sameTrack && wasPlaying && nowPlaying && spotifyPlayback.progress_ms) {
                    // Keep local progress, only sync if drift is significant (> 3 seconds)
                    const drift = Math.abs(newPlayback.progress_ms - spotifyPlayback.progress_ms);
                    if (drift > 3000) {
                        // Significant drift - sync with server
                        spotifyPlayback = newPlayback;
                    } else {
                        // Keep local progress, update other fields
                        const localProgress = spotifyPlayback.progress_ms;
                        spotifyPlayback = newPlayback;
                        spotifyPlayback.progress_ms = localProgress;
                    }
                } else {
                    // Track changed or play state changed - use server data
                    spotifyPlayback = newPlayback;
                }

                updateSpotifySummary();

                // Update only the now playing panel if modal is open (don't re-render entire content)
                const modal = document.getElementById('spotifyModal');
                if (modal && modal.classList.contains('active')) {
                    updateNowPlayingPanel();
                }
            } else if (resp.status === 401) {
                document.getElementById('summary-Spotify').textContent = 'Not connected';
            }
        } catch (err) {
            console.error('Failed to load Spotify playback:', err);
        }
    }

    function updateNowPlayingPanel() {
        // Skip full re-render while user is adjusting volume to prevent slider jumping
        if (isAdjustingVolume) {
            updateProgressBar();
            updateTracksNowPlaying();
            return;
        }

        const leftPanel = document.querySelector('.spotify-left-panel');
        if (leftPanel) {
            leftPanel.innerHTML = renderNowPlayingPanel();
            updateProgressBar();
        }
        // Also update now-playing indicator on tracks
        updateTracksNowPlaying();
    }

    function updateTracksNowPlaying() {
        const currentTrackUri = spotifyPlayback?.item?.uri;
        const isPlaying = spotifyPlayback?.is_playing;

        // Update artist tracks
        document.querySelectorAll('.spotify-artist-track[data-track-uri]').forEach((track, index) => {
            const trackUri = track.dataset.trackUri;
            const isCurrentTrack = trackUri === currentTrackUri;
            const numEl = track.querySelector('.spotify-artist-track-num');

            track.classList.toggle('now-playing', isCurrentTrack);
            if (numEl) {
                if (isCurrentTrack && isPlaying) {
                    numEl.innerHTML = '<img src="/icon/playing" class="now-playing-icon" alt="Playing">';
                } else {
                    numEl.textContent = index + 1;
                }
            }
        });

        // Update album tracks
        document.querySelectorAll('.spotify-album-track[data-track-uri]').forEach((track, index) => {
            const trackUri = track.dataset.trackUri;
            const isCurrentTrack = trackUri === currentTrackUri;
            const numEl = track.querySelector('.spotify-album-track-num');

            track.classList.toggle('now-playing', isCurrentTrack);
            if (numEl) {
                if (isCurrentTrack && isPlaying) {
                    numEl.innerHTML = '<img src="/icon/playing" class="now-playing-icon" alt="Playing">';
                } else {
                    numEl.textContent = index + 1;
                }
            }
        });
    }

    async function loadSpotifyDevices() {
        if (!spotifyStatus || !spotifyStatus.authenticated) return;

        try {
            const resp = await fetch('/api/spotify/devices');
            if (resp.ok) {
                spotifyDevices = await resp.json();
            }
        } catch (err) {
            console.error('Failed to load Spotify devices:', err);
        }
    }

    async function loadSpotifyPlaylists() {
        if (!spotifyStatus || !spotifyStatus.authenticated) return;

        try {
            const resp = await fetch('/api/spotify/playlists?limit=50');
            if (resp.ok) {
                const data = await resp.json();
                spotifyPlaylists = data.items || [];
            }
        } catch (err) {
            console.error('Failed to load Spotify playlists:', err);
        }
    }

    async function loadRecentTracks() {
        if (!spotifyStatus || !spotifyStatus.authenticated) return;

        try {
            const resp = await fetch('/api/spotify/recent?limit=50');
            if (resp.ok) {
                const data = await resp.json();
                spotifyRecentItems = data.items || [];
                // Re-render browse content if modal is open and on home tab
                const browseContent = document.getElementById('spotifyBrowseContent');
                if (browseContent && activeSpotifyBrowseTab === 'home') {
                    browseContent.innerHTML = renderBrowseContent();
                }
            }
        } catch (err) {
            console.error('Failed to load recent tracks:', err);
        }
    }

    async function loadTopArtists() {
        if (!spotifyStatus || !spotifyStatus.authenticated) return;

        try {
            const resp = await fetch('/api/spotify/top/artists?limit=20');
            if (resp.ok) {
                const data = await resp.json();
                spotifyTopArtists = data.items || [];
            }
        } catch (err) {
            console.error('Failed to load top artists:', err);
        }
    }

    async function loadTopTracks() {
        if (!spotifyStatus || !spotifyStatus.authenticated) return;

        try {
            const resp = await fetch('/api/spotify/top/tracks?limit=20');
            if (resp.ok) {
                const data = await resp.json();
                spotifyTopTracks = data.items || [];
            }
        } catch (err) {
            console.error('Failed to load top tracks:', err);
        }
    }

    // ===== Summary and Mini Player =====

    function updateSpotifySummary() {
        const summaryEl = document.getElementById('summary-Spotify');
        if (summaryEl) {
            if (!spotifyPlayback || !spotifyPlayback.item) {
                summaryEl.textContent = 'Not playing';
            } else {
                const track = spotifyPlayback.item;
                const artist = track.artists ? track.artists.map(a => a.name).join(', ') : '';
                summaryEl.textContent = spotifyPlayback.is_playing
                    ? `${track.name} - ${artist}`
                    : 'Paused';
            }
        }

        // Update mini player
        updateMiniPlayer();
    }

    function updateMiniPlayer() {
        const miniPlayer = document.getElementById('spotifyMiniPlayer');
        if (!miniPlayer) return;

        // Show mini player only when there's active playback
        if (!spotifyPlayback || !spotifyPlayback.item) {
            miniPlayer.style.display = 'none';
            if (typeof Screensaver !== 'undefined') {
                Screensaver.updateSpotify(spotifyPlayback);
            }
            return;
        }

        miniPlayer.style.display = 'flex';

        const track = spotifyPlayback.item;
        const album = track.album || {};
        const artists = track.artists ? track.artists.map(a => a.name).join(', ') : '';
        const albumArt = album.images && album.images.length > 0
            ? album.images[album.images.length - 1].url
            : '';

        // Update album art
        const artEl = document.getElementById('spotifyMiniArt');
        if (artEl) {
            artEl.innerHTML = albumArt ? `<img src="${albumArt}" alt="">` : '';
        }

        // Update track info
        const infoEl = document.getElementById('spotifyMiniInfo');
        if (infoEl) {
            infoEl.innerHTML = `
                <div class="spotify-mini-track">${escapeHtml(track.name)}</div>
                <div class="spotify-mini-artist">${escapeHtml(artists)}</div>
            `;
        }

        // Update play/pause icon
        const playIcon = document.getElementById('spotifyMiniPlayIcon');
        if (playIcon) {
            playIcon.src = spotifyPlayback.is_playing ? '/icon/pause' : '/icon/play';
            playIcon.alt = spotifyPlayback.is_playing ? 'Pause' : 'Play';
        }

        // Update volume slider and icon (only if not currently adjusting)
        if (!isAdjustingVolume) {
            const volumeSlider = document.getElementById('spotifyMiniVolumeSlider');
            if (volumeSlider && spotifyPlayback.device) {
                volumeSlider.value = spotifyPlayback.device.volume_percent || 50;
            }
            // Update volume icon
            const volume = spotifyPlayback.device ? spotifyPlayback.device.volume_percent : 50;
            const volumeIcon = document.getElementById('spotifyMiniVolumeIcon');
            if (volumeIcon) {
                volumeIcon.src = `/icon/${getVolumeIcon(volume)}`;
            }
        }

        // Update device name
        const deviceName = document.getElementById('spotifyMiniDeviceName');
        if (deviceName && spotifyPlayback.device) {
            deviceName.textContent = spotifyPlayback.device.name || 'Unknown';
        }

        // Update progress bar
        updateMiniPlayerProgress();

        // Update screensaver display
        if (typeof Screensaver !== 'undefined') {
            Screensaver.updateSpotify(spotifyPlayback);
        }
    }

    function updateMiniPlayerProgress() {
        const progressFill = document.getElementById('spotifyMiniProgressFill');
        if (!progressFill || !spotifyPlayback || !spotifyPlayback.item) return;

        const progress = spotifyPlayback.progress_ms || 0;
        const duration = spotifyPlayback.item.duration_ms || 1;
        const percent = (progress / duration) * 100;
        progressFill.style.width = `${percent}%`;
    }

    function handleMiniPlayerClick(event) {
        // Open the full modal when clicking on empty area (not on controls)
        openSpotifyModal();
    }

    // ===== Modal Functions =====

    function openSpotifyModal() {
        if (!spotifyStatus || !spotifyStatus.authenticated) {
            window.location.href = '/auth/spotify';
            return;
        }

        document.getElementById('spotifyModal').classList.add('active');
        loadSpotifyDevices();
        loadSpotifyPlaylists();
        loadRecentTracks();
        loadTopArtists();
        loadTopTracks();
        renderSpotifyContent();
        startSpotifyProgressUpdates();
    }

    function closeSpotifyModal() {
        document.getElementById('spotifyModal').classList.remove('active');
        stopSpotifyProgressUpdates();
    }

    function startSpotifyProgressUpdates() {
        // No longer needed - startMiniPlayerProgress() handles all progress updates
        // Keeping function for compatibility with openSpotifyModal()
    }

    function stopSpotifyProgressUpdates() {
        // No longer needed - startMiniPlayerProgress() handles all progress updates
    }

    function updateProgressBar() {
        const progressBar = document.getElementById('spotifyProgressBar');
        const currentTime = document.getElementById('spotifyCurrentTime');
        const totalTime = document.getElementById('spotifyTotalTime');

        if (!progressBar || !spotifyPlayback || !spotifyPlayback.item) return;

        const progress = spotifyPlayback.progress_ms || 0;
        const duration = spotifyPlayback.item.duration_ms || 1;
        const percent = (progress / duration) * 100;

        progressBar.style.width = `${percent}%`;
        if (currentTime) currentTime.textContent = formatTime(progress);
        if (totalTime) totalTime.textContent = formatTime(duration);
    }

    // ===== Render Functions =====

    function renderSpotifyContent() {
        const content = document.getElementById('spotifyModalContent');

        if (!spotifyStatus || !spotifyStatus.authenticated) {
            content.innerHTML = `
                <div class="spotify-auth-prompt">
                    <p>Connect your Spotify account to control playback</p>
                    <a href="/auth/spotify" class="modal-btn primary">Connect Spotify</a>
                </div>`;
            return;
        }

        // Split layout: left (now playing) + right (search/browse)
        let html = `
            <div class="spotify-split-layout">
                <div class="spotify-left-panel">
                    ${renderNowPlayingPanel()}
                </div>
                <div class="spotify-right-panel">
                    <div class="spotify-search-bar">
                        <img src="/icon/magnifying-glass" class="spotify-search-icon" alt="">
                        <input type="text" id="spotifySearchInput" class="spotify-search-input"
                               placeholder="What do you want to listen to?"
                               onkeydown="if(event.key==='Enter'){Spotify.performSearch();}"
                               oninput="Spotify.toggleSearchClear()">
                        <button class="spotify-search-clear" id="spotifySearchClear" onclick="Spotify.clearSearch()" style="display:none;">
                            <img src="/icon/xmark" alt="Clear">
                        </button>
                    </div>
                    <div class="spotify-browse-tabs">
                        <button class="spotify-browse-tab ${activeSpotifyBrowseTab === 'home' ? 'active' : ''}" onclick="Spotify.switchBrowseTab('home')">Home</button>
                        <button class="spotify-browse-tab ${activeSpotifyBrowseTab === 'library' ? 'active' : ''}" onclick="Spotify.switchBrowseTab('library')">Library</button>
                    </div>
                    <div class="spotify-browse-content" id="spotifyBrowseContent">
                        ${renderBrowseContent()}
                    </div>
                </div>
            </div>`;

        content.innerHTML = html;
        updateProgressBar();
    }

    function renderNowPlayingPanel() {
        if (!spotifyPlayback || !spotifyPlayback.item) {
            const deviceName = spotifyPlayback?.device?.name || 'No device';
            return `
                <div class="spotify-now-playing-panel">
                    <div class="spotify-no-playback">
                        <img src="/icon/spotify" class="spotify-no-playback-icon" alt="">
                        <p>No active playback</p>
                        <p class="spotify-hint">Start playing on any device</p>
                    </div>
                    <button class="spotify-device-btn" onclick="Spotify.openDeviceModal()">
                        <img src="/icon/volume-high" alt="">
                        <span>${escapeHtml(deviceName)}</span>
                    </button>
                </div>`;
        }

        const track = spotifyPlayback.item;
        const album = track.album || {};
        const artistsList = track.artists || [];
        const albumArt = album.images && album.images.length > 0 ? album.images[0].url : '';
        const isPlaying = spotifyPlayback.is_playing;
        const shuffleActive = spotifyPlayback.shuffle_state ? 'active' : '';
        const repeatState = spotifyPlayback.repeat_state || 'off';
        const repeatActive = repeatState !== 'off' ? 'active' : '';
        const volume = spotifyPlayback.device ? spotifyPlayback.device.volume_percent : 50;
        const deviceName = spotifyPlayback.device?.name || 'Unknown device';
        const albumId = album.id || '';

        // Build clickable artist links
        const artistLinks = artistsList.map(a =>
            `<span class="spotify-artist-link" onclick="Spotify.openArtistDetail('${a.id}')">${escapeHtml(a.name)}</span>`
        ).join(', ');

        return `
            <div class="spotify-now-playing-panel">
                <div class="spotify-album-art" ${albumId ? `onclick="Spotify.openAlbumDetail('${albumId}')" style="cursor:pointer"` : ''}>
                    ${albumArt ? `<img src="${albumArt}" alt="${escapeHtml(album.name || '')}">` : '<div class="spotify-no-art"></div>'}
                </div>
                <div class="spotify-track-info">
                    <div class="spotify-track-name">${escapeHtml(track.name)}</div>
                    <div class="spotify-track-artist">${artistLinks}</div>
                </div>
                <div class="spotify-progress">
                    <span class="spotify-time" id="spotifyCurrentTime">${formatTime(spotifyPlayback.progress_ms || 0)}</span>
                    <div class="spotify-progress-bar" onclick="Spotify.seek(event)">
                        <div class="spotify-progress-fill" id="spotifyProgressBar"></div>
                    </div>
                    <span class="spotify-time" id="spotifyTotalTime">${formatTime(track.duration_ms || 0)}</span>
                </div>
                <div class="spotify-controls">
                    <button class="spotify-control-btn spotify-secondary ${shuffleActive}" onclick="Spotify.toggleShuffle()">
                        <img src="/icon/shuffle" alt="Shuffle">
                    </button>
                    <button class="spotify-control-btn" onclick="Spotify.previous()">
                        <img src="/icon/backward-step" alt="Previous">
                    </button>
                    <button class="spotify-control-btn spotify-play-btn" onclick="Spotify.togglePlayback()">
                        <img src="/icon/${isPlaying ? 'pause' : 'play'}" alt="${isPlaying ? 'Pause' : 'Play'}">
                    </button>
                    <button class="spotify-control-btn" onclick="Spotify.next()">
                        <img src="/icon/forward-step" alt="Next">
                    </button>
                    <button class="spotify-control-btn spotify-secondary ${repeatActive}" onclick="Spotify.toggleRepeat()">
                        <img src="/icon/repeat" alt="Repeat">
                    </button>
                </div>
                <div class="spotify-volume">
                    <img src="/icon/${getVolumeIcon(volume)}" class="spotify-volume-icon" alt="Volume" onclick="Spotify.toggleMute()" style="cursor:pointer">
                    <input type="range" class="spotify-volume-slider" min="0" max="100" value="${volume}"
                           oninput="Spotify.onVolumeInput(this)">
                    <span class="spotify-volume-value">${volume}%</span>
                </div>
                <button class="spotify-device-btn" onclick="Spotify.openDeviceModal()">
                    <img src="/icon/speaker" alt="">
                    <span>${escapeHtml(deviceName)}</span>
                </button>
            </div>`;
    }

    function renderBrowseContent() {
        // Check if we're showing a detail view
        if (spotifyDetailView) {
            return renderDetailView();
        }

        if (activeSpotifyBrowseTab === 'home') {
            return renderHomeContent();
        } else {
            return renderLibraryContent();
        }
    }

    function renderDetailView() {
        const { type, data } = spotifyDetailView;

        let html = `
            <div class="spotify-detail-inline">
                <button class="spotify-back-btn" onclick="Spotify.goBackFromDetail()">
                    <img src="/icon/circle-arrow-left" alt="Back">
                    <span>Back</span>
                </button>`;

        if (type === 'album') {
            html += renderAlbumDetailInline(data);
        } else if (type === 'artist') {
            html += renderArtistDetailInline(data.artist, data.albums, data.topTracks || []);
        } else if (type === 'section') {
            html += renderSectionViewInline(data.title, data.items, data.roundImages);
        } else if (type === 'liked-songs') {
            html += renderLikedSongsInline(data.tracks, data.total);
        }

        html += `</div>`;
        return html;
    }

    function renderSectionViewInline(title, items, roundImages) {
        let html = `
            <div class="spotify-section-view">
                <h2 class="spotify-section-view-title">${escapeHtml(title)}</h2>
                <div class="spotify-section-grid">`;

        items.forEach(item => {
            const imageClass = roundImages || item.round ? 'round' : '';
            const clickAction = getItemClickAction(item);
            html += `
                <div class="spotify-home-card" onclick="${clickAction}">
                    <div class="spotify-home-card-image ${imageClass}">
                        ${item.image ? `<img src="${item.image}" alt="">` : '<div class="spotify-no-art"></div>'}
                        <button class="spotify-play-overlay" onclick="event.stopPropagation(); Spotify.playUri('${item.uri}')">
                            <img src="/icon/play" alt="Play">
                        </button>
                    </div>
                    <div class="spotify-home-card-name">${escapeHtml(item.name)}</div>
                    <div class="spotify-home-card-subtitle">${escapeHtml(item.subtitle)}</div>
                </div>`;
        });

        html += `</div></div>`;
        return html;
    }

    function openSectionView(title) {
        const sectionData = spotifySectionData[title];
        if (!sectionData) return;

        // Save current view to history
        if (spotifyDetailView) {
            spotifyDetailHistory.push(spotifyDetailView);
        }

        spotifyDetailView = {
            type: 'section',
            data: {
                title: title,
                items: sectionData.items,
                roundImages: sectionData.roundImages
            }
        };

        const browseContent = document.getElementById('spotifyBrowseContent');
        if (browseContent) {
            browseContent.innerHTML = renderBrowseContent();
        }
    }

    function renderAlbumDetailInline(album) {
        const image = album.images?.[0]?.url || '';
        const year = album.release_date?.split('-')[0] || '';
        const totalTracks = album.total_tracks || album.tracks?.total || 0;

        // Build clickable artist links
        const artistLinks = album.artists?.map(a =>
            `<span class="spotify-album-artist-link" onclick="event.stopPropagation(); Spotify.openArtistDetail('${a.id}')">${escapeHtml(a.name)}</span>`
        ).join(', ') || '';

        // Calculate total duration
        let totalDurationMs = 0;
        if (album.tracks?.items) {
            album.tracks.items.forEach(track => {
                totalDurationMs += track.duration_ms || 0;
            });
        }
        const totalMins = Math.floor(totalDurationMs / 60000);

        // Build tracks HTML with now-playing support
        let tracksHtml = '';
        const currentTrackUri = spotifyPlayback?.item?.uri;
        const isPlaying = spotifyPlayback?.is_playing;

        if (album.tracks?.items) {
            album.tracks.items.forEach((track, index) => {
                const isCurrentTrack = track.uri === currentTrackUri;
                const nowPlayingClass = isCurrentTrack ? ' now-playing' : '';
                const trackIndicator = isCurrentTrack && isPlaying
                    ? `<img src="/icon/playing" class="now-playing-icon" alt="Playing">`
                    : `${index + 1}`;

                tracksHtml += `
                    <div class="spotify-album-track${nowPlayingClass}" onclick="Spotify.playAlbumTrack('${album.uri}', ${index})" data-track-uri="${track.uri}">
                        <span class="spotify-album-track-num">${trackIndicator}</span>
                        <div class="spotify-album-track-info">
                            <div class="spotify-album-track-name">${escapeHtml(track.name)}</div>
                        </div>
                        <span class="spotify-album-track-duration">${formatTime(track.duration_ms)}</span>
                    </div>`;
            });
        }

        return `
            <div class="spotify-album-detail-layout">
                <div class="spotify-album-detail-left">
                    <div class="spotify-album-image-large">
                        ${image ? `<img src="${image}" alt="">` : ''}
                    </div>
                    <div class="spotify-album-info">
                        <div class="spotify-detail-type">Album</div>
                        <div class="spotify-album-name-large">${escapeHtml(album.name)}</div>
                        <div class="spotify-album-artists">${artistLinks}</div>
                        <div class="spotify-album-meta">${year} • ${totalTracks} songs • ${totalMins} min</div>
                    </div>
                    <div class="spotify-album-actions">
                        <button class="spotify-play-btn" onclick="Spotify.playUri('${album.uri}')">
                            <img src="/icon/play" alt="">
                            Play
                        </button>
                        <button class="spotify-shuffle-btn" onclick="Spotify.shufflePlay('${album.uri}')">
                            <img src="/icon/shuffle" alt="">
                            Shuffle
                        </button>
                    </div>
                    <div class="spotify-secondary-actions">
                        <button class="spotify-save-btn" id="albumSaveBtn-${album.id}" onclick="Spotify.toggleAlbumSaved('${album.id}')" title="Save to Library">
                            <img src="/icon/heart" alt="" id="albumSaveIcon-${album.id}">
                        </button>
                    </div>
                </div>
                <div class="spotify-album-detail-right">
                    <div class="spotify-album-tracklist">${tracksHtml}</div>
                </div>
            </div>
        `;
    }

    function renderArtistDetailInline(artist, albums, topTracks) {
        const image = artist.images?.[0]?.url || '';
        const followers = artist.followers?.total ? formatNumber(artist.followers.total) + ' followers' : '';
        const genres = artist.genres?.slice(0, 3).join(', ') || '';

        // Build top tracks HTML (limit to 10)
        let topTracksHtml = '';
        const currentTrackUri = spotifyPlayback?.item?.uri;
        const isPlaying = spotifyPlayback?.is_playing;

        topTracks.slice(0, 10).forEach((track, index) => {
            const trackImage = track.album?.images?.[2]?.url || track.album?.images?.[0]?.url || '';
            const duration = formatTime(track.duration_ms);
            const isCurrentTrack = track.uri === currentTrackUri;
            const nowPlayingClass = isCurrentTrack ? ' now-playing' : '';
            const trackIndicator = isCurrentTrack && isPlaying
                ? `<img src="/icon/playing" class="now-playing-icon" alt="Playing">`
                : `${index + 1}`;

            topTracksHtml += `
                <div class="spotify-artist-track${nowPlayingClass}" onclick="Spotify.playUri('${track.uri}')" data-track-uri="${track.uri}">
                    <span class="spotify-artist-track-num">${trackIndicator}</span>
                    <div class="spotify-artist-track-image">
                        ${trackImage ? `<img src="${trackImage}" alt="">` : ''}
                    </div>
                    <div class="spotify-artist-track-info">
                        <div class="spotify-artist-track-name">${escapeHtml(track.name)}</div>
                    </div>
                    <span class="spotify-artist-track-duration">${duration}</span>
                </div>`;
        });

        // Build albums HTML
        let albumsHtml = '';
        albums.forEach(album => {
            const albumImage = album.images?.[1]?.url || album.images?.[0]?.url || '';
            const year = album.release_date ? album.release_date.substring(0, 4) : '';
            albumsHtml += `
                <div class="spotify-artist-album" onclick="Spotify.openAlbumDetail('${album.id}')">
                    <div class="spotify-artist-album-image">
                        ${albumImage ? `<img src="${albumImage}" alt="">` : ''}
                    </div>
                    <div class="spotify-artist-album-info">
                        <div class="spotify-artist-album-name">${escapeHtml(album.name)}</div>
                        <div class="spotify-artist-album-year">${year}</div>
                    </div>
                </div>`;
        });

        return `
            <div class="spotify-artist-detail-layout">
                <div class="spotify-artist-detail-left">
                    <div class="spotify-artist-image-large">
                        ${image ? `<img src="${image}" alt="">` : ''}
                    </div>
                    <div class="spotify-artist-info">
                        <div class="spotify-detail-type">Artist</div>
                        <div class="spotify-artist-name-large">${escapeHtml(artist.name)}</div>
                        <div class="spotify-artist-genres">${escapeHtml(genres)}</div>
                        <div class="spotify-artist-followers">${followers}</div>
                    </div>
                    <div class="spotify-artist-actions">
                        <button class="spotify-play-btn" onclick="Spotify.playUri('${artist.uri}')">
                            <img src="/icon/play" alt="">
                            Play
                        </button>
                        <button class="spotify-shuffle-btn" onclick="Spotify.shufflePlay('${artist.uri}')">
                            <img src="/icon/shuffle" alt="">
                            Shuffle
                        </button>
                    </div>
                    <div class="spotify-secondary-actions">
                        <button class="spotify-follow-btn" id="artistFollowBtn-${artist.id}" onclick="Spotify.toggleArtistFollow('${artist.id}')" title="Follow">
                            <img src="/icon/user-plus" alt="" id="artistFollowIcon-${artist.id}">
                        </button>
                    </div>
                </div>
                <div class="spotify-artist-detail-right">
                    <div class="spotify-artist-section">
                        <div class="spotify-detail-section-title">Popular</div>
                        <div class="spotify-artist-top-tracks">${topTracksHtml}</div>
                    </div>
                    <div class="spotify-artist-section">
                        <div class="spotify-detail-section-title">Discography</div>
                        <div class="spotify-artist-albums">${albumsHtml}</div>
                    </div>
                </div>
            </div>
        `;
    }

    function renderLikedSongsInline(tracks, total) {
        const currentTrackUri = spotifyPlayback?.item?.uri;
        const isPlaying = spotifyPlayback?.is_playing;

        let tracksHtml = '';
        tracks.forEach((saved, index) => {
            const track = saved.track;
            const trackImage = track.album?.images?.[2]?.url || track.album?.images?.[0]?.url || '';
            const isCurrentTrack = track.uri === currentTrackUri;
            const nowPlayingClass = isCurrentTrack ? ' now-playing' : '';
            const trackIndicator = isCurrentTrack && isPlaying
                ? `<img src="/icon/playing" class="now-playing-icon" alt="Playing">`
                : `${index + 1}`;

            tracksHtml += `
                <div class="spotify-liked-track${nowPlayingClass}" onclick="Spotify.playUri('${track.uri}')" data-track-uri="${track.uri}">
                    <span class="spotify-liked-track-num">${trackIndicator}</span>
                    <div class="spotify-liked-track-image">
                        ${trackImage ? `<img src="${trackImage}" alt="">` : ''}
                    </div>
                    <div class="spotify-liked-track-info">
                        <div class="spotify-liked-track-name">${escapeHtml(track.name)}</div>
                        <div class="spotify-liked-track-artist">${track.artists?.map(a => a.name).join(', ') || ''}</div>
                    </div>
                    <div class="spotify-liked-track-album">${escapeHtml(track.album?.name || '')}</div>
                    <span class="spotify-liked-track-duration">${formatTime(track.duration_ms)}</span>
                </div>`;
        });

        return `
            <div class="spotify-liked-songs-layout">
                <div class="spotify-liked-songs-header">
                    <div class="spotify-liked-songs-image">
                        <div class="spotify-liked-songs-gradient"></div>
                        <img src="/icon/heart-solid" alt="" class="spotify-liked-songs-icon">
                    </div>
                    <div class="spotify-liked-songs-info">
                        <div class="spotify-detail-type">Playlist</div>
                        <div class="spotify-liked-songs-title">Liked Songs</div>
                        <div class="spotify-liked-songs-count">${total} songs</div>
                    </div>
                    <div class="spotify-liked-songs-actions">
                        <button class="spotify-play-btn" onclick="Spotify.playLikedSongs()">
                            <img src="/icon/play" alt="">
                            Play
                        </button>
                        <button class="spotify-shuffle-btn" onclick="Spotify.shuffleLikedSongs()">
                            <img src="/icon/shuffle" alt="">
                            Shuffle
                        </button>
                    </div>
                </div>
                <div class="spotify-liked-songs-tracks">
                    ${tracksHtml}
                </div>
            </div>
        `;
    }

    async function playLikedSongs() {
        // Play the first liked song
        try {
            const resp = await fetch('/api/spotify/library/tracks?limit=1');
            if (resp.ok) {
                const data = await resp.json();
                if (data.items && data.items.length > 0) {
                    playUri(data.items[0].track.uri);
                }
            }
        } catch (err) {
            console.error('Failed to play liked songs:', err);
        }
    }

    async function shuffleLikedSongs() {
        // Enable shuffle and play the first liked song
        try {
            await fetch('/api/spotify/shuffle', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ state: true })
            });

            const resp = await fetch('/api/spotify/library/tracks?limit=1');
            if (resp.ok) {
                const data = await resp.json();
                if (data.items && data.items.length > 0) {
                    playUri(data.items[0].track.uri);
                }
            }
        } catch (err) {
            console.error('Failed to shuffle liked songs:', err);
        }
    }

    function goBackFromDetail() {
        const browseContent = document.getElementById('spotifyBrowseContent');

        // Check if there's history to go back to
        if (spotifyDetailHistory.length > 0) {
            spotifyDetailView = spotifyDetailHistory.pop();
        } else {
            spotifyDetailView = null;
        }

        if (browseContent) {
            browseContent.innerHTML = renderBrowseContent();
        }
    }

    function renderHomeContent() {
        let html = `<div class="spotify-home-content">`;

        // Recently Played Section - shows individual tracks (deduplicated)
        if (spotifyRecentItems.length > 0) {
            const seen = new Set();
            const recent = spotifyRecentItems
                .map(item => ({
                    type: 'track',
                    id: item.track.id,
                    name: item.track.name,
                    subtitle: item.track.artists?.map(a => a.name).join(', ') || '',
                    image: item.track.album?.images?.[0]?.url || '',
                    uri: item.track.uri,
                    albumId: item.track.album?.id
                }))
                .filter(item => {
                    if (!item.id || seen.has(item.id)) return false;
                    seen.add(item.id);
                    return true;
                });
            html += renderSection('Recently Played', recent, false);
        }

        // Your Top Artists Section
        if (spotifyTopArtists.length > 0) {
            const artists = spotifyTopArtists.map(artist => ({
                type: 'artist',
                id: artist.id,
                name: artist.name,
                subtitle: 'Artist',
                image: artist.images?.[0]?.url || '',
                uri: artist.uri,
                round: true
            }));
            html += renderSection('Your Top Artists', artists, true);
        }

        // Your Playlists Section
        if (spotifyPlaylists.length > 0) {
            const playlists = spotifyPlaylists.map(playlist => ({
                type: 'playlist',
                id: playlist.id,
                name: playlist.name,
                subtitle: `${playlist.tracks.total} songs`,
                image: playlist.images?.[0]?.url || '',
                uri: playlist.uri
            }));
            html += renderSection('Your Playlists', playlists, false);
        }

        // Jump Back In - shows unique albums from top tracks
        if (spotifyTopTracks.length > 0) {
            const seen = new Set();
            const topAlbums = [];

            spotifyTopTracks.forEach(track => {
                const albumId = track.album?.id;
                if (albumId && !seen.has(albumId)) {
                    seen.add(albumId);
                    topAlbums.push({
                        type: 'album',
                        id: albumId,
                        name: track.album.name,
                        subtitle: track.album.artists?.[0]?.name || track.artists?.map(a => a.name).join(', ') || '',
                        image: track.album.images?.[0]?.url || '',
                        uri: track.album.uri
                    });
                }
            });

            if (topAlbums.length > 0) {
                html += renderSection('Jump Back In', topAlbums, false);
            }
        }

        html += `</div>`;

        if (html === `<div class="spotify-home-content"></div>`) {
            return `<div class="spotify-browse-empty">
                <p>Loading your music...</p>
                <p class="spotify-hint">Your personalized content will appear here</p>
            </div>`;
        }

        return html;
    }

    function renderSection(title, items, roundImages) {
        // Store all items for "Show all" functionality
        spotifySectionData[title] = { items: items, roundImages: roundImages };

        // Limit to 10 items in the row
        const displayItems = items.slice(0, 10);
        const hasMore = items.length > 10;

        // Escape title for use in onclick
        const escapedTitle = title.replace(/'/g, "\\'").replace(/"/g, '\\"');

        let html = `
            <div class="spotify-home-section">
                <div class="spotify-section-header">
                    <h4 class="spotify-section-title">${escapeHtml(title)}</h4>
                    ${hasMore ? `<span class="spotify-section-showall" onclick="Spotify.openSectionView('${escapedTitle}')">Show all</span>` : ''}
                </div>
                <div class="spotify-scroll-row">`;

        displayItems.forEach(item => {
            const imageClass = roundImages || item.round ? 'round' : '';
            const clickAction = getItemClickAction(item);
            html += `
                <div class="spotify-home-card" onclick="${clickAction}">
                    <div class="spotify-home-card-image ${imageClass}">
                        ${item.image ? `<img src="${item.image}" alt="">` : '<div class="spotify-no-art"></div>'}
                        <button class="spotify-play-overlay" onclick="event.stopPropagation(); Spotify.playUri('${item.uri}')">
                            <img src="/icon/play" alt="Play">
                        </button>
                    </div>
                    <div class="spotify-home-card-name">${escapeHtml(item.name)}</div>
                    <div class="spotify-home-card-subtitle">${escapeHtml(item.subtitle)}</div>
                </div>`;
        });

        html += `</div></div>`;
        return html;
    }

    function getItemClickAction(item) {
        switch (item.type) {
            case 'album':
                return `Spotify.openAlbumDetail('${item.id}')`;
            case 'artist':
                return `Spotify.openArtistDetail('${item.id}')`;
            case 'playlist':
                return `Spotify.openPlaylist('${item.id}', '${escapeHtml(item.name).replace(/'/g, "\\'")}')`;
            case 'track':
                // For tracks, open the album detail
                return item.albumId ? `Spotify.openAlbumDetail('${item.albumId}')` : `Spotify.playUri('${item.uri}')`;
            default:
                return `Spotify.playUri('${item.uri}')`;
        }
    }

    function renderLibraryContent() {
        // Load library data if not loaded
        if (!libraryLoaded) {
            loadLibraryData();
            return `<div class="spotify-loading">Loading library...</div>`;
        }

        // Build all library items
        let allItems = buildLibraryItems();

        // Filter by type
        if (libraryFilter !== 'all') {
            allItems = allItems.filter(item => item.filterType === libraryFilter);
        }

        // Filter by search
        if (librarySearch) {
            const search = librarySearch.toLowerCase();
            allItems = allItems.filter(item =>
                item.name.toLowerCase().includes(search) ||
                (item.subtitle && item.subtitle.toLowerCase().includes(search))
            );
        }

        // Sort items
        allItems = sortLibraryItems(allItems, librarySort);

        let html = `
            <div class="spotify-library-container">
                <div class="spotify-library-filters">
                    <button class="spotify-filter-pill ${libraryFilter === 'all' ? 'active' : ''}" onclick="Spotify.setLibraryFilter('all')">All</button>
                    <button class="spotify-filter-pill ${libraryFilter === 'playlists' ? 'active' : ''}" onclick="Spotify.setLibraryFilter('playlists')">Playlists</button>
                    <button class="spotify-filter-pill ${libraryFilter === 'artists' ? 'active' : ''}" onclick="Spotify.setLibraryFilter('artists')">Artists</button>
                    <button class="spotify-filter-pill ${libraryFilter === 'albums' ? 'active' : ''}" onclick="Spotify.setLibraryFilter('albums')">Albums</button>
                    <button class="spotify-filter-pill ${libraryFilter === 'shows' ? 'active' : ''}" onclick="Spotify.setLibraryFilter('shows')">Podcasts & Shows</button>
                </div>
                <div class="spotify-library-controls">
                    <div class="spotify-library-search">
                        <img src="/icon/magnifying-glass" alt="" class="spotify-library-search-icon">
                        <input type="text" placeholder="Search in Your Library" value="${escapeHtml(librarySearch)}"
                               oninput="Spotify.setLibrarySearch(this.value)" class="spotify-library-search-input">
                        ${librarySearch ? `<button class="spotify-library-search-clear" onclick="Spotify.setLibrarySearch('')"><img src="/icon/xmark" alt=""></button>` : ''}
                    </div>
                    <div class="spotify-library-sort">
                        <select onchange="Spotify.setLibrarySort(this.value)" class="spotify-library-sort-select">
                            <option value="recents" ${librarySort === 'recents' ? 'selected' : ''}>Recents</option>
                            <option value="alphabetical" ${librarySort === 'alphabetical' ? 'selected' : ''}>Alphabetical</option>
                            <option value="creator" ${librarySort === 'creator' ? 'selected' : ''}>Creator</option>
                        </select>
                    </div>
                </div>
                <div class="spotify-library-grid">`;

        // Render items
        allItems.forEach(item => {
            const roundClass = item.type === 'artist' ? ' round' : '';
            html += `
                <div class="spotify-library-item${roundClass}" onclick="${item.onclick}">
                    <div class="spotify-library-item-image${roundClass}">
                        ${item.image ? `<img src="${item.image}" alt="">` : `<div class="spotify-no-art${roundClass}"></div>`}
                        ${item.type === 'liked' ? '<div class="spotify-liked-songs-gradient"></div>' : ''}
                    </div>
                    <div class="spotify-library-item-info">
                        <div class="spotify-library-item-name">${escapeHtml(item.name)}</div>
                        <div class="spotify-library-item-meta">${escapeHtml(item.subtitle)}</div>
                    </div>
                </div>`;
        });

        html += `</div></div>`;
        return html;
    }

    function buildLibraryItems() {
        const items = [];

        // Add Liked Songs first (always)
        items.push({
            type: 'liked',
            filterType: 'playlists',
            name: 'Liked Songs',
            subtitle: `Playlist • ${likedSongsTotal} songs`,
            image: '',
            onclick: `Spotify.openLikedSongs()`,
            addedAt: new Date().toISOString(), // Always at top for recents
            creator: ''
        });

        // Add playlists
        spotifyPlaylists.forEach(playlist => {
            items.push({
                type: 'playlist',
                filterType: 'playlists',
                name: playlist.name,
                subtitle: `Playlist • ${playlist.owner?.display_name || ''}`,
                image: playlist.images?.[0]?.url || '',
                onclick: `Spotify.openPlaylist('${playlist.id}', '${escapeHtml(playlist.name).replace(/'/g, "\\'")}')`,
                addedAt: '', // Playlists don't have addedAt
                creator: playlist.owner?.display_name || ''
            });
        });

        // Add albums
        libraryAlbums.forEach(saved => {
            const album = saved.album;
            items.push({
                type: 'album',
                filterType: 'albums',
                name: album.name,
                subtitle: `Album • ${album.artists?.map(a => a.name).join(', ') || ''}`,
                image: album.images?.[0]?.url || '',
                onclick: `Spotify.openAlbumDetail('${album.id}')`,
                addedAt: saved.added_at || '',
                creator: album.artists?.[0]?.name || ''
            });
        });

        // Add artists
        libraryArtists.forEach(artist => {
            items.push({
                type: 'artist',
                filterType: 'artists',
                name: artist.name,
                subtitle: 'Artist',
                image: artist.images?.[0]?.url || '',
                onclick: `Spotify.openArtistDetail('${artist.id}')`,
                addedAt: '', // Artists don't have addedAt
                creator: artist.name
            });
        });

        // Add shows
        libraryShows.forEach(saved => {
            const show = saved.show;
            items.push({
                type: 'show',
                filterType: 'shows',
                name: show.name,
                subtitle: `Podcast • ${show.publisher || ''}`,
                image: show.images?.[0]?.url || '',
                onclick: `Spotify.openShow('${show.id}')`,
                addedAt: saved.added_at || '',
                creator: show.publisher || ''
            });
        });

        return items;
    }

    function sortLibraryItems(items, sortBy) {
        // Always keep Liked Songs first
        const likedSongs = items.filter(i => i.type === 'liked');
        const rest = items.filter(i => i.type !== 'liked');

        switch (sortBy) {
            case 'alphabetical':
                rest.sort((a, b) => a.name.localeCompare(b.name));
                break;
            case 'creator':
                rest.sort((a, b) => (a.creator || '').localeCompare(b.creator || ''));
                break;
            case 'recents':
            default:
                // Sort by addedAt date (most recent first), items without dates go to end
                rest.sort((a, b) => {
                    if (!a.addedAt && !b.addedAt) return 0;
                    if (!a.addedAt) return 1;
                    if (!b.addedAt) return -1;
                    return new Date(b.addedAt) - new Date(a.addedAt);
                });
                break;
        }

        return [...likedSongs, ...rest];
    }

    async function loadLibraryData() {
        try {
            const [albumsResp, artistsResp, tracksResp, showsResp] = await Promise.all([
                fetch('/api/spotify/library/albums?limit=50'),
                fetch('/api/spotify/library/artists?limit=50'),
                fetch('/api/spotify/library/tracks?limit=1'), // Just get total count
                fetch('/api/spotify/library/shows?limit=50')
            ]);

            if (albumsResp.ok) {
                const data = await albumsResp.json();
                libraryAlbums = data.items || [];
            }
            if (artistsResp.ok) {
                const data = await artistsResp.json();
                libraryArtists = data.items || [];
            }
            if (tracksResp.ok) {
                const data = await tracksResp.json();
                likedSongsTotal = data.total || 0;
            }
            if (showsResp.ok) {
                const data = await showsResp.json();
                libraryShows = data.items || [];
            }

            libraryLoaded = true;

            // Re-render if still on library tab
            if (activeSpotifyBrowseTab === 'library') {
                const browseContent = document.getElementById('spotifyBrowseContent');
                if (browseContent) {
                    browseContent.innerHTML = renderBrowseContent();
                }
            }
        } catch (err) {
            console.error('Failed to load library data:', err);
        }
    }

    function setLibraryFilter(filter) {
        libraryFilter = filter;
        const browseContent = document.getElementById('spotifyBrowseContent');
        if (browseContent) {
            browseContent.innerHTML = renderBrowseContent();
        }
    }

    function setLibrarySort(sort) {
        librarySort = sort;
        const browseContent = document.getElementById('spotifyBrowseContent');
        if (browseContent) {
            browseContent.innerHTML = renderBrowseContent();
        }
    }

    function setLibrarySearch(search) {
        librarySearch = search;
        const browseContent = document.getElementById('spotifyBrowseContent');
        if (browseContent) {
            browseContent.innerHTML = renderBrowseContent();
        }
    }

    async function openLikedSongs() {
        const browseContent = document.getElementById('spotifyBrowseContent');

        if (spotifyDetailView) {
            spotifyDetailHistory.push(spotifyDetailView);
        }

        browseContent.innerHTML = '<div class="spotify-loading">Loading liked songs...</div>';

        try {
            const resp = await fetch('/api/spotify/library/tracks?limit=50');
            if (resp.ok) {
                const data = await resp.json();
                spotifyDetailView = {
                    type: 'liked-songs',
                    data: {
                        tracks: data.items || [],
                        total: data.total || 0
                    }
                };
                browseContent.innerHTML = renderBrowseContent();
            } else {
                browseContent.innerHTML = '<div class="spotify-error">Failed to load liked songs</div>';
            }
        } catch (err) {
            console.error('Failed to load liked songs:', err);
            browseContent.innerHTML = '<div class="spotify-error">Failed to load liked songs</div>';
        }
    }

    function openShow(showId) {
        // Placeholder for show detail - could be implemented later
        console.log('Open show:', showId);
    }

    function switchBrowseTab(tab) {
        activeSpotifyBrowseTab = tab;
        spotifyDetailView = null; // Clear detail view when switching tabs
        spotifyDetailHistory = []; // Clear navigation history
        const browseContent = document.getElementById('spotifyBrowseContent');
        if (browseContent) {
            browseContent.innerHTML = renderBrowseContent();
        }
        // Update tab buttons
        document.querySelectorAll('.spotify-browse-tab').forEach(btn => {
            btn.classList.toggle('active', btn.textContent.toLowerCase() === tab);
        });
    }

    // ===== Device Modal Functions =====

    function openDeviceModal() {
        document.getElementById('spotifyDeviceModal').classList.add('active');
        renderDeviceModalContent();
    }

    function closeDeviceModal() {
        document.getElementById('spotifyDeviceModal').classList.remove('active');
        // Clear pending play state if modal closed without selecting a device
        pendingPlayUri = null;
        pendingShufflePlay = false;
    }

    function renderDeviceModalContent() {
        const content = document.getElementById('spotifyDeviceContent');

        if (spotifyDevices.length === 0) {
            content.innerHTML = `
                <div class="spotify-no-devices">
                    <p>No devices found</p>
                    <p class="spotify-hint">Open Spotify on a device to see it here</p>
                    <button class="modal-btn secondary" onclick="Spotify.loadDevices().then(Spotify.renderDeviceModalContent)">Refresh</button>
                </div>`;
            return;
        }

        let html = `<div class="spotify-devices-list">`;
        spotifyDevices.forEach(device => {
            const isActive = device.is_active ? 'active' : '';
            const icon = getDeviceIcon(device.type);
            html += `
                <div class="spotify-device ${isActive}" onclick="Spotify.transferToDevice('${device.id}'); Spotify.closeDeviceModal();">
                    <div class="spotify-device-icon">${icon}</div>
                    <div class="spotify-device-info">
                        <div class="spotify-device-name">${escapeHtml(device.name)}</div>
                        <div class="spotify-device-type">${device.type}${device.is_active ? ' - Active' : ''}</div>
                    </div>
                    ${device.is_active ? '<div class="spotify-device-active-indicator"></div>' : ''}
                </div>`;
        });
        html += `</div>`;
        content.innerHTML = html;
    }

    // ===== Album Detail Functions =====

    async function openAlbumDetail(albumId) {
        const browseContent = document.getElementById('spotifyBrowseContent');

        // Save current view to history if we're navigating from another detail view
        if (spotifyDetailView) {
            spotifyDetailHistory.push(spotifyDetailView);
        }

        browseContent.innerHTML = '<div class="spotify-loading">Loading album...</div>';

        try {
            const resp = await fetch(`/api/spotify/album/${albumId}`);
            if (resp.ok) {
                const album = await resp.json();
                spotifyDetailView = { type: 'album', data: album };
                browseContent.innerHTML = renderBrowseContent();
                // Check if album is saved and update button
                checkAlbumSaved(albumId);
            } else {
                browseContent.innerHTML = '<div class="spotify-error">Failed to load album</div>';
            }
        } catch (err) {
            console.error('Failed to load album:', err);
            browseContent.innerHTML = '<div class="spotify-error">Failed to load album</div>';
        }
    }

    async function checkAlbumSaved(albumId) {
        try {
            const resp = await fetch(`/api/spotify/album/${albumId}/saved`);
            if (resp.ok) {
                const data = await resp.json();
                albumSavedStates[albumId] = data.saved;
                updateAlbumSaveButton(albumId, data.saved);
            }
        } catch (err) {
            console.error('Failed to check album saved:', err);
        }
    }

    function updateAlbumSaveButton(albumId, isSaved) {
        const icon = document.getElementById(`albumSaveIcon-${albumId}`);
        const btn = document.getElementById(`albumSaveBtn-${albumId}`);
        if (icon) {
            icon.src = isSaved ? '/icon/heart-solid' : '/icon/heart';
        }
        if (btn) {
            btn.title = isSaved ? 'Remove from Library' : 'Save to Library';
            btn.classList.toggle('saved', isSaved);
        }
    }

    async function toggleAlbumSaved(albumId) {
        const isSaved = albumSavedStates[albumId] || false;
        try {
            const resp = await fetch(`/api/spotify/album/${albumId}/save`, {
                method: isSaved ? 'DELETE' : 'PUT'
            });
            if (resp.ok || resp.status === 204) {
                albumSavedStates[albumId] = !isSaved;
                updateAlbumSaveButton(albumId, !isSaved);
            }
        } catch (err) {
            console.error('Failed to toggle album saved:', err);
        }
    }

    // ===== Artist Detail Functions =====

    async function openArtistDetail(artistId) {
        const browseContent = document.getElementById('spotifyBrowseContent');

        // Save current view to history if we're navigating from another detail view
        if (spotifyDetailView) {
            spotifyDetailHistory.push(spotifyDetailView);
        }

        browseContent.innerHTML = '<div class="spotify-loading">Loading artist...</div>';

        try {
            const [artistResp, albumsResp, topTracksResp] = await Promise.all([
                fetch(`/api/spotify/artist/${artistId}`),
                fetch(`/api/spotify/artist/${artistId}/albums?limit=30`),
                fetch(`/api/spotify/artist/${artistId}/top-tracks`)
            ]);

            if (artistResp.ok && albumsResp.ok && topTracksResp.ok) {
                const artist = await artistResp.json();
                const albumsData = await albumsResp.json();
                const topTracksData = await topTracksResp.json();
                spotifyDetailView = {
                    type: 'artist',
                    data: {
                        artist,
                        albums: albumsData.items || [],
                        topTracks: topTracksData.tracks || []
                    }
                };
                browseContent.innerHTML = renderBrowseContent();
                // Check if artist is followed and update button
                checkArtistFollowing(artistId);
            } else {
                browseContent.innerHTML = '<div class="spotify-error">Failed to load artist</div>';
            }
        } catch (err) {
            console.error('Failed to load artist:', err);
            browseContent.innerHTML = '<div class="spotify-error">Failed to load artist</div>';
        }
    }

    async function checkArtistFollowing(artistId) {
        try {
            const resp = await fetch(`/api/spotify/artist/${artistId}/following`);
            if (resp.ok) {
                const data = await resp.json();
                artistFollowingStates[artistId] = data.following;
                updateArtistFollowButton(artistId, data.following);
            }
        } catch (err) {
            console.error('Failed to check artist following:', err);
        }
    }

    function updateArtistFollowButton(artistId, isFollowing) {
        const icon = document.getElementById(`artistFollowIcon-${artistId}`);
        const btn = document.getElementById(`artistFollowBtn-${artistId}`);
        if (icon) {
            icon.src = isFollowing ? '/icon/user-check' : '/icon/user-plus';
        }
        if (btn) {
            btn.title = isFollowing ? 'Unfollow' : 'Follow';
            btn.classList.toggle('following', isFollowing);
        }
    }

    async function toggleArtistFollow(artistId) {
        const isFollowing = artistFollowingStates[artistId] || false;
        try {
            const resp = await fetch(`/api/spotify/artist/${artistId}/follow`, {
                method: isFollowing ? 'DELETE' : 'PUT'
            });
            if (resp.ok || resp.status === 204) {
                artistFollowingStates[artistId] = !isFollowing;
                updateArtistFollowButton(artistId, !isFollowing);
            }
        } catch (err) {
            console.error('Failed to toggle artist follow:', err);
        }
    }

    // ===== Playback Controls =====

    async function togglePlayback() {
        const endpoint = spotifyPlayback && spotifyPlayback.is_playing ? '/api/spotify/pause' : '/api/spotify/play';
        const deviceId = spotifyPlayback?.device?.id || '';
        try {
            const resp = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ device_id: deviceId })
            });
            if (!resp.ok) {
                const text = await resp.text();
                console.error('Playback toggle failed:', resp.status, text);
            }
            setTimeout(loadSpotifyPlayback, 300);
        } catch (err) {
            console.error('Playback toggle failed:', err);
        }
    }

    async function next() {
        try {
            const deviceId = spotifyPlayback?.device?.id || '';
            const resp = await fetch('/api/spotify/next', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ device_id: deviceId })
            });
            if (!resp.ok) {
                const text = await resp.text();
                console.error('Next failed:', resp.status, text);
            }
            // Poll a few times to catch the track change
            setTimeout(loadSpotifyPlayback, 300);
            setTimeout(loadSpotifyPlayback, 800);
            setTimeout(loadSpotifyPlayback, 1500);
        } catch (err) {
            console.error('Next failed:', err);
        }
    }

    async function previous() {
        try {
            const deviceId = spotifyPlayback?.device?.id || '';
            const resp = await fetch('/api/spotify/previous', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ device_id: deviceId })
            });
            if (!resp.ok) {
                const text = await resp.text();
                console.error('Previous failed:', resp.status, text);
            }
            // Poll a few times to catch the track change
            setTimeout(loadSpotifyPlayback, 300);
            setTimeout(loadSpotifyPlayback, 800);
            setTimeout(loadSpotifyPlayback, 1500);
        } catch (err) {
            console.error('Previous failed:', err);
        }
    }

    // ===== Volume Controls =====

    function getVolumeIcon(volume) {
        if (isMuted) return 'volume-mute';
        if (volume === 0) return 'volume-off';
        if (volume < 50) return 'volume-low';
        return 'volume-high';
    }

    function updateVolumeIcons(volume) {
        const icon = getVolumeIcon(volume);
        document.querySelectorAll('.spotify-volume-icon, .spotify-mini-volume-icon').forEach(img => {
            img.src = `/icon/${icon}`;
        });
    }

    async function toggleMute() {
        if (!spotifyPlayback || !spotifyPlayback.device) return;

        if (!isMuted) {
            // Mute: save current volume and set to 0
            preMuteVolume = spotifyPlayback.device.volume_percent || 50;
            isMuted = true;
            await setVolume(0);
        } else {
            // Unmute: restore previous volume
            isMuted = false;
            await setVolume(preMuteVolume || 50);
        }

        // Update all volume sliders and icons
        const newVolume = spotifyPlayback.device.volume_percent;
        document.querySelectorAll('.spotify-volume-slider, .spotify-mini-volume-slider').forEach(slider => {
            slider.value = newVolume;
        });
        document.querySelectorAll('.spotify-volume-value').forEach(el => {
            el.textContent = newVolume + '%';
        });
        updateVolumeIcons(newVolume);
    }

    function onVolumeInput(slider) {
        // Immediately update UI during drag
        isAdjustingVolume = true;
        const volume = parseInt(slider.value);

        // Clear mute state when user manually adjusts volume
        if (volume > 0) {
            isMuted = false;
        }

        // Update volume display text
        document.querySelectorAll('.spotify-volume-value').forEach(el => {
            el.textContent = volume + '%';
        });

        // Update volume icons
        updateVolumeIcons(volume);

        // Update local state to prevent jumps on next poll
        if (spotifyPlayback && spotifyPlayback.device) {
            spotifyPlayback.device.volume_percent = volume;
        }

        // Debounce the API call - only send when user stops dragging
        if (volumeDebounceTimeout) {
            clearTimeout(volumeDebounceTimeout);
        }

        volumeDebounceTimeout = setTimeout(() => {
            setVolume(volume);
        }, 300);
    }

    async function setVolume(volume) {
        try {
            // Update local state
            if (spotifyPlayback && spotifyPlayback.device) {
                spotifyPlayback.device.volume_percent = parseInt(volume);
            }

            await fetch('/api/spotify/volume', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ volume_percent: parseInt(volume) })
            });

            // Keep adjusting flag for a bit to prevent poll overwrite
            setTimeout(() => {
                isAdjustingVolume = false;
            }, 1000);
        } catch (err) {
            console.error('Volume change failed:', err);
            isAdjustingVolume = false;
        }
    }

    async function toggleShuffle() {
        const newState = !spotifyPlayback.shuffle_state;
        try {
            await fetch('/api/spotify/shuffle', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ state: newState })
            });
            setTimeout(loadSpotifyPlayback, 300);
        } catch (err) {
            console.error('Shuffle toggle failed:', err);
        }
    }

    async function toggleRepeat() {
        const states = ['off', 'context', 'track'];
        const currentIndex = states.indexOf(spotifyPlayback.repeat_state || 'off');
        const newState = states[(currentIndex + 1) % states.length];
        try {
            await fetch('/api/spotify/repeat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ state: newState })
            });
            setTimeout(loadSpotifyPlayback, 300);
        } catch (err) {
            console.error('Repeat toggle failed:', err);
        }
    }

    async function seek(event) {
        if (!spotifyPlayback || !spotifyPlayback.item) return;

        const bar = event.currentTarget;
        const rect = bar.getBoundingClientRect();
        const percent = (event.clientX - rect.left) / rect.width;
        const positionMs = Math.floor(percent * spotifyPlayback.item.duration_ms);

        try {
            await fetch('/api/spotify/seek', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ position_ms: positionMs })
            });
            spotifyPlayback.progress_ms = positionMs;
            updateProgressBar();
        } catch (err) {
            console.error('Seek failed:', err);
        }
    }

    // ===== Play Functions =====

    async function transferToDevice(deviceId) {
        try {
            const hasPendingPlay = !!pendingPlayUri;

            // Transfer to device - don't auto-play if we have a pending URI to play
            await fetch('/api/spotify/transfer', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ device_id: deviceId, play: !hasPendingPlay })
            });

            // If there's a pending play URI, play it after transfer
            if (hasPendingPlay) {
                const uriToPlay = pendingPlayUri;
                const shouldShuffle = pendingShufflePlay;
                const positionToPlay = pendingPlayPosition;
                pendingPlayUri = null;
                pendingShufflePlay = false;
                pendingPlayPosition = 0;

                setTimeout(async () => {
                    // Enable shuffle if it was a shuffle play
                    if (shouldShuffle) {
                        await fetch('/api/spotify/shuffle', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ state: true })
                        });
                    }

                    await fetch('/api/spotify/play', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ uri: uriToPlay, device_id: deviceId, position: positionToPlay })
                    });
                    loadSpotifyDevices();
                    loadSpotifyPlayback();
                }, 500);
            } else {
                setTimeout(() => {
                    loadSpotifyDevices();
                    loadSpotifyPlayback();
                }, 500);
            }
        } catch (err) {
            console.error('Transfer failed:', err);
        }
    }

    async function playUri(uri) {
        try {
            // Always fetch fresh device list before playing
            const devicesResp = await fetch('/api/spotify/devices');
            if (!devicesResp.ok) {
                console.error('Failed to fetch devices');
                return;
            }
            const devices = await devicesResp.json();
            spotifyDevices = devices || [];

            // Check if there's an active device
            const hasActiveDevice = spotifyDevices.some(d => d.is_active);

            if (!hasActiveDevice) {
                // No active device - show device picker
                pendingPlayUri = uri;
                openDeviceModal();
                return;
            }

            // Active device exists, proceed with play
            const resp = await fetch('/api/spotify/play', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ uri: uri })
            });

            if (!resp.ok) {
                const errorText = await resp.text();
                console.error('Play failed:', errorText);
                return;
            }

            setTimeout(loadSpotifyPlayback, 500);
        } catch (err) {
            console.error('Play failed:', err);
        }
    }

    // Play a specific track within an album context (enables next/previous)
    async function playAlbumTrack(albumUri, trackPosition) {
        try {
            // Always fetch fresh device list before playing
            const devicesResp = await fetch('/api/spotify/devices');
            if (!devicesResp.ok) {
                console.error('Failed to fetch devices');
                return;
            }
            const devices = await devicesResp.json();
            spotifyDevices = devices || [];

            // Check if there's an active device
            const hasActiveDevice = spotifyDevices.some(d => d.is_active);

            if (!hasActiveDevice) {
                // No active device - show device picker
                pendingPlayUri = albumUri;
                pendingPlayPosition = trackPosition;
                openDeviceModal();
                return;
            }

            // Active device exists, proceed with play
            const resp = await fetch('/api/spotify/play', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ uri: albumUri, position: trackPosition })
            });

            if (!resp.ok) {
                const errorText = await resp.text();
                console.error('Play failed:', errorText);
                return;
            }

            setTimeout(loadSpotifyPlayback, 500);
        } catch (err) {
            console.error('Play album track failed:', err);
        }
    }

    async function shufflePlay(uri) {
        try {
            // Always fetch fresh device list before playing
            const devicesResp = await fetch('/api/spotify/devices');
            if (!devicesResp.ok) {
                console.error('Failed to fetch devices');
                return;
            }
            const devices = await devicesResp.json();
            spotifyDevices = devices || [];

            // Check if there's an active device
            const hasActiveDevice = spotifyDevices.some(d => d.is_active);

            if (!hasActiveDevice) {
                // No active device - show device picker with shuffle flag
                pendingPlayUri = uri;
                pendingShufflePlay = true;
                openDeviceModal();
                return;
            }

            // Enable shuffle first
            await fetch('/api/spotify/shuffle', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ state: true })
            });

            // Then play (skip device check since we just checked)
            const resp = await fetch('/api/spotify/play', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ uri: uri })
            });

            if (!resp.ok) {
                const errorText = await resp.text();
                console.error('Shuffle play failed:', errorText);
                return;
            }

            setTimeout(loadSpotifyPlayback, 500);
        } catch (err) {
            console.error('Shuffle play failed:', err);
        }
    }

    // ===== Playlist Functions =====

    async function openPlaylist(playlistId, name) {
        document.getElementById('spotifyPlaylistTitle').textContent = name;
        document.getElementById('spotifyPlaylistModal').classList.add('active');

        const content = document.getElementById('spotifyPlaylistContent');
        content.innerHTML = '<div class="spotify-loading">Loading tracks...</div>';

        try {
            const resp = await fetch(`/api/spotify/playlist/${playlistId}/tracks?limit=50`);
            if (resp.ok) {
                const data = await resp.json();
                renderPlaylistTracks(data.items, `spotify:playlist:${playlistId}`);
            }
        } catch (err) {
            content.innerHTML = '<div class="spotify-error">Failed to load tracks</div>';
        }
    }

    function renderPlaylistTracks(tracks, contextUri) {
        const content = document.getElementById('spotifyPlaylistContent');

        let html = `
            <button class="spotify-play-all-btn" onclick="Spotify.playUri('${contextUri}')">
                <img src="/icon/play" alt=""> Play All
            </button>
            <div class="spotify-tracks-list">`;

        tracks.forEach((item, index) => {
            if (!item.track) return;
            const track = item.track;
            const artists = track.artists.map(a => a.name).join(', ');
            const albumArt = track.album.images && track.album.images.length > 0 ? track.album.images[track.album.images.length - 1].url : '';
            html += `
                <div class="spotify-track-row" onclick="Spotify.playUri('${contextUri}', ${index})">
                    <div class="spotify-track-thumb">
                        ${albumArt ? `<img src="${albumArt}" alt="">` : ''}
                    </div>
                    <div class="spotify-track-details">
                        <div class="spotify-track-title">${escapeHtml(track.name)}</div>
                        <div class="spotify-track-subtitle">${escapeHtml(artists)}</div>
                    </div>
                    <div class="spotify-track-duration">${formatTime(track.duration_ms)}</div>
                </div>`;
        });
        html += `</div>`;
        content.innerHTML = html;
    }

    function closePlaylistModal() {
        document.getElementById('spotifyPlaylistModal').classList.remove('active');
    }

    // ===== Search Functions =====

    function toggleSearchClear() {
        const input = document.getElementById('spotifySearchInput');
        const clearBtn = document.getElementById('spotifySearchClear');
        if (clearBtn) {
            clearBtn.style.display = input.value.length > 0 ? 'flex' : 'none';
        }
    }

    function clearSearch() {
        const input = document.getElementById('spotifySearchInput');
        if (input) {
            input.value = '';
        }
        toggleSearchClear();
        switchBrowseTab('home');
    }

    async function performSearch() {
        const input = document.getElementById('spotifySearchInput');
        const browseContent = document.getElementById('spotifyBrowseContent');
        const query = input.value.trim();

        if (!query) return;

        toggleSearchClear();

        // Deselect tabs when searching
        document.querySelectorAll('.spotify-browse-tab').forEach(btn => btn.classList.remove('active'));
        browseContent.innerHTML = '<div class="spotify-loading">Searching...</div>';

        try {
            const resp = await fetch(`/api/spotify/search?q=${encodeURIComponent(query)}&type=track,artist,album,playlist&limit=10`);
            if (resp.ok) {
                const results = await resp.json();
                renderSearchResults(results, browseContent);
            }
        } catch (err) {
            browseContent.innerHTML = '<div class="spotify-error">Search failed</div>';
        }
    }

    function renderSearchResults(results, container) {
        let html = '<div class="spotify-search-results-container">';

        const tracks = results.tracks?.items || [];
        const artists = results.artists?.items || [];
        const albums = results.albums?.items || [];
        const playlists = (results.playlists?.items || []).filter(p => p);

        // Determine top result (prefer artist match, then album, then track)
        let topResult = null;
        if (artists.length > 0) {
            topResult = { type: 'artist', data: artists[0] };
        } else if (albums.length > 0) {
            topResult = { type: 'album', data: albums[0] };
        } else if (tracks.length > 0) {
            topResult = { type: 'track', data: tracks[0] };
        }

        // Top Result + Songs row
        if (topResult || tracks.length > 0) {
            html += '<div class="spotify-search-top-row">';

            // Top Result
            if (topResult) {
                html += '<div class="spotify-top-result">';
                html += '<h4 class="spotify-search-section-title">Top result</h4>';
                html += renderTopResultCard(topResult);
                html += '</div>';
            }

            // Songs
            if (tracks.length > 0) {
                html += '<div class="spotify-search-songs">';
                html += '<h4 class="spotify-search-section-title">Songs</h4>';
                html += '<div class="spotify-search-songs-list">';
                tracks.slice(0, 4).forEach(track => {
                    const trackArtists = track.artists?.map(a => a.name).join(', ') || '';
                    const albumArt = track.album?.images?.[track.album.images.length - 1]?.url || '';
                    html += `
                        <div class="spotify-search-song-row" onclick="Spotify.playUri('${track.uri}')">
                            <div class="spotify-search-song-thumb">
                                ${albumArt ? `<img src="${albumArt}" alt="">` : ''}
                            </div>
                            <div class="spotify-search-song-info">
                                <div class="spotify-search-song-title">${escapeHtml(track.name)}</div>
                                <div class="spotify-search-song-artist">${escapeHtml(trackArtists)}</div>
                            </div>
                            <div class="spotify-search-song-duration">${formatTime(track.duration_ms)}</div>
                        </div>`;
                });
                html += '</div></div>';
            }

            html += '</div>';
        }

        // Artists row
        if (artists.length > 0) {
            html += '<div class="spotify-search-section">';
            html += '<h4 class="spotify-search-section-title">Artists</h4>';
            html += '<div class="spotify-search-row">';
            artists.slice(0, 7).forEach(artist => {
                const image = artist.images?.[0]?.url || '';
                html += `
                    <div class="spotify-search-card spotify-search-card-round" onclick="Spotify.openArtistDetail('${artist.id}')">
                        <div class="spotify-search-card-img">
                            ${image ? `<img src="${image}" alt="">` : '<div class="spotify-search-card-placeholder"></div>'}
                        </div>
                        <div class="spotify-search-card-name">${escapeHtml(artist.name)}</div>
                        <div class="spotify-search-card-type">Artist</div>
                    </div>`;
            });
            html += '</div></div>';
        }

        // Albums row
        if (albums.length > 0) {
            html += '<div class="spotify-search-section">';
            html += '<h4 class="spotify-search-section-title">Albums</h4>';
            html += '<div class="spotify-search-row">';
            albums.slice(0, 7).forEach(album => {
                const image = album.images?.[0]?.url || '';
                const artistName = album.artists?.[0]?.name || '';
                html += `
                    <div class="spotify-search-card" onclick="Spotify.openAlbumDetail('${album.id}')">
                        <div class="spotify-search-card-img">
                            ${image ? `<img src="${image}" alt="">` : '<div class="spotify-search-card-placeholder"></div>'}
                        </div>
                        <div class="spotify-search-card-name">${escapeHtml(album.name)}</div>
                        <div class="spotify-search-card-type">${escapeHtml(artistName)}</div>
                    </div>`;
            });
            html += '</div></div>';
        }

        // Playlists row
        if (playlists.length > 0) {
            html += '<div class="spotify-search-section">';
            html += '<h4 class="spotify-search-section-title">Playlists</h4>';
            html += '<div class="spotify-search-row">';
            playlists.slice(0, 7).forEach(playlist => {
                const image = playlist.images?.[0]?.url || '';
                html += `
                    <div class="spotify-search-card" onclick="Spotify.openPlaylist('${playlist.id}', '${escapeHtml(playlist.name).replace(/'/g, "\\'")}')">
                        <div class="spotify-search-card-img">
                            ${image ? `<img src="${image}" alt="">` : '<div class="spotify-search-card-placeholder"></div>'}
                        </div>
                        <div class="spotify-search-card-name">${escapeHtml(playlist.name)}</div>
                        <div class="spotify-search-card-type">By ${escapeHtml(playlist.owner?.display_name || 'Spotify')}</div>
                    </div>`;
            });
            html += '</div></div>';
        }

        html += '</div>';

        if (html === '<div class="spotify-search-results-container"></div>') {
            html = '<div class="spotify-no-results">No results found</div>';
        }

        container.innerHTML = html;
    }

    function renderTopResultCard(topResult) {
        const { type, data } = topResult;
        let image = '';
        let name = '';
        let subtitle = '';
        let onclick = '';

        if (type === 'artist') {
            image = data.images?.[0]?.url || '';
            name = data.name;
            subtitle = 'Artist';
            onclick = `Spotify.openArtistDetail('${data.id}')`;
        } else if (type === 'album') {
            image = data.images?.[0]?.url || '';
            name = data.name;
            subtitle = data.artists?.[0]?.name || 'Album';
            onclick = `Spotify.openAlbumDetail('${data.id}')`;
        } else if (type === 'track') {
            image = data.album?.images?.[0]?.url || '';
            name = data.name;
            subtitle = data.artists?.map(a => a.name).join(', ') || '';
            onclick = `Spotify.playUri('${data.uri}')`;
        }

        const isRound = type === 'artist';

        return `
            <div class="spotify-top-result-card ${isRound ? 'round' : ''}" onclick="${onclick}">
                <div class="spotify-top-result-img ${isRound ? 'round' : ''}">
                    ${image ? `<img src="${image}" alt="">` : '<div class="spotify-top-result-placeholder"></div>'}
                </div>
                <div class="spotify-top-result-name">${escapeHtml(name)}</div>
                <div class="spotify-top-result-type">${escapeHtml(subtitle)}</div>
            </div>`;
    }

    // ===== Helper Functions =====

    function formatTime(ms) {
        const seconds = Math.floor(ms / 1000);
        const mins = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    }

    function formatNumber(num) {
        if (num >= 1000000) {
            return (num / 1000000).toFixed(1) + 'M';
        } else if (num >= 1000) {
            return (num / 1000).toFixed(1) + 'K';
        }
        return num.toString();
    }

    function getDeviceIcon(type) {
        switch (type.toLowerCase()) {
            case 'computer': return '💻';
            case 'smartphone': return '📱';
            case 'speaker': return '🔊';
            case 'tv': return '📺';
            default: return '🎵';
        }
    }

    // ===== Progress Updates =====

    function startMiniPlayerProgress() {
        if (miniPlayerProgressInterval) return;
        miniPlayerProgressInterval = setInterval(() => {
            if (spotifyPlayback && spotifyPlayback.is_playing) {
                spotifyPlayback.progress_ms += 1000;
                updateMiniPlayerProgress();
                // Also update modal progress bar if modal is open
                const modal = document.getElementById('spotifyModal');
                if (modal && modal.classList.contains('active')) {
                    updateProgressBar();
                }
            }
        }, 1000);
    }

    // ===== Initialization =====

    function init() {
        checkSpotifyStatus();
        setInterval(loadSpotifyPlayback, 5000);
        startMiniPlayerProgress();

        // Close modals when clicking outside (on the backdrop)
        document.querySelectorAll('.modal').forEach(modal => {
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    modal.classList.remove('active');
                }
            });
        });
    }

    // Public API
    return {
        init,
        openModal: openSpotifyModal,
        closeModal: closeSpotifyModal,
        openDeviceModal,
        closeDeviceModal,
        openPlaylist,
        closePlaylistModal,
        openAlbumDetail,
        openArtistDetail,
        openLikedSongs,
        openSectionView,
        openShow,
        goBackFromDetail,
        togglePlayback,
        next,
        previous,
        toggleShuffle,
        toggleRepeat,
        toggleMute,
        onVolumeInput,
        seek,
        playUri,
        playAlbumTrack,
        shufflePlay,
        playLikedSongs,
        shuffleLikedSongs,
        transferToDevice,
        toggleAlbumSaved,
        toggleArtistFollow,
        switchBrowseTab,
        setLibraryFilter,
        setLibrarySort,
        setLibrarySearch,
        performSearch,
        toggleSearchClear,
        clearSearch,
        handleMiniPlayerClick,
        loadDevices: loadSpotifyDevices,
        renderDeviceModalContent,
        // Expose for external access
        getPlayback: () => spotifyPlayback
    };
})();

// Initialize on DOMContentLoaded
document.addEventListener('DOMContentLoaded', function() {
    Spotify.init();
});

// Global function aliases for onclick handlers in HTML
function openSpotifyModal() { Spotify.openModal(); }
function closeSpotifyModal() { Spotify.closeModal(); }
function openSpotifyDeviceModal() { Spotify.openDeviceModal(); }
function closeSpotifyDeviceModal() { Spotify.closeDeviceModal(); }
function closeSpotifyPlaylistModal() { Spotify.closePlaylistModal(); }
