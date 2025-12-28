# Android Native Mode Roadmap

This roadmap tracks the implementation of native Jetpack Compose UI for the Home Control Android app.

**Project Board**: [Android Native Mode](https://github.com/users/mscrnt/projects/3)

## Quick Links

| Phase | Issue | Status |
|-------|-------|--------|
| Phase 1: Foundation | [#1](https://github.com/mscrnt/home_control/issues/1) | **Complete** |
| Phase 2: Data Layer | [#2](https://github.com/mscrnt/home_control/issues/2) | Not Started |
| Phase 3: Home + Hue | [#3](https://github.com/mscrnt/home_control/issues/3) | Not Started |
| Phase 4: Spotify | [#4](https://github.com/mscrnt/home_control/issues/4) | Not Started |
| Phase 5: Calendar | [#5](https://github.com/mscrnt/home_control/issues/5) | Not Started |
| Phase 6: Supporting | [#6](https://github.com/mscrnt/home_control/issues/6) | Not Started |
| Phase 7: Integration | [#7](https://github.com/mscrnt/home_control/issues/7) | Not Started |

---

## Phase 1: Foundation

**Goal**: Set up Jetpack Compose, Hilt DI, Retrofit, and basic app structure.

### Dependencies
- [ ] Jetpack Compose
- [ ] Hilt
- [ ] Retrofit + OkHttp
- [ ] Kotlin Serialization
- [ ] Coil
- [ ] Navigation Compose

### Files to Create
- [ ] `HomeControlApp.kt` - Hilt Application
- [ ] `di/AppModule.kt` - Provides Retrofit, OkHttp
- [ ] `di/RepositoryModule.kt` - Binds repositories
- [ ] `data/api/HomeControlApi.kt` - Retrofit interface
- [ ] `data/model/*.kt` - Data classes (6 files)
- [ ] `ui/theme/*.kt` - Theme files (4 files)
- [ ] `NativeActivity.kt` - Compose host

---

## Phase 2: Data Layer

**Goal**: Implement repositories, WebSocket client, and sensor service bridge.

### Files to Create
- [ ] `data/api/WebSocketClient.kt`
- [ ] `data/repository/EntityRepository.kt`
- [ ] `data/repository/HueRepository.kt`
- [ ] `data/repository/SpotifyRepository.kt`
- [ ] `data/repository/CalendarRepository.kt`
- [ ] `data/repository/CameraRepository.kt`
- [ ] `data/repository/EntertainmentRepository.kt`
- [ ] `data/repository/WeatherRepository.kt`
- [ ] `service/SensorServiceBridge.kt`
- [ ] `service/SensorServiceBridgeImpl.kt`

---

## Phase 3: Home + Hue Screens

**Goal**: Build home dashboard and Hue lights control.

### Components
- [ ] `ui/components/GroupCard.kt`
- [ ] `ui/components/EntityCard.kt`
- [ ] `ui/components/LightSlider.kt`
- [ ] `ui/components/EntityModal.kt`
- [ ] `ui/components/ClimateControl.kt`
- [ ] `ui/components/LoadingIndicator.kt`
- [ ] `ui/components/ErrorState.kt`

### Screens
- [ ] `ui/screens/home/HomeScreen.kt`
- [ ] `ui/screens/home/HomeViewModel.kt`
- [ ] `ui/screens/hue/HueScreen.kt`
- [ ] `ui/screens/hue/HueViewModel.kt`

### Navigation
- [ ] `ui/navigation/NavGraph.kt`
- [ ] Bottom navigation bar

---

## Phase 4: Spotify Screen

**Goal**: Full Spotify control with browsing and library.

### Components
- [ ] `ui/components/PlaybackControls.kt`
- [ ] `ui/components/ProgressBar.kt`
- [ ] `ui/components/AlbumArt.kt`
- [ ] `ui/components/DevicePicker.kt`
- [ ] `ui/components/TrackItem.kt`
- [ ] `ui/components/PlaylistCard.kt`

### Screens
- [ ] `ui/screens/spotify/SpotifyScreen.kt`
- [ ] `ui/screens/spotify/SpotifyViewModel.kt`
- [ ] `ui/screens/spotify/NowPlayingPanel.kt`
- [ ] `ui/screens/spotify/BrowseScreen.kt`
- [ ] `ui/screens/spotify/LibraryScreen.kt`
- [ ] `ui/screens/spotify/PlaylistDetailScreen.kt`
- [ ] `ui/screens/spotify/AlbumDetailScreen.kt`
- [ ] `ui/screens/spotify/ArtistDetailScreen.kt`

---

## Phase 5: Calendar Screen

**Goal**: Calendar with day/week/month views and event management.

### Components
- [ ] `ui/components/WeatherWidget.kt`
- [ ] `ui/components/EventCard.kt`
- [ ] `ui/components/TaskItem.kt`
- [ ] `ui/components/DatePicker.kt`
- [ ] `ui/components/TimePicker.kt`

### Screens
- [ ] `ui/screens/calendar/CalendarScreen.kt`
- [ ] `ui/screens/calendar/CalendarViewModel.kt`
- [ ] `ui/screens/calendar/DayView.kt`
- [ ] `ui/screens/calendar/WeekView.kt`
- [ ] `ui/screens/calendar/MonthView.kt`
- [ ] `ui/screens/calendar/EventDetailScreen.kt`
- [ ] `ui/screens/calendar/EventEditScreen.kt`
- [ ] `ui/screens/calendar/TasksPanel.kt`

---

## Phase 6: Supporting Screens

**Goal**: Cameras, Entertainment, Settings, and Screensaver.

### Cameras
- [ ] `ui/screens/cameras/CamerasScreen.kt`
- [ ] `ui/screens/cameras/CamerasViewModel.kt`
- [ ] Full-screen stream viewer
- [ ] Push-to-talk functionality

### Entertainment
- [ ] `ui/screens/entertainment/EntertainmentScreen.kt`
- [ ] `ui/screens/entertainment/EntertainmentViewModel.kt`
- [ ] Device controls (power, volume, input)

### Settings
- [ ] `ui/screens/settings/SettingsScreen.kt`
- [ ] `ui/screens/settings/SettingsViewModel.kt`
- [ ] Mode switch (WebView/Native)

### Screensaver
- [ ] `ui/screens/screensaver/ScreensaverScreen.kt`
- [ ] `ui/screens/screensaver/ScreensaverViewModel.kt`
- [ ] Clock display
- [ ] Photo slideshow
- [ ] Mini Spotify player

---

## Phase 7: Integration

**Goal**: Connect everything, polish, and test.

### Integration
- [ ] Mode switching in MainActivity
- [ ] SensorService bridge connection
- [ ] Doorbell WebSocket notifications
- [ ] Kiosk exit gesture

### Accelerometer Features
- [ ] Tap-to-wake detection (double-tap)
- [ ] Shake-to-wake detection
- [ ] Orientation change detection (picked up / laid down)
- [ ] Motion-based idle timeout reset

### Polish
- [ ] Loading states
- [ ] Error handling
- [ ] Animations
- [ ] Memory optimization
- [ ] Battery optimization

### Testing
- [ ] Tablet form factor
- [ ] Phone form factor
- [ ] Landscape/portrait
- [ ] All API endpoints
- [ ] WebSocket reconnection

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Unified Android App                     │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────────┐        ┌─────────────────┐        │
│  │   Kiosk Mode    │        │   Native Mode   │        │
│  │   (WebView)     │        │   (Compose UI)  │        │
│  └────────┬────────┘        └────────┬────────┘        │
│           └──────────┬───────────────┘                  │
│  ┌───────────────────┴───────────────────┐             │
│  │         Shared Services Layer          │             │
│  │  - SensorService (proximity, light)    │             │
│  │  - CommandServer (HTTP API)            │             │
│  │  - WakeLock / WiFi management          │             │
│  └────────────────────────────────────────┘             │
└─────────────────────────────────────────────────────────┘
                          ▼
              ┌───────────────────────┐
              │   Go Backend Server   │
              │   REST API + WebSocket│
              └───────────────────────┘
```

## Tech Stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose |
| State | MVI + StateFlow |
| DI | Hilt |
| Network | Retrofit + OkHttp |
| JSON | Kotlin Serialization |
| Images | Coil |
| Real-time | OkHttp WebSocket |

---

## Target Device Profile

**Model**: URAO G140L (14" Tablet)

### Hardware Specs

| Component | Specification |
|-----------|---------------|
| **CPU** | Allwinner A733 (ARM Cortex-A55), 4 cores @ 48 BogoMIPS |
| **RAM** | 6 GB (5.6 GB usable) |
| **Storage** | 256 GB (233 GB usable, 204 GB free) |
| **Display** | 1920 x 1200, 240 dpi, 60Hz |
| **GPU** | PowerVR Rogue (OpenGL ES 3.2) |
| **OS** | Android 15 (API 35), Kernel 6.6.57 |

### Available Sensors

| Sensor | Type | Capabilities |
|--------|------|--------------|
| **Proximity** | stk3x1x | Wake-up, on-change, 1-20Hz |
| **Light** | stk3x1x | On-change, 1-20Hz |
| **Accelerometer** | Msa 3-axis | Continuous, 1-100Hz |
| **Accelerometer (uncalibrated)** | Msa 3-axis | Continuous, 1-100Hz |

### Sensor Capabilities to Implement

- [x] **Proximity wake** - Wake screen when hand approaches (existing)
- [x] **Light sensor** - Auto-brightness adjustment (existing)
- [ ] **Tap-to-wake** - Double-tap accelerometer to wake screen
- [ ] **Shake-to-wake** - Shake detection as alternative wake method
- [ ] **Orientation detection** - Detect tablet picked up/laid down

### Performance Notes

- Dalvik heap size: 768 MB
- No gyroscope (no rotation vector available)
- No HDR support
- GPU profiler support available
