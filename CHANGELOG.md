# Changelog

All notable changes to the Home Control project will be documented in this file.

## [Unreleased]

### Server/Backend

#### Added
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

### Android App

#### Added
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
