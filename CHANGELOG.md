# Changelog

All notable changes to the Home Control project will be documented in this file.

## [1.8.0] - 2026-01-04

### Server/Backend

#### Added
- **Fan Entity Endpoint** (`93a393d`)
  - GET `/api/ha/fans` - List all fan entities with on/off state
  - Sorted by friendly name

- **Climate Entity Endpoint** (`93a393d`)
  - GET `/api/ha/climate` - List climate entities with fan mode control
  - Returns entity_id, friendly_name, state, fan_mode, and available fan_modes
  - Filters out Tesla HVAC entities
  - Only includes climates with fan mode support

- **Filtered Automations Endpoint** (`93a393d`)
  - GET `/api/ha/automations/filtered` - Button/switch triggered automations
  - Returns trigger entities that activate automations
  - Supports state triggers, device triggers, and event triggers

- **Home Assistant Registry** (`93a393d`)
  - New registry module for entity browsing and search
  - GET `/api/ha/registry/domains` - List all domains with entity counts
  - GET `/api/ha/registry/domain/{domain}` - Entities in a domain
  - GET `/api/ha/registry/entity/{entityId}` - Single entity details
  - GET `/api/ha/search` - Search entities by query
  - POST `/api/ha/registry/refresh` - Force registry refresh

- **Home Assistant Icon** (`93a393d`)
  - Added home-assistant.svg icon for drawer menu

### Android App

#### Added
- **Only Fans Section** (`93a393d`)
  - New section on Home screen showing all fan entities
  - On/off toggle switches for each fan
  - Climate fan mode control (e.g., Ecobee Auto/On)
  - Segmented buttons for selecting fan modes
  - Combined regular fans and climate fans in single section

- **Screensaver Tomorrow's Events** (`93a393d`)
  - Shows tomorrow's events after 9pm alongside today's events
  - Separate "Today's Events" and "Tomorrow's Events" headers
  - Includes tomorrow's holidays when applicable

- **Home Assistant Icon** (`93a393d`)
  - Custom HA icon in navigation drawer

#### Changed
- **Screensaver Event Limit Removed** (`93a393d`)
  - Previously limited to 10 events, now shows all events
  - Removed "+N more" indicator

- **Home Screen Loading** (`93a393d`)
  - Parallel loading of automations, fans, and climates
  - Improved loading state handling

## [1.7.0] - 2026-01-02

### Server/Backend

#### Added
- **Xbox Home Assistant Integration** (`d83bea9`)
  - Replaced SmartGlass REST with Home Assistant media_player/remote control
  - GET/POST `/api/entertainment/xbox/{name}/state` - Xbox power and state via HA
  - POST `/api/entertainment/xbox/{name}/input` - Button presses via HA remote
  - POST `/api/entertainment/xbox/{name}/media` - Media commands (play/pause/stop)
  - POST `/api/entertainment/xbox/{name}/launch` - Launch apps/games
  - Optional `now_playing_sensor` for rich game info (genre, developer, progress, gamerscore)
  - Config format: `name:media_player_id:remote_id[:now_playing_sensor]`

- **PS5 Home Assistant Integration** (`cdf32b3`)
  - Power control via PS5-MQTT switch entity
  - PSN integration for game info, trophies, online status
  - GET `/api/entertainment/ps5/{name}/state` - PS5 state with PSN data
  - POST `/api/entertainment/ps5/{name}/power` - Power on/off via HA switch
  - Fetches: current game, game art, trophy counts, online ID, avatar
  - Config format: `name:power_switch:psn_account_id`

- **Sony Device Control Endpoints** (`4270a79`, `9c7c6a3`)
  - POST `/api/entertainment/sony/{name}/command` - Send IRCC commands
  - POST `/api/entertainment/sony/{name}/app` - Launch apps by URI
  - GET `/api/entertainment/sony/{name}/apps` - List installed applications
  - GET `/api/entertainment/sony/{name}/system` - System info and settings
  - Background polling with configurable interval
  - State caching with TTL for reduced API calls
  - Support for both TVs (with PSK) and soundbars (open API)

- **Shield Device Enhancements** (`1b1fc9e`, `182bfd2`, `1931405`)
  - GET `/api/entertainment/shield/{name}/apps` - List installed apps
  - POST `/api/entertainment/shield/{name}/app` - Launch app by package name
  - POST `/api/entertainment/shield/{name}/text` - Send text input
  - POST `/api/entertainment/shield/{name}/screenshot` - Take screenshot
  - POST `/api/entertainment/shield/{name}/reboot` - Reboot device
  - Expanded ShieldApps map with 20+ streaming service packages
  - PackageToName mapping for friendly app display names
  - ADB daemon startup for faster command execution

- **Streaming Service Icons** (`fc8a3a7`)
  - Added SVG icons: Netflix, Hulu, Disney+, Prime Video, Apple TV, HBO Max
  - Added SVG icons: Peacock, YouTube TV, Twitch, Steam, RetroArch
  - Added SVG icons: Xbox Game Pass, OpenVPN

#### Changed
- **Weather API Refresh Intervals** (`5a1189b`)
  - Current conditions: 30 min → 15 min refresh
  - Forecasts: optimized for ~4,320 calls/month within free tier

- **Home Assistant Client** (`d83bea9`)
  - Added `GetBaseURL()` method for constructing entity picture URLs
  - Added `CallServiceWithData()` for custom service payloads

### Android App

#### Added
- **RemoteTemplate Composable** (`67e3235`)
  - Reusable remote control UI component
  - D-pad navigation with center select button
  - Volume/channel rockers with mute toggle
  - Customizable button callbacks for different devices

- **Xbox Controller Panel** (`d83bea9`)
  - Full Xbox control UI with game art display
  - D-pad, A/B/X/Y, menu/view, guide buttons
  - Media controls for video playback
  - Game info display: title, genre, developer, progress
  - Gamerscore display

- **PS5 Controller Panel** (`cdf32b3`)
  - Power toggle with status display
  - Large game art display (fills panel when playing)
  - PS5 logo display when in standby
  - Current game title and online status
  - Trophy summary (platinum, gold, silver, bronze)
  - PSN ID and avatar display

- **Sony TV/Soundbar Control** (`4270a79`, `9c7c6a3`)
  - Full remote control with number pad
  - Input source selection grid
  - App launcher with streaming service icons
  - Volume control with mute
  - Sound settings panel for soundbars
  - Power and system controls

- **Shield App Launcher** (`1b1fc9e`, `182bfd2`)
  - Grid of installed apps with custom icons
  - Quick launch for popular streaming services
  - App filtering (user vs system apps)

- **Sofabaton Integration** (`1931405`)
  - IR blaster control for legacy devices
  - HDMI input management
  - Activity-based automation

- **Streaming Service Icons** (`fc8a3a7`)
  - Vector drawable icons for 12+ streaming services
  - Consistent styling across app launcher UI

- **Holiday Display** (`bd229b1`)
  - Calendar shows holidays on date cells
  - Screensaver displays current holiday

#### Changed
- **Entertainment Screen Layout** (`67e3235` - `cdf32b3`)
  - Tab-based navigation: Shield, Sony, Xbox, PS5
  - Two-column layout: controls left, content right
  - Consistent styling across all device panels

- **Date Change Detection** (`bd229b1`)
  - Calendar auto-refreshes on date change
  - Screensaver updates when day changes at midnight

---

## [Unreleased]

### Server/Backend

#### Added
- **Spotify Queue Endpoint** (`98d00b2`)
  - GET `/api/spotify/queue` - Retrieve user's playback queue
  - POST `/api/spotify/queue` - Add track to queue

- **Spotify New Releases Endpoint** (`98d00b2`)
  - GET `/api/spotify/browse/new-releases` - Browse new album releases

- **Spotify Track Save Status Endpoint** (`98d00b2`)
  - GET `/api/spotify/tracks/saved?ids=...` - Batch check if tracks are saved
  - PUT `/api/spotify/track/{id}/save` - Save track to library
  - DELETE `/api/spotify/track/{id}/save` - Remove track from library

- **Entertainment Room Light Population** (`8eea0a4`)
  - Added `Channels` and `LightServices` fields to `v2EntertainmentConfig` struct
  - Entertainment areas now correctly report their associated lights
  - Enables sync detection by checking which lights are in streaming entertainment areas

- **Hue SSE Event Stream** (`bba5244`)
  - Real-time event stream from Hue bridge via `/eventstream/clip/v2`
  - New `EventStream` client in `internal/hue/eventstream.go`
  - SSE endpoint `/api/hue/events` for clients to receive live updates
  - Exponential backoff reconnection (5s to 60s max)
  - Eliminates need for polling when SSE is connected

- **Active Scene Indicator** (`bba5244`)
  - Scene `Active` field populated from V2 `status.active`
  - Active when status is `static` or `dynamic_palette`

- **Web Frontend SSE Integration** (`bba5244`)
  - Replaced 1-second polling with SSE connection in `static/js/hue.js`
  - Debounced reloads (1s) to prevent API flooding
  - Fallback to 10-second polling when SSE disconnects
  - Active scene highlighting in scene modal with orange border and checkmark

#### Changed
- **Hue Bridge V2 API Migration** (`35c1e44`)
  - Complete rewrite of Hue client to use V2 API (`/clip/v2/resource/`)
  - Authentication moved from URL path to `hue-application-key` header
  - Resources now use UUIDs instead of numeric IDs
  - Brightness values normalized to 0-100% (V1 used 1-254)
  - Room control via `grouped_light` resources instead of `/groups/{id}`
  - Scene activation via PUT to `/scene/{uuid}` with `{"recall": {"action": "active"}}`
  - New V2 data types: `v2Light`, `v2Room`, `v2Zone`, `v2GroupedLight`, `v2Scene`, `v2Device`
  - Entertainment deactivation updated to use V2 API endpoint

- **Sync Box Single API Call Optimization** (`2671d25`)
  - New `GetStatus()` method retrieves device, execution, hue, and HDMI state in one request
  - Uses `/api/v1` endpoint to fetch all state at once (as recommended by Philips docs)
  - Reduces API calls from 4 to 1 per status check
  - Added `FullStatus` struct for combined response parsing

#### Removed
- **Deprecated Spotify Endpoints** (Spotify API deprecation November 2024)
  - Removed `/api/spotify/recommendations` endpoint
  - Removed `/api/spotify/browse/featured` endpoint
  - Removed `/api/spotify/browse/categories` endpoint
  - Removed `/api/spotify/browse/categories/{id}/playlists` endpoint
  - These endpoints now return 404 from Spotify's API

### Android App

#### Added
- **Adaptive Brightness** (`2b2cea3`)
  - Automatic screen brightness based on ambient light sensor
  - Logarithmic curve for natural perception (10% min to 100% max)
  - Smooth 500ms fade transitions using ValueAnimator
  - Toggle in Settings > Display > Adaptive Brightness
  - Broadcasts brightness changes to NativeActivity for smooth application

- **Proximity-Based Display Timeout** (`2b2cea3`)
  - Configurable timeout: 1, 5, 10, 15, 30 min, 1hr, 2hr
  - Screen turns off when no one is detected nearby
  - Touch resets the proximity timer
  - Proximity wakes screen to screensaver (not main UI)
  - Only touch dismisses screensaver

- **Display Settings UI** (`2b2cea3`)
  - New "Display" section in Settings screen
  - Display Off Timeout picker with chip-based selection
  - Adaptive Brightness toggle
  - Removed deprecated "Keep Screen On" setting

- **OTA Update System**
  - Automatic update checks every 15 minutes via WorkManager
  - Manual update check in Settings > About & Updates
  - Downloads APK from MDM server and installs silently
  - Version comparison using semantic versioning
  - Progress indicator during download
  - Uses JSON manifest at `https://mdm.mscrnt.com/files/homecontrol-latest.json`
  - Auto-restart app after OTA update via MY_PACKAGE_REPLACED receiver

#### Changed
- Increased screensaver slide interval from 30 to 90 seconds

- **Spotify Queue View** (`98d00b2`)
  - View current playback queue from Now Playing panel
  - Add tracks to queue from track lists
  - Queue accessible via queue icon in playback controls

- **New Releases Section** (`98d00b2`)
  - Browse new album releases on Spotify Home tab
  - Displayed as horizontal scrollable section with album art
  - "Show all" expands to full grid view

- **Track Save/Remove Functionality** (`98d00b2`)
  - Heart button on tracks to save/remove from library
  - Batch check for saved status when viewing track lists
  - `savedTracks: Map<String, Boolean>` tracks UI state
  - Works in album detail, artist detail, playlist detail, and liked songs views

- **New Room Icons** (`8eea0a4`)
  - Custom vector drawable icons for room types: patio (garden bench), bar, desk
  - Lightbulb on/off icons for individual light state indication
  - Color palette icon for creative spaces
  - Icons auto-selected based on room class or name matching

- **Hue Sync Detection & Warning Banner** (`8eea0a4`)
  - Added `streamingActive` field to `HueRoom` model
  - Detects syncing lights by checking Entertainment areas with active streaming
  - Purple sync warning banner appears when room lights are syncing
  - "Stop Sync" button to deactivate all active sync boxes
  - Syncing lights show movie 🎬 icon instead of lightbulb
  - Purple styling for syncing lights vs orange for regular on lights

- **Screensaver Calendar Events** (`1123b18`)
  - Today's events displayed in top left corner of screensaver
  - Shows event time (or "All Day") with event title and color indicator
  - Respects 12/24 hour time format setting
  - Limited to 5 events with "+N more" indicator

- **New App Icon** (`1123b18`, `50ff262`)
  - Custom launcher icon with warm orange/coral calendar design
  - Home icon integrated into calendar grid
  - Generated all mipmap sizes (mdpi through xxxhdpi)
  - Adaptive icon foreground properly sized for Android's safe zone

- **Navigation Drawer Icons** (`857f297`)
  - Added Philips Hue lightbulb icon (red) for Hue screen
  - Added Amcrest camera icon for Cameras screen
  - Added photo-film-music icon (purple) for Entertainment screen
  - Added house-signal icon for Home screen

- **Hue SSE Real-time Updates** (`bba5244`)
  - New `HueEventClient` for SSE connection to server
  - Uses OkHttp SSE library (`okhttp-sse:4.12.0`)
  - Auto-reconnect with exponential backoff (1s to 30s max)
  - Event types: `LightUpdate`, `GroupUpdate`, `SceneUpdate`, `DataChanged`
  - Falls back to polling when SSE disconnects

- **Active Scene Indicator** (`bba5244`)
  - Scenes section highlights currently active scene
  - Orange border and checkmark on active scene
  - `active` field added to `HueScene` model

#### Changed
- **App Name** (`5145223`)
  - Renamed app from "HCC" to "Home Control"

- **ADB Monitoring Simplified** (`5145223`)
  - Changed from active reconfiguration to passive monitoring
  - Checks if port 5555 is listening every 5 minutes
  - Logs warning when ADB stops working instead of attempting to fix
  - Removed non-functional shell commands (setprop, stop/start adbd)
  - Note: Run `adb tcpip 5555` externally to enable ADB over WiFi

- **Screen Wake Behavior** (`2b2cea3`)
  - Proximity now wakes screen to screensaver, not main UI
  - Only touch dismisses the screensaver
  - Touch resets both idle timer and proximity timer
  - Wake-to-screensaver broadcast from SensorService to NativeActivity

- **HueScreen UI Improvements** (`8eea0a4`)
  - Lights grid changed from adaptive width to fixed 3 columns
  - Orange border outline on lights that are on (but not syncing)
  - Smaller dropdowns for HDMI Input and Entertainment Area selectors
  - Reduced spacing between power button and brightness slider
  - Large room icons (120dp) displayed above room name

- **HueScreen Tab Navigation** (`397c309`)
  - Refactored to use horizontal tab bar for room navigation
  - Each room displayed as a tab at the top of the screen
  - Entertainment Areas tab added when sync boxes are configured
  - Improved room content layout with power button, brightness control, and scenes
  - Better organization of sync box controls with mode, brightness, and HDMI input selectors

- **Removed Pull-to-Refresh**
  - Removed PullToRefreshContainer from HueScreen and HomeScreen
  - Auto-polling (5 second interval) provides automatic data refresh
  - Eliminates visual glitch with persistent refresh indicator circle

- **UI Branding** (`1123b18`)
  - Renamed "Smart Home" to "Home Control" in navigation drawer
  - "Media" renamed to "Entertainment" with new icon

- **Screensaver Time Format** (`1123b18`)
  - Clock now respects use24HourFormat setting
  - 12-hour format shows AM/PM indicator

- **Weather Overlay** (`857f297`)
  - Improved weather overlay layout in screensaver
  - Better icon display for weather conditions

- **Removed KioskActivity** (`d6c5cdf`)
  - Fully transitioned to NativeActivity as the primary UI
  - Removed WebView-based kiosk mode in favor of native Jetpack Compose UI
  - Moved broadcast constants (ACTION_PROXIMITY, EXTRA_NEAR) from KioskActivity to SensorService
  - Updated CommandServer to return "not supported" for reload/exit_kiosk endpoints
  - MainActivity now launches NativeActivity instead of KioskActivity

- **Proximity Detection for Native UI** (`d6c5cdf`)
  - Added BroadcastReceiver in NativeActivity to listen for proximity events
  - Screensaver now dismisses when proximity sensor detects presence
  - Added ProximityCallback interface for clean event propagation to Compose UI

- **Keep Screen On Setting** (`d6c5cdf`)
  - NativeActivity now observes keepScreenOn setting from preferences
  - Applies FLAG_KEEP_SCREEN_ON dynamically based on user setting
  - Disabled SensorService idle check to prevent conflict with FLAG_KEEP_SCREEN_ON

- **WebSocket Connection** (`d6c5cdf`)
  - WebSocketClient.connect() now called on app startup in HomeControlApp
  - Ensures real-time events (doorbell, etc.) work immediately after app launch

#### Fixed
- **Spotify Launch Logic**
  - Changed `launchSpotifyQuickly()` to check local process FIRST instead of API
  - Prevents false positives from other devices showing as "active"
  - Only checks API for active devices after confirming Spotify isn't running locally

#### Removed
- **KioskActivity** - Deleted KioskActivity.kt and activity_kiosk.xml
- **WebView Mode** - No longer supported; all UI is now native Compose
- **Browse Tab** - Removed from Spotify screen (now Home and Library tabs only)
- **Deprecated Spotify UI Sections** (Spotify API deprecation November 2024)
  - Removed "Made For You (Recommendations)" section
  - Removed "Featured Playlists" section
  - Removed `FEATURED_PLAYLISTS` and `RECOMMENDATIONS` from `SectionType` enum
  - Removed `featuredPlaylists` and `recommendations` fields from `SpotifyUiState`

---

## [1.6.1] - 2025-01-01

### Android App

#### Fixed
- **Weather 10-Day Forecast** - Filter stale data showing previous day
- **Weather Modal Refresh** - Fetch fresh data when opening weather modal
- **Version Display** - Nav drawer now shows correct app version dynamically

#### Changed
- **OTA Manifest URL** - Now fetches from GitHub instead of MDM server
- **Screensaver Interval** - Increased slide interval from 30 to 90 seconds

#### Added
- **Auto-Restart After OTA** - App automatically restarts via MY_PACKAGE_REPLACED receiver

---

## [1.3.0] - 2025-12-30

### Server/Backend

#### Added
- Google Weather API migration with tiered caching
- Screensaver photo slideshow integration
- Enhanced calendar event modal

#### Changed
- Spotify launch logic prioritizes local process check
- ManagedAppsManager version comparison improvements

### Android App

#### Added
- **Screensaver Screen** (`f9b0e18`)
  - Photo slideshow with Google Drive integration
  - Background music via Spotify integration
  - Configurable idle timeout for screensaver activation
  - Smooth crossfade transitions between photos
  - Photo sync with local caching for offline display

- **Photo Sync System** (`d20d098`)
  - PhotoSyncManager for downloading and caching Google Drive photos
  - PhotoSyncWorker for periodic background sync via WorkManager
  - Hourly automatic sync with manual trigger option
  - Efficient disk caching with cache size tracking

- **Weather Enhancements** (`d20d098`, `af48079`)
  - Weather data disk caching for offline fallback
  - Improved weather widget with better layout and styling
  - Enhanced hourly and daily forecast display

#### Changed
- **Google Weather API Migration** (`af48079`)
  - Migrated from OpenWeatherMap to Google Weather API
  - Tiered caching strategy to stay within 1000 free calls/month:
    - Current conditions: refreshed hourly (~720/month)
    - Daily forecast: refreshed every 12 hours (~60/month)
    - Hourly forecast: refreshed every 6 hours (~120/month)
  - Updated weather client implementation with new API response handling

- **Dark Theme Support** (`d8dd22d`)
  - Refactored theme handling for proper dark mode support
  - Updated NativeActivity theme configuration

- **Calendar Improvements** (`d20d098`)
  - Event modal refactored to full-screen overlay for better DPI scaling
  - Improved MonthView with better text sizes and colors
  - Enhanced WeekView with visual distinction for today and holidays
  - Separate date and time fields for all-day event handling

#### Fixed
- **ManagedAppsManager** (`2d8b94b`)
  - Now compares versions BEFORE downloading APKs (was downloading unnecessarily)
  - Fixed expected version handling with explicit version strings in config
  - Removed APK parsing that caused AconfigFlags errors

- **Spotify Launch Logic** (`2d8b94b`)
  - Improved `launchSpotifyQuickly()` to check local process first
  - Prevents false positives from other devices showing as "active"
  - Better logging for debugging launch behavior

- **Screensaver Stability** (`d20d098`)
  - Improved layout to prevent accidental dismissals
  - Better error handling for photo loading

---

## [1.2.0] - 2025-12-28

### Server/Backend

#### Added
- Spotify OAuth integration with full playback control API
- Google Calendar and Tasks API endpoints
- Google Drive photos API for screensaver backgrounds

### Android App

#### Added
- **Phase 6 Spotify Screen** (`57a9d0b`)
  - Full Spotify playback control UI with two-column layout
  - Now Playing panel with album art, track info, progress bar
  - Playback controls: play/pause, next, previous, seek, volume slider
  - Shuffle and repeat toggle with visual state indicators
  - Device picker dialog for transferring playback between devices
  - Browse tab with sections: Recently Played, Top Artists, Your Playlists, Jump Back In
  - Library tab with filters: All, Playlists, Albums, Artists, Liked Songs
  - Album detail view with track listing, play/shuffle buttons, save to library
  - Artist detail view with top tracks, discography, follow button
  - Playlist detail view with full track listing
  - "Show all" functionality for home sections with full-screen grid views
  - Search with global Spotify search and local library filtering
  - MiniSpotifyPlayer component in drawer with compact controls
  - Spotify branding with logo and green accent color throughout
  - SpotifyTopTracksResponse type for correct API response handling

- **Phase 5 Calendar Screen** (`45a1a69`)
  - Full calendar implementation with Day, Week, and Month views
  - Swipe gesture navigation between time periods
  - Event creation/editing modal with calendar selection
  - Google Calendar integration via REST API
  - Tasks panel with Google Tasks support
  - US holidays display (federal, religious, observances)
  - Moon phase calculations and display
  - Weather widget in calendar header with border outline
  - Expanded weather modal matching web UI design:
    - Current conditions (temp, feels like, humidity, wind, clouds)
    - Sunrise/sunset times with moon phase
    - Hourly forecast (TODAY section)
    - 5-day forecast with precipitation percentages
  - Current time indicator in day view
  - Event color coding from Google Calendar

#### Fixed
- Weather model updated to match actual API response format
- Icon mapping for weather conditions (sun, moon, clouds, etc.)

---

## [1.1.0] - 2025-12-27

### Server/Backend

#### Added
- Philips Hue bridge control (lights, rooms, scenes)
- Hue Sync Box entertainment control
- Home Assistant REST API integration
- Camera snapshots and Frigate integration
- MQTT client for doorbell events
- WebSocket hub for real-time broadcasts
- OpenWeatherMap API client

### Android App

#### Added
- **Phase 1 Foundation** (`457f2a2`)
  - Jetpack Compose UI framework
  - Hilt dependency injection
  - Retrofit + OkHttp networking
  - Kotlin Serialization for JSON
  - Coil for image loading
  - Navigation Compose
  - Material3 theming (light/dark)
  - HomeControlApp with @HiltAndroidApp
  - NativeActivity with bottom navigation
  - HomeControlApi with 80+ endpoints
  - Data models: Entity, Hue, Spotify, Calendar, Weather, Entertainment
  - Repository interfaces for all domains
- **Phase 2 Data Layer** (`4afb120`)
  - WebSocketClient with auto-reconnect and exponential backoff
  - Event handling for doorbell and proximity_wake events
  - SensorServiceBridge interface and implementation
  - Push-to-talk endpoint for camera audio
  - SensorService integration with bridge commands
- **Phase 3 UI Screens** (`af647fc`)
  - HomeScreen with GroupCard and EntityCard components
  - HueScreen with room controls, brightness sliders, scene selection
  - Sync Box controls with mode, brightness, and input selection
  - SettingsScreen with server URL, theme, and app mode configuration
  - SettingsRepository with DataStore persistence
  - Glassmorphism theme matching web UI (dark navy, tan text, accent colors)
  - Fixed JSON parsing for Entity, HueRoom, HueLight, SyncBoxStatus models

#### Changed
- Upgraded to Android 15 (API 35)
- Upgraded AGP from 8.2.0 to 8.10.1
- Upgraded Kotlin from 1.9.20 to 2.0.21
- Upgraded Hilt from 2.48 to 2.56.2
- Upgraded KSP from 1.9.20-1.0.14 to 2.0.21-1.0.28

#### Fixed
- KioskActivity: Replaced deprecated `onBackPressed()` with `OnBackPressedCallback`
- Theme: Added API level check for deprecated `statusBarColor`/`navigationBarColor`
- InstallResultReceiver: Added API 33+ version of `getParcelableExtra`
- CommandServer: Fixed deprecated `parms` to `parameters`
- Removed deprecated `databaseEnabled` WebView setting

#### Known Issues
- Hilt generates code using deprecated `applicationContextModule()` API
  - Tracked in [#8](https://github.com/mscrnt/home_control/issues/8)
  - Upstream fix merged, awaiting next Dagger release

---

## [1.0.0] - Initial Release

### Server/Backend

#### Added
- Go server with chi router
- HTML templates with glass-morphism UI
- Home Assistant entity control
- Google OAuth flow with token persistence
- Environment-based configuration

### Android App

#### Added
- Kiosk mode with WebView
- Sensor service (proximity, light)
- Command server (HTTP API)
- Device admin/owner support
- Bloatware management
- Boot receiver for auto-start
