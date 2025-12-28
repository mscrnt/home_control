# Changelog

All notable changes to the Android sensor app will be documented in this file.

## [Unreleased]

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
