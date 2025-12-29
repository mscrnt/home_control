# Changelog

All notable changes to the Android sensor app will be documented in this file.

## [Unreleased]

## [1.2.0] - 2025-12-28

### Added
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

### Fixed
- Weather model updated to match actual API response format
- Icon mapping for weather conditions (sun, moon, clouds, etc.)

## [1.1.0] - 2025-12-27

### Added
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

### Changed
- Upgraded to Android 15 (API 35)
- Upgraded AGP from 8.2.0 to 8.10.1
- Upgraded Kotlin from 1.9.20 to 2.0.21
- Upgraded Hilt from 2.48 to 2.56.2
- Upgraded KSP from 1.9.20-1.0.14 to 2.0.21-1.0.28

### Fixed
- KioskActivity: Replaced deprecated `onBackPressed()` with `OnBackPressedCallback`
- Theme: Added API level check for deprecated `statusBarColor`/`navigationBarColor`
- InstallResultReceiver: Added API 33+ version of `getParcelableExtra`
- CommandServer: Fixed deprecated `parms` to `parameters`
- Removed deprecated `databaseEnabled` WebView setting

### Known Issues
- Hilt generates code using deprecated `applicationContextModule()` API
  - Tracked in [#8](https://github.com/mscrnt/home_control/issues/8)
  - Upstream fix merged, awaiting next Dagger release

## [1.0.0] - Initial Release

### Added
- Kiosk mode with WebView
- Sensor service (proximity, light)
- Command server (HTTP API)
- Device admin/owner support
- Bloatware management
- Boot receiver for auto-start
