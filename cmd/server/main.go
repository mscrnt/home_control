package main

import (
	"context"
	"encoding/json"
	"fmt"
	"html/template"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"home_control/internal/calendar"
	"home_control/internal/camera"
	"home_control/internal/drive"
	"home_control/internal/entertainment"
	"home_control/internal/homeassistant"
	"home_control/internal/hue"
	"home_control/internal/icons"
	"home_control/internal/mqtt"
	"home_control/internal/spotify"

	pahomqtt "github.com/eclipse/paho.mqtt.golang"
	"home_control/internal/syncbox"
	"home_control/internal/tasks"
	"home_control/internal/weather"
	"home_control/internal/websocket"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/joho/godotenv"
)

var pageTemplates map[string]*template.Template
var templateFuncMap template.FuncMap

// devMode enables hot-reloading of templates (set via DEV_MODE env var)
var devMode = os.Getenv("DEV_MODE") == "true"

// getTemplate returns a template, re-parsing in dev mode for hot reload
func getTemplate(page string) *template.Template {
	if devMode {
		t, err := template.New("").Funcs(templateFuncMap).ParseFiles(
			filepath.Join("templates", "base.html"),
			filepath.Join("templates", page+".html"),
		)
		if err != nil {
			log.Printf("Template parse error for %s: %v", page, err)
			return pageTemplates[page] // fallback to cached
		}
		return t
	}
	return pageTemplates[page]
}

// quietPaths are endpoints that get polled frequently and shouldn't spam logs
var quietPaths = map[string]bool{
	"/api/hue/rooms":        true,
	"/api/ha/states":        true,
	"/api/entities":         true,
	"/api/syncbox":          true,
	"/api/spotify/playback": true,
	"/api/calendar/events":  true,
}

// quietPrefixes are path prefixes that shouldn't spam logs
var quietPrefixes = []string{
	"/api/syncbox/",
	"/api/tablet/",
}

// ConditionalLogger is a middleware that skips logging for certain paths
func ConditionalLogger(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Check exact matches
		if quietPaths[r.URL.Path] {
			next.ServeHTTP(w, r)
			return
		}
		// Check prefix matches
		for _, prefix := range quietPrefixes {
			if strings.HasPrefix(r.URL.Path, prefix) {
				next.ServeHTTP(w, r)
				return
			}
		}
		middleware.Logger(next).ServeHTTP(w, r)
	})
}

type Config struct {
	Port               string
	BaseURL            string
	HomeAssistantURL   string
	HomeAssistantToken string
	Entities           []string
	GoogleClientID     string
	GoogleClientSecret string
	GoogleCalendars    []string
	GooglePlacesAPIKey  string
	GoogleWeatherAPIKey string
	WeatherLat          float64
	WeatherLon          float64
	Timezone           *time.Location
	// MQTT settings
	MQTTHost           string
	MQTTPort           int
	MQTTUsername       string
	MQTTPassword       string
	MQTTDoorbellTopics []string // Custom doorbell topics (optional)
	// Camera settings
	Cameras        map[string]string // name -> RTSP URL
	FrigateHost    string            // Frigate server URL (optional, for camera proxying)
	DoorbellCamera string            // Camera name for doorbell events (default: front_door)
	// Webhook settings
	WebhookSecret string // Optional secret for webhook authentication
	// Philips Hue settings
	HueBridgeIP  string
	HueUsername  string
	HueClientKey string
	// Sync Box settings (format: "name:ip:token,name2:ip2:token2")
	SyncBoxes []SyncBoxConfig
	// Google Drive settings (for screensaver and background photos)
	DrivePhotosFolder string
	ScreensaverTimeout     int // Seconds of inactivity before screensaver (default: 300)
	// Spotify settings
	SpotifyClientID     string
	SpotifyClientSecret string
	// Entertainment device settings
	// Sony devices (soundbar + TV) format: "name:host:port:psk:type" (type = soundbar|tv)
	SonyDevices []SonyDeviceConfig
	// Nvidia Shield format: "name:host:port"
	ShieldDevices []ShieldDeviceConfig
	// Xbox format: "name:host:liveid"
	XboxDevices       []XboxDeviceConfig
	XboxRESTServerURL string // Optional: xbox-smartglass-rest server URL
	// PS5 format: "name:deviceid:psnaccount"
	PS5Devices    []PS5DeviceConfig
	PS5MQTTTopic  string // Base MQTT topic for PS5-MQTT (default: homeassistant)
}

// SonyDeviceConfig holds configuration for a Sony device
type SonyDeviceConfig struct {
	Name       string
	Host       string
	Port       int
	PSK        string
	DeviceType string // "soundbar" or "tv"
}

// ShieldDeviceConfig holds configuration for an Nvidia Shield
type ShieldDeviceConfig struct {
	Name string
	Host string
	Port int
}

// XboxDeviceConfig holds configuration for an Xbox
type XboxDeviceConfig struct {
	Name   string
	Host   string
	LiveID string
}

// PS5DeviceConfig holds configuration for a PS5
type PS5DeviceConfig struct {
	Name       string
	DeviceID   string
	PSNAccount string
}

// SyncBoxConfig holds configuration for a single Hue Sync Box
type SyncBoxConfig struct {
	Name        string
	IP          string
	AccessToken string
}

// CalendarPrefs stores user preferences for calendar display
type CalendarPrefs struct {
	Calendars map[string]CalendarPref `json:"calendars"`
}

// CalendarPref stores display preferences for a single calendar
type CalendarPref struct {
	Visible bool   `json:"visible"`
	Color   string `json:"color"` // Hex color, empty = use Google's default
}

// CalendarWithPrefs combines calendar info with user preferences for template rendering
type CalendarWithPrefs struct {
	ID      string `json:"id"`
	Name    string `json:"name"`
	Color   string `json:"color"`
	Visible bool   `json:"visible"`
}

var haClient *homeassistant.Client
var hueClient *hue.Client
var hueStreamer *hue.EntertainmentStreamer
var syncBoxClients []*syncbox.Client
var calClient *calendar.Client
var tasksClient *tasks.Client
var weatherClient *weather.Client
var mqttClient *mqtt.Client
var cameraManager *camera.Manager
var driveClient *drive.Client
var spotifyClient *spotify.Client
var wsHub *websocket.Hub
var appConfig Config
var calendarPrefs *CalendarPrefs
var calendarPrefsFile string

// Entertainment device managers
var sonyManager *entertainment.SonyManager
var shieldManager *entertainment.ShieldManager
var xboxManager *entertainment.XboxManager
var ps5Manager *entertainment.PS5Manager

// Sensor state from Android app (HCC)
var sensorState struct {
	sync.RWMutex
	ProximityNear    bool
	LightLevel       float64
	LastProximityAt  time.Time
	LastLightAt      time.Time
	ScreenIdleAt     time.Time // when screen should turn off due to no proximity
	IdleTimeoutSecs  int       // seconds before screen turns off (from app config)
}

var tabletIdleTimeout = 180 * time.Second // default 180 seconds (3 minutes)

// Calendar cache for faster page loads
var calendarCache struct {
	sync.RWMutex
	Events           []*calendar.Event
	EventsStart      time.Time
	EventsEnd        time.Time
	EventsUpdatedAt  time.Time
	Calendars        []CalendarWithPrefs
	CalendarsUpdatedAt time.Time
	CacheDuration    time.Duration
}

// Sync box status cache to reduce API calls and improve responsiveness
var syncBoxCache struct {
	sync.RWMutex
	Statuses      map[int]*syncbox.Status
	UpdatedAt     map[int]time.Time
	LastError     map[int]time.Time // Track when last error occurred to reduce log spam
	CacheDuration time.Duration
}

func init() {
	calendarCache.CacheDuration = 30 * time.Second // Refresh cache every 30 seconds
	syncBoxCache.Statuses = make(map[int]*syncbox.Status)
	syncBoxCache.UpdatedAt = make(map[int]time.Time)
	syncBoxCache.LastError = make(map[int]time.Time)
	syncBoxCache.CacheDuration = 5 * time.Second // Cache sync box status for 5 seconds
}

func main() {
	// Load .env file if present (for local dev)
	_ = godotenv.Load()

	// Load timezone
	tz := getEnv("TZ", "America/New_York")
	loc, err := time.LoadLocation(tz)
	if err != nil {
		log.Printf("Warning: Invalid timezone %s, using UTC: %v", tz, err)
		loc = time.UTC
	}

	// Parse weather coordinates
	weatherLat, _ := strconv.ParseFloat(getEnv("WEATHER_LAT", "0"), 64)
	weatherLon, _ := strconv.ParseFloat(getEnv("WEATHER_LON", "0"), 64)

	// Parse MQTT port
	mqttPort, _ := strconv.Atoi(getEnv("MQTT_PORT", "1883"))

	// Parse custom MQTT doorbell topics
	var mqttDoorbellTopics []string
	if topicsStr := getEnv("MQTT_DOORBELL_TOPICS", ""); topicsStr != "" {
		mqttDoorbellTopics = parseEntities(topicsStr)
	}

	// Parse cameras from environment
	cameras := make(map[string]string)
	// Support simple CAMERAS list (for Frigate-only setups)
	if cameraList := getEnv("CAMERAS", ""); cameraList != "" {
		for _, name := range parseEntities(cameraList) {
			cameras[name] = "" // No RTSP URL needed when using Frigate
		}
	}
	// Also support individual CAMERA_* vars for direct access fallback
	if doorbell := getEnv("CAMERA_DOORBELL", ""); doorbell != "" {
		cameras["doorbell"] = doorbell
	}
	if livingroom := getEnv("CAMERA_LIVINGROOM", ""); livingroom != "" {
		cameras["livingroom"] = livingroom
	}
	if kitchen := getEnv("CAMERA_KITCHEN", ""); kitchen != "" {
		cameras["kitchen"] = kitchen
	}
	if frontDoor := getEnv("CAMERA_FRONT_DOOR", ""); frontDoor != "" {
		cameras["front_door"] = frontDoor
	}

	cfg := Config{
		Port:               getEnv("PORT", "8080"),
		BaseURL:            getEnv("BASE_URL", "http://localhost:8080"),
		HomeAssistantURL:   getEnv("HA_URL", "http://homeassistant.local:8123"),
		HomeAssistantToken: getEnv("HA_TOKEN", ""),
		Entities:           parseEntities(getEnv("HA_ENTITIES", "")),
		GoogleClientID:     getEnv("GOOGLE_CLIENT_ID", ""),
		GoogleClientSecret: getEnv("GOOGLE_CLIENT_SECRET", ""),
		GoogleCalendars:    parseEntities(getEnv("GOOGLE_CALENDARS", "")),
		GooglePlacesAPIKey:  getEnv("GOOGLE_PLACES_API_KEY", ""),
		GoogleWeatherAPIKey: getEnv("GOOGLE_WEATHER_API_KEY", ""),
		WeatherLat:          weatherLat,
		WeatherLon:          weatherLon,
		Timezone:           loc,
		MQTTHost:           getEnv("MQTT_HOST", ""),
		MQTTPort:           mqttPort,
		MQTTUsername:       getEnv("MQTT_USERNAME", ""),
		MQTTPassword:       getEnv("MQTT_PASSWORD", ""),
		MQTTDoorbellTopics: mqttDoorbellTopics,
		Cameras:            cameras,
		FrigateHost:        getEnv("FRIGATE_HOST", ""),
		DoorbellCamera:     getEnv("DOORBELL_CAMERA", "front_door"),
		WebhookSecret:      getEnv("WEBHOOK_SECRET", ""),
		HueBridgeIP:            getEnv("HUE_BRIDGE_IP", ""),
		HueUsername:            getEnv("HUE_USERNAME", ""),
		HueClientKey:           getEnv("HUE_CLIENT_KEY", ""),
		SyncBoxes:              parseSyncBoxes(getEnv("SYNC_BOXES", "")),
		DrivePhotosFolder: getEnv("DRIVE_PHOTOS_FOLDER", getEnv("DRIVE_BACKGROUND_FOLDER", "")),
		ScreensaverTimeout:     parseIntEnv("SCREENSAVER_TIMEOUT", 300),
		SpotifyClientID:           getEnv("SPOTIFY_CLIENT_ID", ""),
		SpotifyClientSecret:       getEnv("SPOTIFY_CLIENT_SECRET", ""),
		// Entertainment devices
		SonyDevices:       parseSonyDevices(getEnv("SONY_DEVICES", "")),
		ShieldDevices:     parseShieldDevices(getEnv("SHIELD_DEVICES", "")),
		XboxDevices:       parseXboxDevices(getEnv("XBOX_DEVICES", "")),
		XboxRESTServerURL: getEnv("XBOX_REST_SERVER", ""),
		PS5Devices:        parsePS5Devices(getEnv("PS5_DEVICES", "")),
		PS5MQTTTopic:      getEnv("PS5_MQTT_TOPIC", "homeassistant"),
	}
	appConfig = cfg
	log.Printf("Using timezone: %s", loc.String())

	// Initialize HA client
	if cfg.HomeAssistantToken != "" {
		haClient = homeassistant.NewClient(cfg.HomeAssistantURL, cfg.HomeAssistantToken)
		log.Printf("Home Assistant client initialized for %s", cfg.HomeAssistantURL)
	} else {
		log.Println("Warning: HA_TOKEN not set, Home Assistant integration disabled")
	}

	// Initialize Hue client
	if cfg.HueBridgeIP != "" && cfg.HueUsername != "" {
		hueClient = hue.NewClient(cfg.HueBridgeIP, cfg.HueUsername)
		log.Printf("Philips Hue client initialized for bridge %s", cfg.HueBridgeIP)

		// Initialize entertainment streamer for area switching
		hueStreamer = hue.NewEntertainmentStreamer(hueClient)
		log.Println("Hue Entertainment area switching enabled")
	} else {
		log.Println("Info: Hue bridge not configured (optional)")
	}

	// Initialize Sync Box clients
	if len(cfg.SyncBoxes) > 0 {
		for _, sbCfg := range cfg.SyncBoxes {
			client := syncbox.NewClient(sbCfg.IP, sbCfg.AccessToken, sbCfg.Name)
			syncBoxClients = append(syncBoxClients, client)
			log.Printf("Hue Sync Box client initialized: %s (%s)", sbCfg.Name, sbCfg.IP)
		}
	} else {
		log.Println("Info: No Sync Boxes configured (optional)")
	}

	// Initialize Google Calendar client
	if cfg.GoogleClientID != "" && cfg.GoogleClientSecret != "" {
		// Ensure data directory exists
		dataDir := getEnv("DATA_DIR", "data")
		if err := os.MkdirAll(dataDir, 0755); err != nil {
			log.Printf("Warning: Failed to create data directory: %v", err)
		}

		// Set up calendar preferences file path and load prefs
		calendarPrefsFile = filepath.Join(dataDir, "calendar_prefs.json")
		calendarPrefs = loadCalendarPrefs()

		redirectURL := cfg.BaseURL + "/auth/google/callback"
		tokenFile := filepath.Join(dataDir, "token.json")
		calClient = calendar.NewClient(cfg.GoogleClientID, cfg.GoogleClientSecret, redirectURL, tokenFile, cfg.GoogleCalendars, cfg.Timezone)

		// Try to initialize with stored token
		if calClient.IsAuthorized() {
			if err := calClient.Init(context.Background()); err != nil {
				log.Printf("Warning: Failed to init calendar with stored token: %v", err)
			} else {
				log.Println("Google Calendar initialized with stored token")
			}
		} else {
			log.Println("Google Calendar not authorized. Visit /auth/google to authorize.")
		}
	} else {
		log.Println("Warning: Google Calendar credentials not set")
	}

	// Initialize Google Tasks client (shares OAuth token with Calendar)
	if cfg.GoogleClientID != "" && cfg.GoogleClientSecret != "" {
		dataDir := getEnv("DATA_DIR", "data")
		tokenFile := filepath.Join(dataDir, "token.json")
		tasksClient = tasks.NewClient(cfg.GoogleClientID, cfg.GoogleClientSecret, tokenFile, cfg.Timezone)

		// Try to initialize with stored token (if calendar is authorized)
		if calClient != nil && calClient.IsAuthorized() {
			if err := tasksClient.Init(context.Background()); err != nil {
				log.Printf("Warning: Failed to init tasks client: %v", err)
			} else {
				log.Println("Google Tasks initialized with stored token")
			}
		}
	}

	// Initialize Google Drive client (shares OAuth token with Calendar)
	if calClient == nil {
		log.Println("Drive: Skipping - Calendar client not configured")
	} else if !calClient.IsAuthorized() {
		log.Println("Drive: Skipping - Calendar not authorized (complete OAuth first)")
	} else if cfg.DrivePhotosFolder == "" {
		log.Println("Drive: Skipping - DRIVE_PHOTOS_FOLDER not set")
	} else {
		httpClient, err := calClient.GetHTTPClient(context.Background())
		if err != nil {
			log.Printf("Warning: Failed to get HTTP client for Drive: %v", err)
		} else {
			driveClient, err = drive.NewClient(httpClient, cfg.DrivePhotosFolder)
			if err != nil {
				log.Printf("Warning: Failed to initialize Drive client: %v", err)
			} else {
				log.Printf("Google Drive client initialized (folder: %s)", cfg.DrivePhotosFolder)
			}
		}
	}

	// Initialize Spotify client
	if cfg.SpotifyClientID != "" && cfg.SpotifyClientSecret != "" {
		dataDir := getEnv("DATA_DIR", "data")
		redirectURL := cfg.BaseURL + "/auth/spotify/callback"
		spotifyClient = spotify.NewClient(cfg.SpotifyClientID, cfg.SpotifyClientSecret, redirectURL)

		// Set up token persistence
		tokenFile := filepath.Join(dataDir, "spotify_token.json")
		spotifyClient.SetTokenSaveCallback(func(token *spotify.Token) error {
			data, err := json.MarshalIndent(token, "", "  ")
			if err != nil {
				return err
			}
			return os.WriteFile(tokenFile, data, 0600)
		})

		// Try to load existing token
		if tokenData, err := os.ReadFile(tokenFile); err == nil {
			var token spotify.Token
			if err := json.Unmarshal(tokenData, &token); err == nil {
				spotifyClient.SetToken(&token)
				log.Println("Spotify client initialized with saved token")
			}
		} else {
			log.Println("Spotify client initialized. Visit /auth/spotify to authorize.")
		}
	} else {
		log.Println("Info: Spotify not configured (optional)")
	}

	// Initialize Weather client (Google Weather API - uses API key)
	if cfg.GoogleWeatherAPIKey != "" && cfg.WeatherLat != 0 && cfg.WeatherLon != 0 {
		weatherCacheFile := "data/weather_cache.json"
		weatherClient = weather.NewClient(cfg.GoogleWeatherAPIKey, cfg.WeatherLat, cfg.WeatherLon, cfg.Timezone, weatherCacheFile)
		weatherClient.Start()
		log.Printf("Weather client initialized for coordinates (%.4f, %.4f)", cfg.WeatherLat, cfg.WeatherLon)
	} else if cfg.WeatherLat != 0 && cfg.WeatherLon != 0 {
		log.Println("Warning: GOOGLE_WEATHER_API_KEY not set. Weather will not be available.")
	}

	// Initialize WebSocket hub
	wsHub = websocket.NewHub()
	go wsHub.Run()
	log.Println("WebSocket hub started")

	// Initialize Camera manager
	cameraManager = camera.NewManager()
	if cfg.FrigateHost != "" {
		cameraManager.SetFrigateHost(cfg.FrigateHost)
	}
	for name, rtspURL := range cfg.Cameras {
		if err := cameraManager.AddCamera(name, rtspURL); err != nil {
			log.Printf("Warning: Failed to add camera %s: %v", name, err)
		}
	}
	if len(cfg.Cameras) > 0 {
		log.Printf("Camera manager initialized with %d cameras", len(cfg.Cameras))
	}

	// Initialize MQTT client for doorbell events
	if cfg.MQTTHost != "" {
		mqttClient = mqtt.NewClient(mqtt.Config{
			Host:           cfg.MQTTHost,
			Port:           cfg.MQTTPort,
			Username:       cfg.MQTTUsername,
			Password:       cfg.MQTTPassword,
			ClientID:       "home-control-kiosk",
			DoorbellTopics: cfg.MQTTDoorbellTopics,
		})

		// Set doorbell handler to broadcast via WebSocket and wake tablet
		mqttClient.SetDoorbellHandler(func() {
			go wakeTablet() // Wake tablet screen first
			wsHub.BroadcastDoorbell(cfg.DoorbellCamera)
		})

		go func() {
			if err := mqttClient.Connect(); err != nil {
				log.Printf("Warning: MQTT connection failed: %v", err)
			}
		}()
		log.Printf("MQTT client connecting to %s:%d", cfg.MQTTHost, cfg.MQTTPort)
	}

	// Initialize entertainment device managers
	initEntertainmentManagers(cfg)

	// Load templates with custom functions
	templateFuncMap = template.FuncMap{
		"formatDate":     formatDate,
		"formatTime":     formatTime,
		"formatDateTime": formatDateTime,
		"isToday":        isToday,
		"isTomorrow":     isTomorrow,
		"dayName":        dayName,
		"json": func(v interface{}) template.JS {
			b, _ := json.Marshal(v)
			return template.JS(b)
		},
	}

	// Parse each page template separately with base to avoid content block conflicts
	pageTemplates = make(map[string]*template.Template)
	pages := []string{"calendar", "home"}
	for _, page := range pages {
		t, err := template.New("").Funcs(templateFuncMap).ParseFiles(
			filepath.Join("templates", "base.html"),
			filepath.Join("templates", page+".html"),
		)
		if err != nil {
			log.Fatalf("Failed to load template %s: %v", page, err)
		}
		pageTemplates[page] = t
	}

	r := chi.NewRouter()
	r.Use(ConditionalLogger)
	r.Use(middleware.Compress(5))

	// Static files
	r.Handle("/static/*", http.StripPrefix("/static/", http.FileServer(http.Dir("static"))))

	// Pages
	r.Get("/", handleCalendar)
	r.Get("/calendar", handleCalendar)
	r.Get("/home", handleHome(cfg))

	// Google OAuth routes
	r.Get("/auth/google", handleGoogleAuth)
	r.Get("/auth/google/callback", handleGoogleCallback)
	r.Get("/auth/google/logout", handleGoogleLogout)

	// API endpoints
	r.Post("/api/toggle/{entityID}", handleToggle)

	// Climate control endpoints
	r.Post("/api/climate/{entityID}/temperature", handleSetClimateTemperature)
	r.Post("/api/climate/{entityID}/mode", handleSetClimateMode)
	r.Post("/api/climate/{entityID}/fan", handleSetClimateFanMode)

	// Calendar API endpoints
	r.Get("/api/calendar/events", handleGetCalendarEvents)
	r.Get("/api/calendar/colors", handleGetColors)
	r.Get("/api/calendar/calendars", handleGetCalendars)
	r.Get("/api/calendar/prefs", handleGetCalendarPrefs)
	r.Put("/api/calendar/prefs/{calendarID}", handleUpdateCalendarPref)
	r.Post("/api/calendar/event", handleCreateEvent)
	r.Get("/api/calendar/event/{calendarID}/{eventID}", handleGetEvent)
	r.Put("/api/calendar/event/{calendarID}/{eventID}", handleUpdateEvent)
	r.Patch("/api/calendar/event/{calendarID}/{eventID}", handlePatchEvent)
	r.Delete("/api/calendar/event/{calendarID}/{eventID}", handleDeleteEvent)
	r.Post("/api/calendar/event/{calendarID}/{eventID}/move", handleMoveEvent)
	r.Get("/api/calendar/event/{calendarID}/{eventID}/instances", handleGetEventInstances)

	// Places API
	r.Get("/api/places/autocomplete", handlePlacesAutocomplete)

	// Tasks API
	r.Get("/api/tasks", handleGetTasks)
	r.Get("/api/tasks/lists", handleGetTaskLists)
	r.Post("/api/tasks", handleCreateTask)
	r.Post("/api/tasks/{listID}/{taskID}/toggle", handleToggleTask)
	r.Delete("/api/tasks/{listID}/{taskID}", handleDeleteTask)
	r.Post("/api/tasks/{listID}/clear", handleClearCompleted)

	// Weather API
	r.Get("/api/weather", handleGetWeather)

	// WebSocket
	r.Get("/ws", handleWebSocket)

	// Camera API
	r.Get("/api/camera/{name}/snapshot", handleCameraSnapshot)
	r.Get("/api/camera/{name}/stream", handleCameraStream)
	r.Post("/api/camera/{name}/talk", handleCameraTalk)
	r.Get("/api/cameras", handleGetCameras)

	// Entity states API (for AJAX refresh)
	r.Get("/api/entities", handleGetEntities(cfg))

	// Test doorbell (for debugging)
	r.Post("/api/doorbell/test", handleTestDoorbell)

	// Webhook for Home Assistant doorbell events
	r.Post("/api/webhook/doorbell", handleDoorbellWebhook)

	// Tablet app communication routes (app reports sensor data, server sends commands)
	r.Post("/api/tablet/sensor/proximity", handleTabletProximity)
	r.Post("/api/tablet/sensor/light", handleTabletLight)
	r.Get("/api/tablet/sensor/state", handleGetSensorState)
	r.Post("/api/tablet/kiosk/exit", handleExitKiosk)
	r.Post("/api/tablet/reload", handleTabletReload)
	r.Get("/api/tablet/theme", handleGetTabletTheme)

	// Hue API routes
	r.Get("/api/hue/rooms", handleGetHueRooms)
	r.Post("/api/hue/light/{id}/toggle", handleToggleHueLight)
	r.Post("/api/hue/light/{id}/brightness", handleSetHueLightBrightness)
	r.Post("/api/hue/group/{id}/toggle", handleToggleHueGroup)
	r.Post("/api/hue/group/{id}/brightness", handleSetHueGroupBrightness)
	r.Post("/api/hue/scene/{id}/activate", handleActivateHueScene)
	r.Post("/api/hue/entertainment/{id}/activate", handleActivateEntertainment)
	r.Post("/api/hue/entertainment/deactivate", handleDeactivateEntertainment)
	r.Get("/api/hue/entertainment/status", handleGetEntertainmentStatus)

	// Sync Box routes
	r.Get("/api/syncbox", handleGetSyncBoxes)
	r.Get("/api/syncbox/{index}/status", handleGetSyncBoxStatus)
	r.Post("/api/syncbox/{index}/sync", handleSetSyncBoxSync)
	r.Post("/api/syncbox/{index}/area", handleSetSyncBoxArea)
	r.Post("/api/syncbox/{index}/mode", handleSetSyncBoxMode)
	r.Post("/api/syncbox/{index}/brightness", handleSetSyncBoxBrightness)
	r.Post("/api/syncbox/{index}/input", handleSetSyncBoxInput)

	// Google Drive routes (photos for screensaver/background)
	r.Get("/api/drive/photos", handleGetDrivePhotos)
	r.Get("/api/drive/photos/random", handleGetRandomDrivePhoto)
	r.Get("/api/drive/photo/{id}", handleGetDrivePhoto)
	r.Get("/api/screensaver/config", handleGetScreensaverConfig)

	// Spotify routes
	r.Get("/auth/spotify", handleSpotifyAuth)
	r.Get("/auth/spotify/callback", handleSpotifyCallback)
	r.Get("/api/spotify/status", handleSpotifyStatus)
	r.Get("/api/spotify/playback", handleSpotifyPlayback)
	r.Get("/api/spotify/devices", handleSpotifyDevices)
	r.Post("/api/spotify/play", handleSpotifyPlay)
	r.Post("/api/spotify/pause", handleSpotifyPause)
	r.Post("/api/spotify/next", handleSpotifyNext)
	r.Post("/api/spotify/previous", handleSpotifyPrevious)
	r.Post("/api/spotify/volume", handleSpotifyVolume)
	r.Post("/api/spotify/seek", handleSpotifySeek)
	r.Post("/api/spotify/shuffle", handleSpotifyShuffle)
	r.Post("/api/spotify/repeat", handleSpotifyRepeat)
	r.Post("/api/spotify/transfer", handleSpotifyTransfer)
	r.Get("/api/spotify/playlists", handleSpotifyPlaylists)
	r.Get("/api/spotify/playlist/{id}/tracks", handleSpotifyPlaylistTracks)
	r.Get("/api/spotify/search", handleSpotifySearch)
	r.Get("/api/spotify/recent", handleSpotifyRecentlyPlayed)
	r.Get("/api/spotify/top/artists", handleSpotifyTopArtists)
	r.Get("/api/spotify/top/tracks", handleSpotifyTopTracks)
	r.Get("/api/spotify/album/{id}", handleSpotifyAlbum)
	r.Get("/api/spotify/album/{id}/saved", handleSpotifyAlbumSaved)
	r.Put("/api/spotify/album/{id}/save", handleSpotifyAlbumSave)
	r.Delete("/api/spotify/album/{id}/save", handleSpotifyAlbumRemove)
	r.Get("/api/spotify/artist/{id}", handleSpotifyArtist)
	r.Get("/api/spotify/artist/{id}/albums", handleSpotifyArtistAlbums)
	r.Get("/api/spotify/artist/{id}/top-tracks", handleSpotifyArtistTopTracks)
	r.Get("/api/spotify/artist/{id}/following", handleSpotifyArtistFollowing)
	r.Put("/api/spotify/artist/{id}/follow", handleSpotifyArtistFollow)
	r.Delete("/api/spotify/artist/{id}/follow", handleSpotifyArtistUnfollow)
	r.Get("/api/spotify/library/albums", handleSpotifyLibraryAlbums)
	r.Get("/api/spotify/library/artists", handleSpotifyLibraryArtists)
	r.Get("/api/spotify/library/tracks", handleSpotifyLibraryTracks)
	r.Get("/api/spotify/library/shows", handleSpotifyLibraryShows)

	// Entertainment device routes
	r.Get("/api/entertainment/devices", handleGetEntertainmentDevices)
	// Sony (soundbar + TV)
	r.Get("/api/entertainment/sony", handleGetSonyDevices)
	r.Get("/api/entertainment/sony/{name}/state", handleGetSonyState)
	r.Post("/api/entertainment/sony/{name}/power", handleSonyPower)
	r.Post("/api/entertainment/sony/{name}/volume", handleSonyVolume)
	r.Post("/api/entertainment/sony/{name}/mute", handleSonyMute)
	r.Post("/api/entertainment/sony/{name}/input", handleSonyInput)
	// Shield
	r.Get("/api/entertainment/shield", handleGetShieldDevices)
	r.Get("/api/entertainment/shield/{name}/state", handleGetShieldState)
	r.Post("/api/entertainment/shield/{name}/power", handleShieldPower)
	r.Post("/api/entertainment/shield/{name}/navigate", handleShieldNavigate)
	r.Post("/api/entertainment/shield/{name}/media", handleShieldMedia)
	r.Post("/api/entertainment/shield/{name}/app", handleShieldApp)
	// Xbox
	r.Get("/api/entertainment/xbox", handleGetXboxDevices)
	r.Get("/api/entertainment/xbox/{name}/state", handleGetXboxState)
	r.Post("/api/entertainment/xbox/{name}/power", handleXboxPower)
	r.Post("/api/entertainment/xbox/{name}/input", handleXboxInput)
	r.Post("/api/entertainment/xbox/{name}/media", handleXboxMedia)
	// PS5
	r.Get("/api/entertainment/ps5", handleGetPS5Devices)
	r.Get("/api/entertainment/ps5/{name}/state", handleGetPS5State)
	r.Post("/api/entertainment/ps5/{name}/power", handlePS5Power)

	// Icon serving
	r.Get("/icon/{name}", icons.Handler())

	log.Printf("Server starting on :%s", cfg.Port)
	log.Fatal(http.ListenAndServe(":"+cfg.Port, r))
}

func handleCalendar(w http.ResponseWriter, r *http.Request) {
	var events []*calendar.Event
	var authorized bool

	view := r.URL.Query().Get("view")
	if view == "" {
		view = "day"
	}

	// Async loading - render shell immediately, load events via JavaScript
	asyncLoad := r.URL.Query().Get("async") == "true"

	// Parse date parameter (defaults to today)
	dateStr := r.URL.Query().Get("date")
	var baseDate time.Time
	if dateStr != "" {
		parsed, err := time.ParseInLocation("2006-01-02", dateStr, appConfig.Timezone)
		if err != nil {
			baseDate = time.Now().In(appConfig.Timezone)
		} else {
			baseDate = parsed
		}
	} else {
		baseDate = time.Now().In(appConfig.Timezone)
	}

	// Calculate date range based on view
	var startDate, endDate time.Time
	var prevDate, nextDate string

	switch view {
	case "day":
		startDate = time.Date(baseDate.Year(), baseDate.Month(), baseDate.Day(), 0, 0, 0, 0, appConfig.Timezone)
		endDate = startDate.AddDate(0, 0, 14) // Fetch 14 days for day view
		prevDate = startDate.AddDate(0, 0, -1).Format("2006-01-02")
		nextDate = startDate.AddDate(0, 0, 1).Format("2006-01-02")
	case "week":
		// Find Sunday of the week containing baseDate
		startDate = baseDate.AddDate(0, 0, -int(baseDate.Weekday()))
		startDate = time.Date(startDate.Year(), startDate.Month(), startDate.Day(), 0, 0, 0, 0, appConfig.Timezone)
		endDate = startDate.AddDate(0, 0, 7)
		prevDate = startDate.AddDate(0, 0, -7).Format("2006-01-02")
		nextDate = startDate.AddDate(0, 0, 7).Format("2006-01-02")
	case "month":
		// First day of the month
		startDate = time.Date(baseDate.Year(), baseDate.Month(), 1, 0, 0, 0, 0, appConfig.Timezone)
		// Need 6 weeks to cover full month grid
		gridStart := startDate.AddDate(0, 0, -int(startDate.Weekday()))
		endDate = gridStart.AddDate(0, 0, 42)
		prevDate = startDate.AddDate(0, -1, 0).Format("2006-01-02")
		nextDate = startDate.AddDate(0, 1, 0).Format("2006-01-02")
	default:
		startDate = time.Now().In(appConfig.Timezone)
		endDate = startDate.AddDate(0, 0, 14)
	}

	var calendarsWithPrefs []CalendarWithPrefs
	if calClient != nil {
		authorized = calClient.IsAuthorized()
		if authorized {
			var err error
			// Always fetch calendars (needed for dropdown)
			calendarsWithPrefs, err = getCachedCalendarsWithPrefs(r.Context())
			if err != nil {
				log.Printf("Error fetching calendars with prefs: %v", err)
			}

			// Only fetch events if not async loading
			if !asyncLoad {
				events, err = getCachedEventsInRange(r.Context(), startDate, endDate)
				if err != nil {
					log.Printf("Error fetching calendar events: %v", err)
				}
				// Apply custom colors from preferences to events
				colorMap := make(map[string]string)
				for _, cal := range calendarsWithPrefs {
					colorMap[cal.ID] = cal.Color
				}
				for i := range events {
					if customColor, ok := colorMap[events[i].CalendarID]; ok && customColor != "" {
						events[i].Color = customColor
					}
				}
			}
		}
	}

	// Group events by day
	eventsByDay := groupEventsByDay(events)

	// Build navigation URLs
	todayDate := time.Now().In(appConfig.Timezone).Format("2006-01-02")

	data := map[string]interface{}{
		"Title":             "Calendar",
		"Authorized":        authorized,
		"Events":            events,
		"EventsByDay":       eventsByDay,
		"View":              view,
		"Calendars":         calendarsWithPrefs,
		"CurrentDate":       baseDate.Format("2006-01-02"),
		"PrevDate":          prevDate,
		"NextDate":          nextDate,
		"TodayDate":         todayDate,
		"WeatherConfigured": weatherClient != nil,
		"AsyncLoad":         asyncLoad,
	}

	// Add view-specific data
	if view == "week" {
		data["WeekDays"] = buildWeekViewForDate(events, startDate)
		weekEnd := startDate.AddDate(0, 0, 6)
		if startDate.Month() == weekEnd.Month() {
			data["WeekDateRange"] = fmt.Sprintf("%s %d - %d, %d", startDate.Month().String(), startDate.Day(), weekEnd.Day(), startDate.Year())
		} else if startDate.Year() == weekEnd.Year() {
			data["WeekDateRange"] = fmt.Sprintf("%s %d - %s %d, %d", startDate.Month().String()[:3], startDate.Day(), weekEnd.Month().String()[:3], weekEnd.Day(), startDate.Year())
		} else {
			data["WeekDateRange"] = fmt.Sprintf("%s %d, %d - %s %d, %d", startDate.Month().String()[:3], startDate.Day(), startDate.Year(), weekEnd.Month().String()[:3], weekEnd.Day(), weekEnd.Year())
		}
	} else if view == "month" {
		data["MonthDays"] = buildMonthView(baseDate, events)
		data["MonthName"] = baseDate.Month().String()
		data["Year"] = baseDate.Year()
	} else if view == "day" {
		data["DayDate"] = baseDate
		data["DayDateStr"] = baseDate.Format("Monday, January 2, 2006")

		// Generate hours array (0-23) for timeline
		hours := make([]int, 24)
		for i := 0; i < 24; i++ {
			hours[i] = i
		}
		data["Hours"] = hours

		// Filter events for just this day
		dayStart := time.Date(baseDate.Year(), baseDate.Month(), baseDate.Day(), 0, 0, 0, 0, appConfig.Timezone)
		dayEnd := dayStart.AddDate(0, 0, 1)
		var dayEvents []*calendar.Event
		for _, e := range events {
			eventStart := e.Start.In(appConfig.Timezone)
			eventEnd := e.End.In(appConfig.Timezone)
			// Include event if it overlaps with this day
			if eventStart.Before(dayEnd) && eventEnd.After(dayStart) {
				dayEvents = append(dayEvents, e)
			}
		}
		data["DayEvents"] = dayEvents
	}

	getTemplate("calendar").ExecuteTemplate(w, "base", data)
}

type DayEvents struct {
	Date   time.Time
	Events []*calendar.Event
}

type WeekDay struct {
	Date    time.Time
	DayName string
	DateStr string
	IsToday bool
	Events  []*calendar.Event
}

type MonthDay struct {
	Day       int
	Date      time.Time
	InMonth   bool
	IsToday   bool
	Events    []*calendar.Event // Limited events for display
	AllEvents []*calendar.Event // All events (for modal)
	MoreCount int               // Number of events beyond the display limit
}

const maxEventsPerDay = 4 // Max events to show per day in month view

func buildWeekView(events []*calendar.Event) []WeekDay {
	now := time.Now().In(appConfig.Timezone)
	sunday := now.AddDate(0, 0, -int(now.Weekday()))
	return buildWeekViewForDate(events, sunday)
}

func buildWeekViewForDate(events []*calendar.Event, weekStart time.Time) []WeekDay {
	now := time.Now().In(appConfig.Timezone)
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, appConfig.Timezone)

	// Build event map using timezone
	eventMap := make(map[string][]*calendar.Event)
	for _, e := range events {
		key := e.Start.In(appConfig.Timezone).Format("2006-01-02")
		eventMap[key] = append(eventMap[key], e)
	}

	var week []WeekDay
	for i := 0; i < 7; i++ {
		d := weekStart.AddDate(0, 0, i)
		key := d.Format("2006-01-02")
		week = append(week, WeekDay{
			Date:    d,
			DayName: d.Weekday().String()[:3],
			DateStr: d.Format("Jan 2"),
			IsToday: d.Equal(today),
			Events:  eventMap[key],
		})
	}
	return week
}

func buildMonthView(viewDate time.Time, events []*calendar.Event) []MonthDay {
	viewDate = viewDate.In(appConfig.Timezone)

	// Build event map using timezone
	eventMap := make(map[string][]*calendar.Event)
	for _, e := range events {
		key := e.Start.In(appConfig.Timezone).Format("2006-01-02")
		eventMap[key] = append(eventMap[key], e)
	}

	// Use actual current date for IsToday, not the viewed date
	now := time.Now().In(appConfig.Timezone)
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, appConfig.Timezone)

	// First day of month (based on viewed date)
	firstOfMonth := time.Date(viewDate.Year(), viewDate.Month(), 1, 0, 0, 0, 0, appConfig.Timezone)

	// Last day of month
	lastOfMonth := firstOfMonth.AddDate(0, 1, -1)

	// Start from Sunday of the week containing the first of month
	startDay := firstOfMonth.AddDate(0, 0, -int(firstOfMonth.Weekday()))

	// End on Saturday of the week containing the last of month
	endDay := lastOfMonth.AddDate(0, 0, 6-int(lastOfMonth.Weekday()))

	var days []MonthDay
	for d := startDay; !d.After(endDay); d = d.AddDate(0, 0, 1) {
		key := d.Format("2006-01-02")
		allEvents := eventMap[key]
		displayEvents := allEvents
		moreCount := 0

		// Limit events shown per day
		if len(allEvents) > maxEventsPerDay {
			displayEvents = allEvents[:maxEventsPerDay]
			moreCount = len(allEvents) - maxEventsPerDay
		}

		days = append(days, MonthDay{
			Day:       d.Day(),
			Date:      d,
			InMonth:   d.Month() == viewDate.Month(),
			IsToday:   d.Equal(today),
			Events:    displayEvents,
			AllEvents: allEvents,
			MoreCount: moreCount,
		})
	}
	return days
}

func groupEventsByDay(events []*calendar.Event) []DayEvents {
	if len(events) == 0 {
		return nil
	}

	grouped := make(map[string]*DayEvents)
	var order []string

	for _, event := range events {
		localStart := event.Start.In(appConfig.Timezone)
		key := localStart.Format("2006-01-02")
		if _, exists := grouped[key]; !exists {
			grouped[key] = &DayEvents{
				Date:   time.Date(localStart.Year(), localStart.Month(), localStart.Day(), 0, 0, 0, 0, appConfig.Timezone),
				Events: []*calendar.Event{},
			}
			order = append(order, key)
		}
		grouped[key].Events = append(grouped[key].Events, event)
	}

	var result []DayEvents
	for _, key := range order {
		result = append(result, *grouped[key])
	}
	return result
}

// handleGetCalendarEvents returns calendar events as JSON for AJAX refresh
func handleGetCalendarEvents(w http.ResponseWriter, r *http.Request) {
	if calClient == nil || !calClient.IsAuthorized() {
		http.Error(w, "Calendar not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	view := r.URL.Query().Get("view")
	if view == "" {
		view = "day"
	}

	// Parse date parameter (defaults to today)
	dateStr := r.URL.Query().Get("date")
	var baseDate time.Time
	if dateStr != "" {
		parsed, err := time.ParseInLocation("2006-01-02", dateStr, appConfig.Timezone)
		if err != nil {
			baseDate = time.Now().In(appConfig.Timezone)
		} else {
			baseDate = parsed
		}
	} else {
		baseDate = time.Now().In(appConfig.Timezone)
	}

	// Calculate date range based on view
	var startDate, endDate time.Time

	switch view {
	case "day":
		startDate = time.Date(baseDate.Year(), baseDate.Month(), baseDate.Day(), 0, 0, 0, 0, appConfig.Timezone)
		endDate = startDate.AddDate(0, 0, 14)
	case "week":
		startDate = baseDate.AddDate(0, 0, -int(baseDate.Weekday()))
		startDate = time.Date(startDate.Year(), startDate.Month(), startDate.Day(), 0, 0, 0, 0, appConfig.Timezone)
		endDate = startDate.AddDate(0, 0, 7)
	case "month":
		startDate = time.Date(baseDate.Year(), baseDate.Month(), 1, 0, 0, 0, 0, appConfig.Timezone)
		gridStart := startDate.AddDate(0, 0, -int(startDate.Weekday()))
		endDate = gridStart.AddDate(0, 0, 42)
	default:
		startDate = time.Now().In(appConfig.Timezone)
		endDate = startDate.AddDate(0, 0, 14)
	}

	// Use cached events for faster response
	events, err := getCachedEventsInRange(r.Context(), startDate, endDate)
	if err != nil {
		log.Printf("Error fetching calendar events: %v", err)
		http.Error(w, "Failed to fetch events", http.StatusInternalServerError)
		return
	}

	// Apply calendar colors from preferences
	calendarsWithPrefs, err := getCachedCalendarsWithPrefs(r.Context())
	if err == nil {
		colorMap := make(map[string]string)
		for _, cal := range calendarsWithPrefs {
			colorMap[cal.ID] = cal.Color
		}
		for i := range events {
			if customColor, ok := colorMap[events[i].CalendarID]; ok && customColor != "" {
				events[i].Color = customColor
			}
		}
	}

	// Return events as JSON
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(events)
}

func handleGoogleAuth(w http.ResponseWriter, r *http.Request) {
	if calClient == nil {
		http.Error(w, "Google Calendar not configured", http.StatusServiceUnavailable)
		return
	}
	calClient.HandleAuth(w, r)
}

func handleGoogleCallback(w http.ResponseWriter, r *http.Request) {
	if calClient == nil {
		http.Error(w, "Google Calendar not configured", http.StatusServiceUnavailable)
		return
	}
	calClient.HandleCallback(w, r)

	// Also initialize tasks client after successful OAuth
	if tasksClient != nil && calClient.IsAuthorized() {
		if err := tasksClient.Init(r.Context()); err != nil {
			log.Printf("Warning: Failed to init tasks client after OAuth: %v", err)
		} else {
			log.Println("Google Tasks initialized after OAuth")
		}
	}
}

func handleGoogleLogout(w http.ResponseWriter, r *http.Request) {
	if calClient == nil {
		http.Error(w, "Google Calendar not configured", http.StatusServiceUnavailable)
		return
	}
	if err := calClient.ClearToken(); err != nil {
		log.Printf("Error clearing token: %v", err)
		http.Error(w, "Failed to clear token", http.StatusInternalServerError)
		return
	}
	http.Redirect(w, r, "/auth/google", http.StatusTemporaryRedirect)
}

// populateLightGroupMembers detects light groups and fetches their member entities
func populateLightGroupMembers(cards []*homeassistant.Card) {
	if haClient == nil {
		return
	}

	for _, card := range cards {
		if card.Type != homeassistant.CardTypeLight {
			continue
		}

		// Debug: log what we're checking
		if entityIDAttr, exists := card.Attributes["entity_id"]; exists {
			log.Printf("Light %s has entity_id attribute: %T = %v", card.EntityID, entityIDAttr, entityIDAttr)
		}

		// Check if this light has entity_id attribute (indicates it's a group)
		if entityIDs, ok := card.Attributes["entity_id"].([]interface{}); ok && len(entityIDs) > 0 {
			log.Printf("Detected light group %s with %d members", card.EntityID, len(entityIDs))
			card.IsLightGroup = true

			// Fetch each member entity
			var memberIDs []string
			for _, id := range entityIDs {
				if idStr, ok := id.(string); ok {
					memberIDs = append(memberIDs, idStr)
				}
			}

			if len(memberIDs) > 0 {
				log.Printf("Fetching %d member entities for %s: %v", len(memberIDs), card.EntityID, memberIDs)
				memberEntities, err := haClient.GetStates(memberIDs)
				if err != nil {
					log.Printf("Error fetching light group members: %v", err)
					continue
				}

				log.Printf("Got %d member entities for %s", len(memberEntities), card.EntityID)
				for _, member := range memberEntities {
					card.Members = append(card.Members, member.ToCard())
				}
				log.Printf("Light group %s now has %d members, IsLightGroup=%v", card.EntityID, len(card.Members), card.IsLightGroup)
			}
		}
	}
}

func handleHome(cfg Config) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var cards []*homeassistant.Card

		if haClient != nil && len(cfg.Entities) > 0 {
			entities, err := haClient.GetStates(cfg.Entities)
			if err != nil {
				log.Printf("Error fetching HA states: %v", err)
			}
			for _, e := range entities {
				cards = append(cards, e.ToCard())
			}

			// Populate light group members
			populateLightGroupMembers(cards)
		}

		groups := homeassistant.GroupCards(cards)

		// Get camera list
		var cameras []map[string]string
		if cameraManager != nil {
			for _, cam := range cameraManager.GetCameras() {
				cameras = append(cameras, map[string]string{
					"name":  cam.Name,
					"label": strings.Title(cam.Name),
				})
			}
		}

		data := map[string]interface{}{
			"Title":             "Home",
			"Groups":            groups,
			"WeatherConfigured": weatherClient != nil,
			"Cameras":           cameras,
		}
		getTemplate("home").ExecuteTemplate(w, "base", data)
	}
}

func handleGetEntities(cfg Config) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var cards []*homeassistant.Card

		if haClient != nil && len(cfg.Entities) > 0 {
			entities, err := haClient.GetStates(cfg.Entities)
			if err != nil {
				log.Printf("Error fetching HA states: %v", err)
				http.Error(w, "Failed to fetch states", http.StatusInternalServerError)
				return
			}
			for _, e := range entities {
				cards = append(cards, e.ToCard())
			}

			// Populate light group members
			populateLightGroupMembers(cards)
		}

		groups := homeassistant.GroupCards(cards)

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(groups)
	}
}

func handleToggle(w http.ResponseWriter, r *http.Request) {
	if haClient == nil {
		http.Error(w, "HA not configured", http.StatusServiceUnavailable)
		return
	}

	entityID := chi.URLParam(r, "entityID")
	if entityID == "" {
		http.Error(w, "Missing entity ID", http.StatusBadRequest)
		return
	}

	// Determine domain and service
	parts := strings.Split(entityID, ".")
	if len(parts) != 2 {
		http.Error(w, "Invalid entity ID", http.StatusBadRequest)
		return
	}

	domain := parts[0]
	var service string

	// Some domains use different services
	switch domain {
	case "light", "switch", "fan", "cover":
		service = "toggle"
	case "lock":
		// Check current state to determine lock/unlock
		entity, err := haClient.GetState(entityID)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		if entity.State == "locked" {
			service = "unlock"
		} else {
			service = "lock"
		}
	default:
		http.Error(w, "Cannot toggle this entity type", http.StatusBadRequest)
		return
	}

	if err := haClient.CallService(domain, service, entityID); err != nil {
		log.Printf("Error toggling %s: %v", entityID, err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	// Return updated state
	entity, err := haClient.GetState(entityID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(entity.ToCard())
}

type SetTemperatureRequest struct {
	Temperature   *float64 `json:"temperature,omitempty"`
	TargetTempLow *float64 `json:"target_temp_low,omitempty"`
	TargetTempHigh *float64 `json:"target_temp_high,omitempty"`
}

func handleSetClimateTemperature(w http.ResponseWriter, r *http.Request) {
	if haClient == nil {
		http.Error(w, "HA not configured", http.StatusServiceUnavailable)
		return
	}

	entityID := chi.URLParam(r, "entityID")
	if entityID == "" {
		http.Error(w, "Missing entity ID", http.StatusBadRequest)
		return
	}

	var req SetTemperatureRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	var err error
	if req.TargetTempLow != nil && req.TargetTempHigh != nil {
		// Dual setpoint mode (for heat_cool/auto)
		err = haClient.SetClimateDualTemperature(entityID, *req.TargetTempLow, *req.TargetTempHigh)
	} else if req.Temperature != nil {
		// Single temperature mode
		err = haClient.SetClimateTemperature(entityID, *req.Temperature)
	} else {
		http.Error(w, "Either temperature or target_temp_low/high required", http.StatusBadRequest)
		return
	}

	if err != nil {
		log.Printf("Error setting climate temperature for %s: %v", entityID, err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	// Return updated state
	entity, err := haClient.GetState(entityID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(entity.ToCard())
}

type SetHVACModeRequest struct {
	Mode string `json:"mode"`
}

func handleSetClimateMode(w http.ResponseWriter, r *http.Request) {
	if haClient == nil {
		http.Error(w, "HA not configured", http.StatusServiceUnavailable)
		return
	}

	entityID := chi.URLParam(r, "entityID")
	if entityID == "" {
		http.Error(w, "Missing entity ID", http.StatusBadRequest)
		return
	}

	var req SetHVACModeRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := haClient.SetClimateHVACMode(entityID, req.Mode); err != nil {
		log.Printf("Error setting HVAC mode for %s: %v", entityID, err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	// Return updated state
	entity, err := haClient.GetState(entityID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(entity.ToCard())
}

type SetFanModeRequest struct {
	FanMode string `json:"fan_mode"`
}

func handleSetClimateFanMode(w http.ResponseWriter, r *http.Request) {
	if haClient == nil {
		http.Error(w, "HA not configured", http.StatusServiceUnavailable)
		return
	}

	entityID := chi.URLParam(r, "entityID")
	if entityID == "" {
		http.Error(w, "Missing entity ID", http.StatusBadRequest)
		return
	}

	var req SetFanModeRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := haClient.SetClimateFanMode(entityID, req.FanMode); err != nil {
		log.Printf("Error setting fan mode for %s: %v", entityID, err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	// Return updated state
	entity, err := haClient.GetState(entityID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(entity.ToCard())
}

type CreateEventRequest struct {
	Type        string `json:"type"`        // "event" or "task"
	Title       string `json:"title"`
	Date        string `json:"date"`        // YYYY-MM-DD (start date)
	EndDate     string `json:"endDate"`     // YYYY-MM-DD (end date, optional - defaults to Date)
	Time        string `json:"time"`        // HH:MM (optional, empty = all day)
	EndTime     string `json:"endTime"`     // HH:MM (optional)
	Location    string `json:"location"`    // optional
	Description string `json:"description"` // optional
	Repeat      string `json:"repeat"`      // optional: daily, weekly, monthly, yearly, weekdays
	CalendarID  string `json:"calendarId"`  // optional: calendar to create event on
}

func handleCreateEvent(w http.ResponseWriter, r *http.Request) {
	if calClient == nil {
		http.Error(w, "Calendar not configured", http.StatusServiceUnavailable)
		return
	}

	if !calClient.IsAuthorized() {
		http.Error(w, "Calendar not authorized", http.StatusUnauthorized)
		return
	}

	var req CreateEventRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if req.Title == "" || req.Date == "" {
		http.Error(w, "Title and date are required", http.StatusBadRequest)
		return
	}

	// Parse start date
	startDate, err := time.ParseInLocation("2006-01-02", req.Date, appConfig.Timezone)
	if err != nil {
		http.Error(w, "Invalid date format", http.StatusBadRequest)
		return
	}

	// Parse end date (defaults to start date if not provided)
	endDateStr := req.EndDate
	if endDateStr == "" {
		endDateStr = req.Date
	}
	endDate, err := time.ParseInLocation("2006-01-02", endDateStr, appConfig.Timezone)
	if err != nil {
		http.Error(w, "Invalid end date format", http.StatusBadRequest)
		return
	}

	var start, end time.Time
	var allDay bool

	if req.Time == "" {
		// All day event
		allDay = true
		start = startDate
		// For all-day events, end date is exclusive (add 1 day)
		end = endDate.AddDate(0, 0, 1)
	} else {
		// Timed event
		allDay = false
		startTime, err := time.ParseInLocation("2006-01-02 15:04", req.Date+" "+req.Time, appConfig.Timezone)
		if err != nil {
			http.Error(w, "Invalid time format", http.StatusBadRequest)
			return
		}
		start = startTime

		if req.EndTime != "" {
			// Use end date with end time
			endTime, err := time.ParseInLocation("2006-01-02 15:04", endDateStr+" "+req.EndTime, appConfig.Timezone)
			if err != nil {
				http.Error(w, "Invalid end time format", http.StatusBadRequest)
				return
			}
			end = endTime
		} else {
			end = start.Add(1 * time.Hour) // Default 1 hour duration
		}
	}

	// Build event options
	opts := &calendar.CreateEventOptions{
		CalendarID:  req.CalendarID,
		Location:    req.Location,
		Description: req.Description,
	}

	// Convert repeat string to RRULE
	if req.Repeat != "" {
		rrule := repeatToRRule(req.Repeat)
		if rrule != "" {
			opts.Recurrence = []string{rrule}
		}
	}

	log.Printf("Creating event: title=%q, date=%s, start=%v, end=%v, allDay=%v, calendarId=%q", req.Title, req.Date, start, end, allDay, req.CalendarID)
	event, err := calClient.CreateEvent(r.Context(), req.Title, start, end, allDay, opts)
	if err != nil {
		log.Printf("Error creating event: %v", err)
		http.Error(w, "Failed to create event: "+err.Error(), http.StatusInternalServerError)
		return
	}

	// Invalidate server-side cache so next page load shows the new event
	invalidateCalendarCache()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(event)
}

func handleGetEvent(w http.ResponseWriter, r *http.Request) {
	if calClient == nil || !calClient.IsAuthorized() {
		http.Error(w, "Calendar not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	calendarID, _ := url.QueryUnescape(chi.URLParam(r, "calendarID"))
	eventID, _ := url.QueryUnescape(chi.URLParam(r, "eventID"))

	event, err := calClient.GetEvent(r.Context(), calendarID, eventID)
	if err != nil {
		log.Printf("Error getting event: %v", err)
		http.Error(w, "Failed to get event: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	// Invalidate calendar cache after creating event
	invalidateCalendarCache()

	json.NewEncoder(w).Encode(event)
}

type UpdateEventRequest struct {
	Title       string `json:"title"`
	Date        string `json:"date"`
	Time        string `json:"time"`
	EndTime     string `json:"endTime"`
	Location    string `json:"location"`
	Description string `json:"description"`
	Repeat      string `json:"repeat"`
	ColorID     string `json:"colorId"`
}

func handleUpdateEvent(w http.ResponseWriter, r *http.Request) {
	if calClient == nil || !calClient.IsAuthorized() {
		http.Error(w, "Calendar not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	calendarID, _ := url.QueryUnescape(chi.URLParam(r, "calendarID"))
	eventID, _ := url.QueryUnescape(chi.URLParam(r, "eventID"))

	var req UpdateEventRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if req.Title == "" || req.Date == "" {
		http.Error(w, "Title and date are required", http.StatusBadRequest)
		return
	}

	date, err := time.ParseInLocation("2006-01-02", req.Date, appConfig.Timezone)
	if err != nil {
		http.Error(w, "Invalid date format", http.StatusBadRequest)
		return
	}

	var start, end time.Time
	var allDay bool

	if req.Time == "" {
		allDay = true
		start = date
		end = date.AddDate(0, 0, 1)
	} else {
		allDay = false
		startTime, err := time.ParseInLocation("2006-01-02 15:04", req.Date+" "+req.Time, appConfig.Timezone)
		if err != nil {
			http.Error(w, "Invalid time format", http.StatusBadRequest)
			return
		}
		start = startTime

		if req.EndTime != "" {
			endTime, err := time.ParseInLocation("2006-01-02 15:04", req.Date+" "+req.EndTime, appConfig.Timezone)
			if err != nil {
				http.Error(w, "Invalid end time format", http.StatusBadRequest)
				return
			}
			end = endTime
			if !end.After(start) {
				end = end.AddDate(0, 0, 1)
			}
		} else {
			end = start.Add(1 * time.Hour)
		}
	}

	opts := &calendar.CreateEventOptions{
		Location:    req.Location,
		Description: req.Description,
		ColorID:     req.ColorID,
	}

	if req.Repeat != "" {
		rrule := repeatToRRule(req.Repeat)
		if rrule != "" {
			opts.Recurrence = []string{rrule}
		}
	}

	event, err := calClient.UpdateEvent(r.Context(), calendarID, eventID, req.Title, start, end, allDay, opts)
	if err != nil {
		log.Printf("Error updating event: %v", err)
		http.Error(w, "Failed to update event: "+err.Error(), http.StatusInternalServerError)
		return
	}

	invalidateCalendarCache()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(event)
}

type PatchEventRequest struct {
	Title       *string `json:"title,omitempty"`
	Date        *string `json:"date,omitempty"`
	Time        *string `json:"time,omitempty"`
	EndTime     *string `json:"endTime,omitempty"`
	Location    *string `json:"location,omitempty"`
	Description *string `json:"description,omitempty"`
	ColorID     *string `json:"colorId,omitempty"`
}

func handlePatchEvent(w http.ResponseWriter, r *http.Request) {
	if calClient == nil || !calClient.IsAuthorized() {
		http.Error(w, "Calendar not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	calendarID, _ := url.QueryUnescape(chi.URLParam(r, "calendarID"))
	eventID, _ := url.QueryUnescape(chi.URLParam(r, "eventID"))

	var req PatchEventRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	opts := &calendar.UpdateEventOptions{}

	if req.Title != nil {
		opts.Title = req.Title
	}
	if req.Location != nil {
		opts.Location = req.Location
	}
	if req.Description != nil {
		opts.Description = req.Description
	}
	if req.ColorID != nil {
		opts.ColorID = req.ColorID
	}

	// Handle time updates
	if req.Date != nil {
		date, err := time.ParseInLocation("2006-01-02", *req.Date, appConfig.Timezone)
		if err != nil {
			http.Error(w, "Invalid date format", http.StatusBadRequest)
			return
		}

		if req.Time != nil && *req.Time != "" {
			startTime, err := time.ParseInLocation("2006-01-02 15:04", *req.Date+" "+*req.Time, appConfig.Timezone)
			if err != nil {
				http.Error(w, "Invalid time format", http.StatusBadRequest)
				return
			}
			opts.Start = &startTime
			allDay := false
			opts.AllDay = &allDay
		} else {
			opts.Start = &date
			allDay := true
			opts.AllDay = &allDay
		}

		if req.EndTime != nil && *req.EndTime != "" {
			endTime, err := time.ParseInLocation("2006-01-02 15:04", *req.Date+" "+*req.EndTime, appConfig.Timezone)
			if err != nil {
				http.Error(w, "Invalid end time format", http.StatusBadRequest)
				return
			}
			if opts.Start != nil && !endTime.After(*opts.Start) {
				endTime = endTime.AddDate(0, 0, 1)
			}
			opts.End = &endTime
		}
	}

	event, err := calClient.PatchEvent(r.Context(), calendarID, eventID, opts)
	if err != nil {
		log.Printf("Error patching event: %v", err)
		http.Error(w, "Failed to patch event: "+err.Error(), http.StatusInternalServerError)
		return
	}

	invalidateCalendarCache()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(event)
}

func handleDeleteEvent(w http.ResponseWriter, r *http.Request) {
	if calClient == nil || !calClient.IsAuthorized() {
		http.Error(w, "Calendar not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	calendarID, _ := url.QueryUnescape(chi.URLParam(r, "calendarID"))
	eventID, _ := url.QueryUnescape(chi.URLParam(r, "eventID"))

	if err := calClient.DeleteEvent(r.Context(), calendarID, eventID); err != nil {
		log.Printf("Error deleting event: %v", err)
		http.Error(w, "Failed to delete event: "+err.Error(), http.StatusInternalServerError)
		return
	}

	invalidateCalendarCache()

	w.WriteHeader(http.StatusNoContent)
}

type MoveEventRequest struct {
	DestinationCalendarID string `json:"destinationCalendarId"`
}

func handleMoveEvent(w http.ResponseWriter, r *http.Request) {
	if calClient == nil || !calClient.IsAuthorized() {
		http.Error(w, "Calendar not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	sourceCalendarID, _ := url.QueryUnescape(chi.URLParam(r, "calendarID"))
	eventID, _ := url.QueryUnescape(chi.URLParam(r, "eventID"))

	var req MoveEventRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if req.DestinationCalendarID == "" {
		http.Error(w, "Destination calendar ID is required", http.StatusBadRequest)
		return
	}

	event, err := calClient.MoveEvent(r.Context(), sourceCalendarID, eventID, req.DestinationCalendarID)
	if err != nil {
		log.Printf("Error moving event: %v", err)
		http.Error(w, "Failed to move event: "+err.Error(), http.StatusInternalServerError)
		return
	}

	invalidateCalendarCache()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(event)
}

func handleGetEventInstances(w http.ResponseWriter, r *http.Request) {
	if calClient == nil || !calClient.IsAuthorized() {
		http.Error(w, "Calendar not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	calendarID, _ := url.QueryUnescape(chi.URLParam(r, "calendarID"))
	eventID, _ := url.QueryUnescape(chi.URLParam(r, "eventID"))

	var timeMin, timeMax time.Time

	if minStr := r.URL.Query().Get("timeMin"); minStr != "" {
		t, err := time.Parse(time.RFC3339, minStr)
		if err == nil {
			timeMin = t
		}
	}

	if maxStr := r.URL.Query().Get("timeMax"); maxStr != "" {
		t, err := time.Parse(time.RFC3339, maxStr)
		if err == nil {
			timeMax = t
		}
	}

	instances, err := calClient.GetEventInstances(r.Context(), calendarID, eventID, timeMin, timeMax)
	if err != nil {
		log.Printf("Error getting event instances: %v", err)
		http.Error(w, "Failed to get event instances: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(instances)
}

func handleGetColors(w http.ResponseWriter, r *http.Request) {
	if calClient == nil || !calClient.IsAuthorized() {
		http.Error(w, "Calendar not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	colors, err := calClient.GetColors(r.Context())
	if err != nil {
		log.Printf("Error getting colors: %v", err)
		http.Error(w, "Failed to get colors: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(colors)
}

func handleGetCalendars(w http.ResponseWriter, r *http.Request) {
	if calClient == nil || !calClient.IsAuthorized() {
		http.Error(w, "Calendar not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	calendars, err := calClient.GetCalendarList(r.Context())
	if err != nil {
		log.Printf("Error getting calendars: %v", err)
		http.Error(w, "Failed to get calendars: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(calendars)
}

func handleGetCalendarPrefs(w http.ResponseWriter, r *http.Request) {
	if calendarPrefs == nil {
		calendarPrefs = &CalendarPrefs{Calendars: make(map[string]CalendarPref)}
	}

	// Return calendars with preferences merged
	calendarsWithPrefs, err := getCalendarsWithPrefs(r.Context())
	if err != nil {
		log.Printf("Error getting calendars with prefs: %v", err)
		http.Error(w, "Failed to get calendars: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(calendarsWithPrefs)
}

func handleUpdateCalendarPref(w http.ResponseWriter, r *http.Request) {
	calendarID := chi.URLParam(r, "calendarID")
	if calendarID == "" {
		http.Error(w, "Calendar ID required", http.StatusBadRequest)
		return
	}

	// Decode URL-encoded calendar ID to match Google Calendar API format
	decodedID, err := url.QueryUnescape(calendarID)
	if err != nil {
		decodedID = calendarID // Fall back to original if decode fails
	}

	var pref CalendarPref
	if err := json.NewDecoder(r.Body).Decode(&pref); err != nil {
		http.Error(w, "Invalid JSON: "+err.Error(), http.StatusBadRequest)
		return
	}

	if calendarPrefs == nil {
		calendarPrefs = &CalendarPrefs{Calendars: make(map[string]CalendarPref)}
	}

	calendarPrefs.Calendars[decodedID] = pref

	if err := saveCalendarPrefs(); err != nil {
		log.Printf("Error saving calendar prefs: %v", err)
		http.Error(w, "Failed to save preferences: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success":    true,
		"calendarId": decodedID,
		"pref":       pref,
	})
}

func handlePlacesAutocomplete(w http.ResponseWriter, r *http.Request) {
	if appConfig.GooglePlacesAPIKey == "" {
		http.Error(w, "Places API not configured", http.StatusServiceUnavailable)
		return
	}

	input := r.URL.Query().Get("input")
	if input == "" {
		http.Error(w, "Input parameter required", http.StatusBadRequest)
		return
	}

	// Build the Google Places API URL
	apiURL := fmt.Sprintf(
		"https://maps.googleapis.com/maps/api/place/autocomplete/json?input=%s&key=%s",
		url.QueryEscape(input),
		appConfig.GooglePlacesAPIKey,
	)

	// Make the request to Google
	resp, err := http.Get(apiURL)
	if err != nil {
		log.Printf("Error calling Places API: %v", err)
		http.Error(w, "Failed to fetch places", http.StatusInternalServerError)
		return
	}
	defer resp.Body.Close()

	// Forward the response
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(resp.StatusCode)

	// Read and forward the body
	var result map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		http.Error(w, "Failed to parse places response", http.StatusInternalServerError)
		return
	}
	json.NewEncoder(w).Encode(result)
}

// repeatToRRule converts user-friendly repeat option to RRULE format
func repeatToRRule(repeat string) string {
	switch repeat {
	case "daily":
		return "RRULE:FREQ=DAILY"
	case "weekly":
		return "RRULE:FREQ=WEEKLY"
	case "monthly":
		return "RRULE:FREQ=MONTHLY"
	case "yearly":
		return "RRULE:FREQ=YEARLY"
	case "weekdays":
		return "RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
	default:
		return ""
	}
}

func getEnv(key, fallback string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return fallback
}

func parseIntEnv(key string, fallback int) int {
	if val := os.Getenv(key); val != "" {
		if i, err := strconv.Atoi(val); err == nil {
			return i
		}
	}
	return fallback
}

// initEntertainmentManagers initializes all entertainment device managers
func initEntertainmentManagers(cfg Config) {
	// Initialize Sony manager
	if len(cfg.SonyDevices) > 0 {
		sonyManager = entertainment.NewSonyManager()
		for _, dev := range cfg.SonyDevices {
			sonyManager.AddDevice(dev.Name, dev.Host, dev.Port, dev.PSK, dev.DeviceType)
		}
		log.Printf("Sony Entertainment manager initialized with %d device(s)", len(cfg.SonyDevices))
	}

	// Initialize Shield manager
	if len(cfg.ShieldDevices) > 0 {
		shieldManager = entertainment.NewShieldManager()
		for _, dev := range cfg.ShieldDevices {
			shieldManager.AddDevice(dev.Name, dev.Host, dev.Port)
		}
		log.Printf("Nvidia Shield manager initialized with %d device(s)", len(cfg.ShieldDevices))
	}

	// Initialize Xbox manager
	if len(cfg.XboxDevices) > 0 {
		xboxManager = entertainment.NewXboxManager(cfg.XboxRESTServerURL)
		for _, dev := range cfg.XboxDevices {
			xboxManager.AddDevice(dev.Name, dev.Host, dev.LiveID)
		}
		log.Printf("Xbox manager initialized with %d device(s)", len(cfg.XboxDevices))
	}

	// Initialize PS5 manager (requires MQTT)
	if len(cfg.PS5Devices) > 0 {
		var pahoClient pahomqtt.Client
		if mqttClient != nil {
			pahoClient = mqttClient.GetPahoClient()
		}
		ps5Manager = entertainment.NewPS5Manager(pahoClient, cfg.PS5MQTTTopic)
		for _, dev := range cfg.PS5Devices {
			ps5Manager.AddDevice(dev.Name, dev.DeviceID, dev.PSNAccount)
		}
		log.Printf("PS5 manager initialized with %d device(s)", len(cfg.PS5Devices))
	}
}

func parseEntities(s string) []string {
	if s == "" {
		return nil
	}
	entities := strings.Split(s, ",")
	for i := range entities {
		entities[i] = strings.TrimSpace(entities[i])
	}
	return entities
}

// parseSyncBoxes parses SYNC_BOXES env var format: "name:ip:token,name2:ip2:token2"
func parseSyncBoxes(s string) []SyncBoxConfig {
	if s == "" {
		return nil
	}
	var boxes []SyncBoxConfig
	for _, entry := range strings.Split(s, ",") {
		entry = strings.TrimSpace(entry)
		parts := strings.SplitN(entry, ":", 3)
		if len(parts) == 3 {
			boxes = append(boxes, SyncBoxConfig{
				Name:        strings.TrimSpace(parts[0]),
				IP:          strings.TrimSpace(parts[1]),
				AccessToken: strings.TrimSpace(parts[2]),
			})
		}
	}
	return boxes
}

// parseSonyDevices parses format: "name:host:port:psk:type,..."
func parseSonyDevices(s string) []SonyDeviceConfig {
	if s == "" {
		return nil
	}
	var devices []SonyDeviceConfig
	for _, entry := range strings.Split(s, ",") {
		entry = strings.TrimSpace(entry)
		parts := strings.SplitN(entry, ":", 5)
		if len(parts) >= 4 {
			port := 10000 // default Sony port
			if len(parts) >= 3 {
				if p, err := strconv.Atoi(strings.TrimSpace(parts[2])); err == nil {
					port = p
				}
			}
			deviceType := "soundbar"
			if len(parts) >= 5 {
				deviceType = strings.TrimSpace(parts[4])
			}
			devices = append(devices, SonyDeviceConfig{
				Name:       strings.TrimSpace(parts[0]),
				Host:       strings.TrimSpace(parts[1]),
				Port:       port,
				PSK:        strings.TrimSpace(parts[3]),
				DeviceType: deviceType,
			})
		}
	}
	return devices
}

// parseShieldDevices parses format: "name:host:port,..."
func parseShieldDevices(s string) []ShieldDeviceConfig {
	if s == "" {
		return nil
	}
	var devices []ShieldDeviceConfig
	for _, entry := range strings.Split(s, ",") {
		entry = strings.TrimSpace(entry)
		parts := strings.SplitN(entry, ":", 3)
		if len(parts) >= 2 {
			port := 5555 // default ADB port
			if len(parts) >= 3 {
				if p, err := strconv.Atoi(strings.TrimSpace(parts[2])); err == nil {
					port = p
				}
			}
			devices = append(devices, ShieldDeviceConfig{
				Name: strings.TrimSpace(parts[0]),
				Host: strings.TrimSpace(parts[1]),
				Port: port,
			})
		}
	}
	return devices
}

// parseXboxDevices parses format: "name:host:liveid,..."
func parseXboxDevices(s string) []XboxDeviceConfig {
	if s == "" {
		return nil
	}
	var devices []XboxDeviceConfig
	for _, entry := range strings.Split(s, ",") {
		entry = strings.TrimSpace(entry)
		parts := strings.SplitN(entry, ":", 3)
		if len(parts) >= 3 {
			devices = append(devices, XboxDeviceConfig{
				Name:   strings.TrimSpace(parts[0]),
				Host:   strings.TrimSpace(parts[1]),
				LiveID: strings.TrimSpace(parts[2]),
			})
		}
	}
	return devices
}

// parsePS5Devices parses format: "name:deviceid:psnaccount,..."
func parsePS5Devices(s string) []PS5DeviceConfig {
	if s == "" {
		return nil
	}
	var devices []PS5DeviceConfig
	for _, entry := range strings.Split(s, ",") {
		entry = strings.TrimSpace(entry)
		parts := strings.SplitN(entry, ":", 3)
		if len(parts) >= 2 {
			psnAccount := ""
			if len(parts) >= 3 {
				psnAccount = strings.TrimSpace(parts[2])
			}
			devices = append(devices, PS5DeviceConfig{
				Name:       strings.TrimSpace(parts[0]),
				DeviceID:   strings.TrimSpace(parts[1]),
				PSNAccount: psnAccount,
			})
		}
	}
	return devices
}

// ========== Entertainment Device Handlers ==========

// handleGetEntertainmentDevices returns all configured entertainment devices
func handleGetEntertainmentDevices(w http.ResponseWriter, r *http.Request) {
	devices := map[string]interface{}{
		"sony":   []interface{}{},
		"shield": []interface{}{},
		"xbox":   []interface{}{},
		"ps5":    []interface{}{},
	}

	if sonyManager != nil {
		devices["sony"] = sonyManager.GetAllStates()
	}
	if shieldManager != nil {
		devices["shield"] = shieldManager.GetAllStates()
	}
	if xboxManager != nil {
		devices["xbox"] = xboxManager.GetAllStates()
	}
	if ps5Manager != nil {
		devices["ps5"] = ps5Manager.GetAllStates()
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(devices)
}

// ========== Sony Handlers ==========

func handleGetSonyDevices(w http.ResponseWriter, r *http.Request) {
	if sonyManager == nil {
		http.Error(w, "Sony devices not configured", http.StatusNotFound)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(sonyManager.GetAllStates())
}

func handleGetSonyState(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if sonyManager == nil {
		http.Error(w, "Sony devices not configured", http.StatusNotFound)
		return
	}
	device := sonyManager.GetDevice(name)
	if device == nil {
		http.Error(w, "Device not found", http.StatusNotFound)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(device.GetState())
}

func handleSonyPower(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if sonyManager == nil {
		http.Error(w, "Sony devices not configured", http.StatusNotFound)
		return
	}
	device := sonyManager.GetDevice(name)
	if device == nil {
		http.Error(w, "Device not found", http.StatusNotFound)
		return
	}

	var req struct {
		Action string `json:"action"` // "on", "off", "toggle"
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var err error
	switch req.Action {
	case "on":
		err = device.PowerOn()
	case "off":
		err = device.PowerOff()
	case "toggle":
		status, _ := device.GetPowerStatus()
		if status != nil && status.Status == "active" {
			err = device.PowerOff()
		} else {
			err = device.PowerOn()
		}
	default:
		http.Error(w, "Invalid action", http.StatusBadRequest)
		return
	}

	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

func handleSonyVolume(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if sonyManager == nil {
		http.Error(w, "Sony devices not configured", http.StatusNotFound)
		return
	}
	device := sonyManager.GetDevice(name)
	if device == nil {
		http.Error(w, "Device not found", http.StatusNotFound)
		return
	}

	var req struct {
		Action string `json:"action"` // "set", "up", "down"
		Value  int    `json:"value"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var err error
	switch req.Action {
	case "set":
		err = device.SetVolume(req.Value)
	case "up":
		step := req.Value
		if step <= 0 {
			step = 1
		}
		err = device.VolumeUp(step)
	case "down":
		step := req.Value
		if step <= 0 {
			step = 1
		}
		err = device.VolumeDown(step)
	default:
		http.Error(w, "Invalid action", http.StatusBadRequest)
		return
	}

	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

func handleSonyMute(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if sonyManager == nil {
		http.Error(w, "Sony devices not configured", http.StatusNotFound)
		return
	}
	device := sonyManager.GetDevice(name)
	if device == nil {
		http.Error(w, "Device not found", http.StatusNotFound)
		return
	}

	var req struct {
		Action string `json:"action"` // "on", "off", "toggle"
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var err error
	switch req.Action {
	case "on":
		err = device.SetMute(true)
	case "off":
		err = device.SetMute(false)
	case "toggle":
		err = device.ToggleMute()
	default:
		http.Error(w, "Invalid action", http.StatusBadRequest)
		return
	}

	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

func handleSonyInput(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if sonyManager == nil {
		http.Error(w, "Sony devices not configured", http.StatusNotFound)
		return
	}
	device := sonyManager.GetDevice(name)
	if device == nil {
		http.Error(w, "Device not found", http.StatusNotFound)
		return
	}

	var req struct {
		Input string `json:"input"` // HDMI port number or URI
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var err error
	// Handle named inputs, HDMI port numbers, or raw URIs
	switch strings.ToLower(req.Input) {
	case "tv":
		err = device.SetTV()
	case "bluetooth", "bt":
		err = device.SetBluetooth()
	case "analog", "line":
		err = device.SetAnalog()
	default:
		// Check if it's a simple HDMI port number
		if port, parseErr := strconv.Atoi(req.Input); parseErr == nil {
			err = device.SetHDMI(port)
		} else {
			// Assume it's a raw URI
			err = device.SetInput(req.Input)
		}
	}

	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

// ========== Shield Handlers ==========

func handleGetShieldDevices(w http.ResponseWriter, r *http.Request) {
	if shieldManager == nil {
		http.Error(w, "Shield devices not configured", http.StatusNotFound)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(shieldManager.GetAllStates())
}

func handleGetShieldState(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if shieldManager == nil {
		http.Error(w, "Shield devices not configured", http.StatusNotFound)
		return
	}
	device := shieldManager.GetDevice(name)
	if device == nil {
		http.Error(w, "Device not found", http.StatusNotFound)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(device.GetState())
}

func handleShieldPower(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if shieldManager == nil {
		http.Error(w, "Shield devices not configured", http.StatusNotFound)
		return
	}
	device := shieldManager.GetDevice(name)
	if device == nil {
		http.Error(w, "Device not found", http.StatusNotFound)
		return
	}

	var req struct {
		Action string `json:"action"` // "wake", "sleep"
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var err error
	switch req.Action {
	case "wake":
		err = device.WakeUp()
	case "sleep":
		err = device.Sleep()
	default:
		http.Error(w, "Invalid action", http.StatusBadRequest)
		return
	}

	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

func handleShieldNavigate(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if shieldManager == nil {
		http.Error(w, "Shield devices not configured", http.StatusNotFound)
		return
	}
	device := shieldManager.GetDevice(name)
	if device == nil {
		http.Error(w, "Device not found", http.StatusNotFound)
		return
	}

	var req struct {
		Action string `json:"action"` // "up", "down", "left", "right", "select", "back", "home", "menu"
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var err error
	switch req.Action {
	case "up":
		err = device.Up()
	case "down":
		err = device.Down()
	case "left":
		err = device.Left()
	case "right":
		err = device.Right()
	case "select", "enter":
		err = device.Select()
	case "back":
		err = device.Back()
	case "home":
		err = device.Home()
	case "menu":
		err = device.Menu()
	default:
		http.Error(w, "Invalid action", http.StatusBadRequest)
		return
	}

	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

func handleShieldMedia(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if shieldManager == nil {
		http.Error(w, "Shield devices not configured", http.StatusNotFound)
		return
	}
	device := shieldManager.GetDevice(name)
	if device == nil {
		http.Error(w, "Device not found", http.StatusNotFound)
		return
	}

	var req struct {
		Action string `json:"action"` // "play", "pause", "playpause", "stop", "next", "previous", "rewind", "forward"
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var err error
	switch req.Action {
	case "play":
		err = device.Play()
	case "pause":
		err = device.Pause()
	case "playpause":
		err = device.PlayPause()
	case "stop":
		err = device.Stop()
	case "next":
		err = device.Next()
	case "previous":
		err = device.Previous()
	case "rewind":
		err = device.Rewind()
	case "forward":
		err = device.FastForward()
	case "volumeup":
		err = device.VolumeUp()
	case "volumedown":
		err = device.VolumeDown()
	case "mute":
		err = device.Mute()
	default:
		http.Error(w, "Invalid action", http.StatusBadRequest)
		return
	}

	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

func handleShieldApp(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if shieldManager == nil {
		http.Error(w, "Shield devices not configured", http.StatusNotFound)
		return
	}
	device := shieldManager.GetDevice(name)
	if device == nil {
		http.Error(w, "Device not found", http.StatusNotFound)
		return
	}

	var req struct {
		Action  string `json:"action"`  // "launch", "stop"
		Package string `json:"package"` // App name or package
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var err error
	switch req.Action {
	case "launch":
		err = device.LaunchApp(req.Package)
	case "stop":
		err = device.ForceStopApp(req.Package)
	default:
		http.Error(w, "Invalid action", http.StatusBadRequest)
		return
	}

	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

// ========== Xbox Handlers ==========

func handleGetXboxDevices(w http.ResponseWriter, r *http.Request) {
	if xboxManager == nil {
		http.Error(w, "Xbox devices not configured", http.StatusNotFound)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(xboxManager.GetAllStates())
}

func handleGetXboxState(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if xboxManager == nil {
		http.Error(w, "Xbox devices not configured", http.StatusNotFound)
		return
	}
	device := xboxManager.GetDevice(name)
	if device == nil {
		http.Error(w, "Device not found", http.StatusNotFound)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(device.GetState())
}

func handleXboxPower(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if xboxManager == nil {
		http.Error(w, "Xbox devices not configured", http.StatusNotFound)
		return
	}
	device := xboxManager.GetDevice(name)
	if device == nil {
		http.Error(w, "Device not found", http.StatusNotFound)
		return
	}

	var req struct {
		Action string `json:"action"` // "on", "off"
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var err error
	switch req.Action {
	case "on":
		err = device.PowerOn()
	case "off":
		err = xboxManager.PowerOffViaREST(name)
	default:
		http.Error(w, "Invalid action", http.StatusBadRequest)
		return
	}

	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

func handleXboxInput(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if xboxManager == nil {
		http.Error(w, "Xbox devices not configured", http.StatusNotFound)
		return
	}

	var req struct {
		Button string `json:"button"` // Button name
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	if err := xboxManager.SendButton(name, req.Button); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

func handleXboxMedia(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if xboxManager == nil {
		http.Error(w, "Xbox devices not configured", http.StatusNotFound)
		return
	}

	var req struct {
		Action string `json:"action"` // "play", "pause", "playpause", "stop", "next", "previous"
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var err error
	switch req.Action {
	case "play":
		err = xboxManager.Play(name)
	case "pause":
		err = xboxManager.Pause(name)
	case "playpause":
		err = xboxManager.PlayPause(name)
	case "stop":
		err = xboxManager.Stop(name)
	case "next":
		err = xboxManager.Next(name)
	case "previous":
		err = xboxManager.Previous(name)
	default:
		http.Error(w, "Invalid action", http.StatusBadRequest)
		return
	}

	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

// ========== PS5 Handlers ==========

func handleGetPS5Devices(w http.ResponseWriter, r *http.Request) {
	if ps5Manager == nil {
		http.Error(w, "PS5 devices not configured", http.StatusNotFound)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(ps5Manager.GetAllStates())
}

func handleGetPS5State(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if ps5Manager == nil {
		http.Error(w, "PS5 devices not configured", http.StatusNotFound)
		return
	}
	state := ps5Manager.GetState(name)
	if state == nil {
		http.Error(w, "Device not found", http.StatusNotFound)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(state)
}

func handlePS5Power(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	if ps5Manager == nil {
		http.Error(w, "PS5 devices not configured", http.StatusNotFound)
		return
	}

	var req struct {
		Action string `json:"action"` // "on", "off", "toggle"
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var err error
	switch req.Action {
	case "on":
		err = ps5Manager.PowerOn(name)
	case "off":
		err = ps5Manager.PowerOff(name)
	case "toggle":
		err = ps5Manager.TogglePower(name)
	default:
		http.Error(w, "Invalid action", http.StatusBadRequest)
		return
	}

	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

// Template functions
func formatDate(t time.Time) string {
	return t.In(appConfig.Timezone).Format("Mon, Jan 2")
}

func formatTime(t time.Time) string {
	return t.In(appConfig.Timezone).Format("3:04 PM")
}

func formatDateTime(t time.Time) string {
	return t.In(appConfig.Timezone).Format("Mon, Jan 2 at 3:04 PM")
}

func isToday(t time.Time) bool {
	now := time.Now().In(appConfig.Timezone)
	local := t.In(appConfig.Timezone)
	return local.Year() == now.Year() && local.YearDay() == now.YearDay()
}

func isTomorrow(t time.Time) bool {
	tomorrow := time.Now().In(appConfig.Timezone).AddDate(0, 0, 1)
	local := t.In(appConfig.Timezone)
	return local.Year() == tomorrow.Year() && local.YearDay() == tomorrow.YearDay()
}

func dayName(t time.Time) string {
	local := t.In(appConfig.Timezone)
	if isToday(t) {
		return "Today"
	}
	if isTomorrow(t) {
		return "Tomorrow"
	}
	return fmt.Sprintf("%s, %s %d", local.Weekday().String(), local.Month().String()[:3], local.Day())
}

// Tasks API handlers

func handleGetTasks(w http.ResponseWriter, r *http.Request) {
	if tasksClient == nil || !tasksClient.IsAuthorized() {
		http.Error(w, "Tasks not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	listID := r.URL.Query().Get("listId")
	tasksList, err := tasksClient.GetTasks(r.Context(), listID)
	if err != nil {
		log.Printf("Error getting tasks: %v", err)
		http.Error(w, "Failed to get tasks: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(tasksList)
}

func handleGetTaskLists(w http.ResponseWriter, r *http.Request) {
	if tasksClient == nil || !tasksClient.IsAuthorized() {
		http.Error(w, "Tasks not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	lists, err := tasksClient.GetTaskLists(r.Context())
	if err != nil {
		log.Printf("Error getting task lists: %v", err)
		http.Error(w, "Failed to get task lists: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(lists)
}

type CreateTaskRequest struct {
	ListID string     `json:"listId"`
	Title  string     `json:"title"`
	Notes  string     `json:"notes"`
	Due    *time.Time `json:"due,omitempty"`
}

func handleCreateTask(w http.ResponseWriter, r *http.Request) {
	if tasksClient == nil || !tasksClient.IsAuthorized() {
		http.Error(w, "Tasks not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	var req CreateTaskRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if req.Title == "" {
		http.Error(w, "Title is required", http.StatusBadRequest)
		return
	}

	task, err := tasksClient.CreateTask(r.Context(), req.ListID, req.Title, req.Notes, req.Due)
	if err != nil {
		log.Printf("Error creating task: %v", err)
		http.Error(w, "Failed to create task: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(task)
}

func handleToggleTask(w http.ResponseWriter, r *http.Request) {
	if tasksClient == nil || !tasksClient.IsAuthorized() {
		http.Error(w, "Tasks not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	listID := chi.URLParam(r, "listID")
	taskID := chi.URLParam(r, "taskID")

	task, err := tasksClient.ToggleTask(r.Context(), listID, taskID)
	if err != nil {
		log.Printf("Error toggling task: %v", err)
		http.Error(w, "Failed to toggle task: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(task)
}

func handleDeleteTask(w http.ResponseWriter, r *http.Request) {
	if tasksClient == nil || !tasksClient.IsAuthorized() {
		http.Error(w, "Tasks not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	listID := chi.URLParam(r, "listID")
	taskID := chi.URLParam(r, "taskID")

	if err := tasksClient.DeleteTask(r.Context(), listID, taskID); err != nil {
		log.Printf("Error deleting task: %v", err)
		http.Error(w, "Failed to delete task: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleClearCompleted(w http.ResponseWriter, r *http.Request) {
	if tasksClient == nil || !tasksClient.IsAuthorized() {
		http.Error(w, "Tasks not configured or not authorized", http.StatusServiceUnavailable)
		return
	}

	listID := chi.URLParam(r, "listID")

	if err := tasksClient.ClearCompleted(r.Context(), listID); err != nil {
		log.Printf("Error clearing completed tasks: %v", err)
		http.Error(w, "Failed to clear completed tasks: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// Weather API handler

func handleGetWeather(w http.ResponseWriter, r *http.Request) {
	if weatherClient == nil {
		http.Error(w, "Weather not configured", http.StatusServiceUnavailable)
		return
	}

	data := weatherClient.GetWeather()
	if data == nil {
		http.Error(w, "Weather data not available yet", http.StatusServiceUnavailable)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(data)
}

// WebSocket handler
func handleWebSocket(w http.ResponseWriter, r *http.Request) {
	wsHub.ServeWS(w, r)
}

// Camera API handlers
func handleCameraSnapshot(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	cameraManager.ProxySnapshot(w, r, name)
}

func handleCameraStream(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")
	cameraManager.ProxyMJPEG(w, r, name)
}

func handleCameraTalk(w http.ResponseWriter, r *http.Request) {
	name := chi.URLParam(r, "name")

	// Read the PCM audio data from the request body
	// Expected format: 16-bit PCM, mono, 8kHz sample rate
	pcmData, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Failed to read audio data", http.StatusBadRequest)
		return
	}

	if len(pcmData) == 0 {
		http.Error(w, "No audio data received", http.StatusBadRequest)
		return
	}

	// Send audio to camera
	err = cameraManager.PostAudio(name, pcmData)
	if err != nil {
		log.Printf("Failed to post audio to camera %s: %v", name, err)
		http.Error(w, fmt.Sprintf("Failed to send audio: %v", err), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
	w.Write([]byte("Audio sent successfully"))
}

func handleGetCameras(w http.ResponseWriter, r *http.Request) {
	cameras := cameraManager.GetCameras()
	cameraList := make([]map[string]string, 0)
	for _, cam := range cameras {
		cameraList = append(cameraList, map[string]string{
			"name": cam.Name,
		})
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(cameraList)
}

func handleTestDoorbell(w http.ResponseWriter, r *http.Request) {
	go wakeTablet() // Wake tablet screen first
	wsHub.BroadcastDoorbell(appConfig.DoorbellCamera)
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("Doorbell event broadcast"))
}

func handleDoorbellWebhook(w http.ResponseWriter, r *http.Request) {
	// Check webhook secret if configured
	if appConfig.WebhookSecret != "" {
		// Check Authorization header (Bearer token)
		authHeader := r.Header.Get("Authorization")
		if authHeader != "" {
			if authHeader != "Bearer "+appConfig.WebhookSecret {
				http.Error(w, "Unauthorized", http.StatusUnauthorized)
				return
			}
		} else {
			// Also check X-Webhook-Secret header
			secret := r.Header.Get("X-Webhook-Secret")
			if secret != appConfig.WebhookSecret {
				http.Error(w, "Unauthorized", http.StatusUnauthorized)
				return
			}
		}
	}

	log.Println("Doorbell webhook triggered from Home Assistant")
	go wakeTablet() // Wake tablet screen first
	wsHub.BroadcastDoorbell(appConfig.DoorbellCamera)
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}

// Hue API handlers

func handleGetHueRooms(w http.ResponseWriter, r *http.Request) {
	if hueClient == nil {
		http.Error(w, "Hue bridge not configured", http.StatusServiceUnavailable)
		return
	}

	rooms, err := hueClient.GetRoomsWithDetails()
	if err != nil {
		log.Printf("Error fetching Hue rooms: %v", err)
		http.Error(w, "Failed to fetch Hue rooms: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(rooms)
}

func handleToggleHueLight(w http.ResponseWriter, r *http.Request) {
	if hueClient == nil {
		http.Error(w, "Hue bridge not configured", http.StatusServiceUnavailable)
		return
	}

	id := chi.URLParam(r, "id")
	if id == "" {
		http.Error(w, "Missing light ID", http.StatusBadRequest)
		return
	}

	if err := hueClient.ToggleLight(id); err != nil {
		log.Printf("Error toggling Hue light %s: %v", id, err)
		http.Error(w, "Failed to toggle light: "+err.Error(), http.StatusInternalServerError)
		return
	}

	// Return updated light state
	light, err := hueClient.GetLight(id)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(light)
}

type SetHueBrightnessRequest struct {
	Brightness int `json:"brightness"` // 1-254
}

func handleSetHueLightBrightness(w http.ResponseWriter, r *http.Request) {
	if hueClient == nil {
		http.Error(w, "Hue bridge not configured", http.StatusServiceUnavailable)
		return
	}

	id := chi.URLParam(r, "id")
	if id == "" {
		http.Error(w, "Missing light ID", http.StatusBadRequest)
		return
	}

	var req SetHueBrightnessRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := hueClient.SetLightBrightness(id, req.Brightness); err != nil {
		log.Printf("Error setting Hue light %s brightness: %v", id, err)
		http.Error(w, "Failed to set brightness: "+err.Error(), http.StatusInternalServerError)
		return
	}

	light, err := hueClient.GetLight(id)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(light)
}

func handleToggleHueGroup(w http.ResponseWriter, r *http.Request) {
	if hueClient == nil {
		http.Error(w, "Hue bridge not configured", http.StatusServiceUnavailable)
		return
	}

	id := chi.URLParam(r, "id")
	if id == "" {
		http.Error(w, "Missing group ID", http.StatusBadRequest)
		return
	}

	if err := hueClient.ToggleGroup(id); err != nil {
		log.Printf("Error toggling Hue group %s: %v", id, err)
		http.Error(w, "Failed to toggle group: "+err.Error(), http.StatusInternalServerError)
		return
	}

	// Return updated rooms
	rooms, err := hueClient.GetRoomsWithDetails()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(rooms)
}

func handleSetHueGroupBrightness(w http.ResponseWriter, r *http.Request) {
	if hueClient == nil {
		http.Error(w, "Hue bridge not configured", http.StatusServiceUnavailable)
		return
	}

	id := chi.URLParam(r, "id")
	if id == "" {
		http.Error(w, "Missing group ID", http.StatusBadRequest)
		return
	}

	var req SetHueBrightnessRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := hueClient.SetGroupBrightness(id, req.Brightness); err != nil {
		log.Printf("Error setting Hue group %s brightness: %v", id, err)
		http.Error(w, "Failed to set brightness: "+err.Error(), http.StatusInternalServerError)
		return
	}

	rooms, err := hueClient.GetRoomsWithDetails()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(rooms)
}

func handleActivateHueScene(w http.ResponseWriter, r *http.Request) {
	if hueClient == nil {
		http.Error(w, "Hue bridge not configured", http.StatusServiceUnavailable)
		return
	}

	id := chi.URLParam(r, "id")
	if id == "" {
		http.Error(w, "Missing scene ID", http.StatusBadRequest)
		return
	}

	if err := hueClient.ActivateScene(id); err != nil {
		log.Printf("Error activating Hue scene %s: %v", id, err)
		http.Error(w, "Failed to activate scene: "+err.Error(), http.StatusInternalServerError)
		return
	}

	// Return updated rooms
	rooms, err := hueClient.GetRoomsWithDetails()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(rooms)
}

func handleActivateEntertainment(w http.ResponseWriter, r *http.Request) {
	if hueClient == nil {
		http.Error(w, "Hue bridge not configured", http.StatusServiceUnavailable)
		return
	}

	id := chi.URLParam(r, "id")
	if id == "" {
		http.Error(w, "Missing entertainment area ID", http.StatusBadRequest)
		return
	}

	// Just select the area locally (informational only)
	// Actual streaming control is done via Sync Box API
	if hueStreamer != nil {
		if err := hueStreamer.SelectArea(id); err != nil {
			log.Printf("Error selecting entertainment area %s: %v", id, err)
			http.Error(w, "Failed to select entertainment area: "+err.Error(), http.StatusInternalServerError)
			return
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "selected", "id": id})
}

func handleDeactivateEntertainment(w http.ResponseWriter, r *http.Request) {
	if hueStreamer == nil {
		http.Error(w, "Entertainment streaming not configured", http.StatusServiceUnavailable)
		return
	}

	if err := hueStreamer.Deactivate(); err != nil {
		log.Printf("Error deactivating entertainment: %v", err)
		http.Error(w, "Failed to deactivate entertainment: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "deactivated"})
}

func handleGetEntertainmentStatus(w http.ResponseWriter, r *http.Request) {
	status := map[string]interface{}{
		"streaming":  false,
		"activeArea": "",
		"enabled":    hueStreamer != nil,
	}

	if hueStreamer != nil {
		status["streaming"] = hueStreamer.IsStreaming()
		status["activeArea"] = hueStreamer.GetActiveArea()
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(status)
}

// Sync Box handlers

func handleGetSyncBoxes(w http.ResponseWriter, r *http.Request) {
	type SyncBoxInfo struct {
		Index int    `json:"index"`
		Name  string `json:"name"`
		IP    string `json:"ip"`
	}

	boxes := make([]SyncBoxInfo, len(syncBoxClients))
	for i, client := range syncBoxClients {
		boxes[i] = SyncBoxInfo{
			Index: i,
			Name:  client.GetName(),
			IP:    client.GetIP(),
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(boxes)
}

func getSyncBoxClient(r *http.Request) (*syncbox.Client, error) {
	indexStr := chi.URLParam(r, "index")
	index, err := strconv.Atoi(indexStr)
	if err != nil || index < 0 || index >= len(syncBoxClients) {
		return nil, fmt.Errorf("invalid sync box index")
	}
	return syncBoxClients[index], nil
}

func handleGetSyncBoxStatus(w http.ResponseWriter, r *http.Request) {
	indexStr := chi.URLParam(r, "index")
	index, err := strconv.Atoi(indexStr)
	if err != nil {
		http.Error(w, "Invalid sync box index", http.StatusBadRequest)
		return
	}

	if index < 0 || index >= len(syncBoxClients) {
		http.Error(w, "Sync box index out of range", http.StatusBadRequest)
		return
	}

	client := syncBoxClients[index]

	// Check cache first
	syncBoxCache.RLock()
	cachedStatus := syncBoxCache.Statuses[index]
	updatedAt := syncBoxCache.UpdatedAt[index]
	syncBoxCache.RUnlock()

	// If cache is fresh, return it immediately
	if cachedStatus != nil && time.Since(updatedAt) < syncBoxCache.CacheDuration {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(cachedStatus)
		return
	}

	// Fetch fresh status
	status, err := client.GetStatus()
	if err != nil {
		// Only log errors once per minute to reduce spam
		syncBoxCache.Lock()
		lastErr := syncBoxCache.LastError[index]
		if time.Since(lastErr) > time.Minute {
			log.Printf("Error getting sync box %d status: %v", index, err)
			syncBoxCache.LastError[index] = time.Now()
		}
		syncBoxCache.Unlock()

		// Return cached status if available (even if stale)
		if cachedStatus != nil {
			w.Header().Set("Content-Type", "application/json")
			json.NewEncoder(w).Encode(cachedStatus)
			return
		}

		http.Error(w, "Failed to get sync box status: "+err.Error(), http.StatusInternalServerError)
		return
	}

	// Update cache
	syncBoxCache.Lock()
	syncBoxCache.Statuses[index] = status
	syncBoxCache.UpdatedAt[index] = time.Now()
	syncBoxCache.Unlock()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(status)
}

// invalidateSyncBoxCache clears the cache for a sync box so the next request fetches fresh data
func invalidateSyncBoxCache(index int) {
	syncBoxCache.Lock()
	delete(syncBoxCache.UpdatedAt, index)
	syncBoxCache.Unlock()
}

func handleSetSyncBoxSync(w http.ResponseWriter, r *http.Request) {
	indexStr := chi.URLParam(r, "index")
	index, _ := strconv.Atoi(indexStr)

	client, err := getSyncBoxClient(r)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var req struct {
		Active bool `json:"active"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := client.SetSyncActive(req.Active); err != nil {
		log.Printf("Error setting sync box sync state: %v", err)
		http.Error(w, "Failed to set sync state: "+err.Error(), http.StatusInternalServerError)
		return
	}

	invalidateSyncBoxCache(index)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]bool{"active": req.Active})
}

func handleSetSyncBoxArea(w http.ResponseWriter, r *http.Request) {
	indexStr := chi.URLParam(r, "index")
	index, _ := strconv.Atoi(indexStr)

	client, err := getSyncBoxClient(r)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var req struct {
		GroupID string `json:"groupId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := client.SetEntertainmentArea(req.GroupID); err != nil {
		log.Printf("Error setting sync box entertainment area: %v", err)
		http.Error(w, "Failed to set entertainment area: "+err.Error(), http.StatusInternalServerError)
		return
	}

	invalidateSyncBoxCache(index)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"groupId": req.GroupID})
}

func handleSetSyncBoxMode(w http.ResponseWriter, r *http.Request) {
	indexStr := chi.URLParam(r, "index")
	index, _ := strconv.Atoi(indexStr)

	client, err := getSyncBoxClient(r)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var req struct {
		Mode string `json:"mode"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := client.SetMode(req.Mode); err != nil {
		log.Printf("Error setting sync box mode: %v", err)
		http.Error(w, "Failed to set mode: "+err.Error(), http.StatusInternalServerError)
		return
	}

	invalidateSyncBoxCache(index)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"mode": req.Mode})
}

func handleSetSyncBoxBrightness(w http.ResponseWriter, r *http.Request) {
	indexStr := chi.URLParam(r, "index")
	index, _ := strconv.Atoi(indexStr)

	client, err := getSyncBoxClient(r)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var req struct {
		Brightness int `json:"brightness"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := client.SetBrightness(req.Brightness); err != nil {
		log.Printf("Error setting sync box brightness: %v", err)
		http.Error(w, "Failed to set brightness: "+err.Error(), http.StatusInternalServerError)
		return
	}

	invalidateSyncBoxCache(index)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]int{"brightness": req.Brightness})
}

func handleSetSyncBoxInput(w http.ResponseWriter, r *http.Request) {
	indexStr := chi.URLParam(r, "index")
	index, _ := strconv.Atoi(indexStr)

	client, err := getSyncBoxClient(r)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	var req struct {
		HDMISource string `json:"hdmiSource"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := client.SetHDMISource(req.HDMISource); err != nil {
		log.Printf("Error setting sync box HDMI source: %v", err)
		http.Error(w, "Failed to set HDMI source: "+err.Error(), http.StatusInternalServerError)
		return
	}

	invalidateSyncBoxCache(index)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"hdmiSource": req.HDMISource})
}

// Google Drive handlers for photos (screensaver and background use same folder)

func handleGetDrivePhotos(w http.ResponseWriter, r *http.Request) {
	if driveClient == nil {
		http.Error(w, "Drive client not initialized", http.StatusServiceUnavailable)
		return
	}

	photos, err := driveClient.GetPhotos(r.Context())
	if err != nil {
		log.Printf("Error fetching photos: %v", err)
		http.Error(w, "Failed to fetch photos: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(photos)
}

func handleGetRandomDrivePhoto(w http.ResponseWriter, r *http.Request) {
	if driveClient == nil {
		http.Error(w, "Drive client not initialized", http.StatusServiceUnavailable)
		return
	}

	photo, err := driveClient.GetRandomPhoto(r.Context())
	if err != nil {
		log.Printf("Error fetching random photo: %v", err)
		http.Error(w, "Failed to fetch photo: "+err.Error(), http.StatusInternalServerError)
		return
	}

	if photo == nil {
		http.Error(w, "No photos available", http.StatusNotFound)
		return
	}

	// Return the photo with a direct URL
	response := map[string]interface{}{
		"id":   photo.ID,
		"name": photo.Name,
		"url":  driveClient.GetPhotoURL(photo.ID),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func handleGetDrivePhoto(w http.ResponseWriter, r *http.Request) {
	if driveClient == nil {
		http.Error(w, "Drive client not initialized", http.StatusServiceUnavailable)
		return
	}

	photoID := chi.URLParam(r, "id")
	if photoID == "" {
		http.Error(w, "Photo ID required", http.StatusBadRequest)
		return
	}

	// Download and proxy the image through our authenticated client
	data, contentType, err := driveClient.GetFileContent(r.Context(), photoID)
	if err != nil {
		log.Printf("Error fetching photo %s: %v", photoID, err)
		http.Error(w, "Failed to fetch photo", http.StatusInternalServerError)
		return
	}

	// Set cache headers for performance
	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Cache-Control", "public, max-age=3600")
	w.Write(data)
}

func handleGetScreensaverConfig(w http.ResponseWriter, r *http.Request) {
	config := map[string]interface{}{
		"timeout":         appConfig.ScreensaverTimeout,
		"hasPhotosFolder": driveClient != nil && driveClient.HasPhotosFolder(),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(config)
}

// Spotify handlers

func handleSpotifyAuth(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil {
		http.Error(w, "Spotify not configured", http.StatusServiceUnavailable)
		return
	}

	state := fmt.Sprintf("%d", time.Now().UnixNano())
	authURL := spotifyClient.GetAuthURL(state)
	http.Redirect(w, r, authURL, http.StatusTemporaryRedirect)
}

func handleSpotifyCallback(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil {
		http.Error(w, "Spotify not configured", http.StatusServiceUnavailable)
		return
	}

	code := r.URL.Query().Get("code")
	if code == "" {
		errMsg := r.URL.Query().Get("error")
		http.Error(w, "Authorization failed: "+errMsg, http.StatusBadRequest)
		return
	}

	_, err := spotifyClient.Exchange(r.Context(), code)
	if err != nil {
		log.Printf("Spotify token exchange failed: %v", err)
		http.Error(w, "Token exchange failed: "+err.Error(), http.StatusInternalServerError)
		return
	}

	log.Println("Spotify authorization successful")
	http.Redirect(w, r, "/home", http.StatusTemporaryRedirect)
}

func handleSpotifyStatus(w http.ResponseWriter, r *http.Request) {
	status := map[string]interface{}{
		"configured":    spotifyClient != nil,
		"authenticated": spotifyClient != nil && spotifyClient.IsAuthenticated(),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(status)
}

func handleSpotifyPlayback(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	state, err := spotifyClient.GetPlaybackState(r.Context())
	if err != nil {
		log.Printf("Error getting playback state: %v", err)
		http.Error(w, "Failed to get playback state: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(state)
}

func handleSpotifyDevices(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	devices, err := spotifyClient.GetDevices(r.Context())
	if err != nil {
		log.Printf("Error getting devices: %v", err)
		http.Error(w, "Failed to get devices: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(devices)
}

func handleSpotifyPlay(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	var req struct {
		DeviceID string `json:"device_id"`
		URI      string `json:"uri"`
		Position int    `json:"position"`
	}
	json.NewDecoder(r.Body).Decode(&req)

	var err error
	if req.URI != "" {
		err = spotifyClient.PlayURI(r.Context(), req.DeviceID, req.URI, req.Position)
	} else {
		err = spotifyClient.Play(r.Context(), req.DeviceID)
	}

	if err != nil {
		log.Printf("Error starting playback: %v", err)
		http.Error(w, "Failed to start playback: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleSpotifyPause(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	var req struct {
		DeviceID string `json:"device_id"`
	}
	json.NewDecoder(r.Body).Decode(&req)

	if err := spotifyClient.Pause(r.Context(), req.DeviceID); err != nil {
		log.Printf("Error pausing playback: %v", err)
		http.Error(w, "Failed to pause: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleSpotifyNext(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	var req struct {
		DeviceID string `json:"device_id"`
	}
	json.NewDecoder(r.Body).Decode(&req)

	if err := spotifyClient.Next(r.Context(), req.DeviceID); err != nil {
		log.Printf("Error skipping to next: %v", err)
		http.Error(w, "Failed to skip: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleSpotifyPrevious(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	var req struct {
		DeviceID string `json:"device_id"`
	}
	json.NewDecoder(r.Body).Decode(&req)

	if err := spotifyClient.Previous(r.Context(), req.DeviceID); err != nil {
		log.Printf("Error going to previous: %v", err)
		http.Error(w, "Failed to go to previous: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleSpotifyVolume(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	var req struct {
		DeviceID      string `json:"device_id"`
		VolumePercent int    `json:"volume_percent"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := spotifyClient.SetVolume(r.Context(), req.DeviceID, req.VolumePercent); err != nil {
		log.Printf("Error setting volume: %v", err)
		http.Error(w, "Failed to set volume: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleSpotifySeek(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	var req struct {
		DeviceID   string `json:"device_id"`
		PositionMS int    `json:"position_ms"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := spotifyClient.Seek(r.Context(), req.DeviceID, req.PositionMS); err != nil {
		log.Printf("Error seeking: %v", err)
		http.Error(w, "Failed to seek: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleSpotifyShuffle(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	var req struct {
		DeviceID string `json:"device_id"`
		State    bool   `json:"state"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := spotifyClient.SetShuffle(r.Context(), req.DeviceID, req.State); err != nil {
		log.Printf("Error setting shuffle: %v", err)
		http.Error(w, "Failed to set shuffle: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleSpotifyRepeat(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	var req struct {
		DeviceID string `json:"device_id"`
		State    string `json:"state"` // track, context, off
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := spotifyClient.SetRepeat(r.Context(), req.DeviceID, req.State); err != nil {
		log.Printf("Error setting repeat: %v", err)
		http.Error(w, "Failed to set repeat: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleSpotifyTransfer(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	var req struct {
		DeviceID string `json:"device_id"`
		Play     bool   `json:"play"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if req.DeviceID == "" {
		http.Error(w, "device_id required", http.StatusBadRequest)
		return
	}

	if err := spotifyClient.TransferPlayback(r.Context(), req.DeviceID, req.Play); err != nil {
		log.Printf("Error transferring playback: %v", err)
		http.Error(w, "Failed to transfer playback: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleSpotifyPlaylists(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	limit := 50
	offset := 0
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil {
			limit = parsed
		}
	}
	if o := r.URL.Query().Get("offset"); o != "" {
		if parsed, err := strconv.Atoi(o); err == nil {
			offset = parsed
		}
	}

	playlists, total, err := spotifyClient.GetPlaylists(r.Context(), limit, offset)
	if err != nil {
		log.Printf("Error getting playlists: %v", err)
		http.Error(w, "Failed to get playlists: "+err.Error(), http.StatusInternalServerError)
		return
	}

	response := map[string]interface{}{
		"items":  playlists,
		"total":  total,
		"limit":  limit,
		"offset": offset,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func handleSpotifyPlaylistTracks(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	playlistID := chi.URLParam(r, "id")
	if playlistID == "" {
		http.Error(w, "Playlist ID required", http.StatusBadRequest)
		return
	}

	limit := 50
	offset := 0
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil {
			limit = parsed
		}
	}
	if o := r.URL.Query().Get("offset"); o != "" {
		if parsed, err := strconv.Atoi(o); err == nil {
			offset = parsed
		}
	}

	tracks, total, err := spotifyClient.GetPlaylistTracks(r.Context(), playlistID, limit, offset)
	if err != nil {
		log.Printf("Error getting playlist tracks: %v", err)
		http.Error(w, "Failed to get tracks: "+err.Error(), http.StatusInternalServerError)
		return
	}

	response := map[string]interface{}{
		"items":  tracks,
		"total":  total,
		"limit":  limit,
		"offset": offset,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func handleSpotifySearch(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	query := r.URL.Query().Get("q")
	if query == "" {
		http.Error(w, "Search query required", http.StatusBadRequest)
		return
	}

	types := []string{"track", "album", "artist", "playlist"}
	if t := r.URL.Query().Get("type"); t != "" {
		types = strings.Split(t, ",")
	}

	limit := 20
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil {
			limit = parsed
		}
	}

	results, err := spotifyClient.Search(r.Context(), query, types, limit)
	if err != nil {
		log.Printf("Error searching: %v", err)
		http.Error(w, "Search failed: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(results)
}

func handleSpotifyRecentlyPlayed(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	limit := 20
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil {
			limit = parsed
		}
	}

	items, err := spotifyClient.GetRecentlyPlayed(r.Context(), limit)
	if err != nil {
		log.Printf("Error getting recently played: %v", err)
		http.Error(w, "Failed to get recently played: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"items": items,
	})
}

func handleSpotifyTopArtists(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	limit := 20
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil {
			limit = parsed
		}
	}

	timeRange := r.URL.Query().Get("time_range")
	if timeRange == "" {
		timeRange = "medium_term"
	}

	artists, err := spotifyClient.GetTopArtists(r.Context(), limit, timeRange)
	if err != nil {
		log.Printf("Error getting top artists: %v", err)
		http.Error(w, "Failed to get top artists: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"items": artists,
	})
}

func handleSpotifyTopTracks(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	limit := 20
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil {
			limit = parsed
		}
	}

	timeRange := r.URL.Query().Get("time_range")
	if timeRange == "" {
		timeRange = "medium_term"
	}

	tracks, err := spotifyClient.GetTopTracks(r.Context(), limit, timeRange)
	if err != nil {
		log.Printf("Error getting top tracks: %v", err)
		http.Error(w, "Failed to get top tracks: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"items": tracks,
	})
}

func handleSpotifyAlbum(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	albumID := chi.URLParam(r, "id")
	if albumID == "" {
		http.Error(w, "Album ID required", http.StatusBadRequest)
		return
	}

	album, err := spotifyClient.GetAlbum(r.Context(), albumID)
	if err != nil {
		log.Printf("Error getting album: %v", err)
		http.Error(w, "Failed to get album: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(album)
}

func handleSpotifyArtist(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	artistID := chi.URLParam(r, "id")
	if artistID == "" {
		http.Error(w, "Artist ID required", http.StatusBadRequest)
		return
	}

	artist, err := spotifyClient.GetArtist(r.Context(), artistID)
	if err != nil {
		log.Printf("Error getting artist: %v", err)
		http.Error(w, "Failed to get artist: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(artist)
}

func handleSpotifyArtistAlbums(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	artistID := chi.URLParam(r, "id")
	if artistID == "" {
		http.Error(w, "Artist ID required", http.StatusBadRequest)
		return
	}

	limit := 20
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil {
			limit = parsed
		}
	}

	albums, err := spotifyClient.GetArtistAlbums(r.Context(), artistID, limit)
	if err != nil {
		log.Printf("Error getting artist albums: %v", err)
		http.Error(w, "Failed to get artist albums: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"items": albums,
	})
}

func handleSpotifyArtistTopTracks(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	artistID := chi.URLParam(r, "id")
	if artistID == "" {
		http.Error(w, "Artist ID required", http.StatusBadRequest)
		return
	}

	market := r.URL.Query().Get("market")
	if market == "" {
		market = "US"
	}

	tracks, err := spotifyClient.GetArtistTopTracks(r.Context(), artistID, market)
	if err != nil {
		log.Printf("Error getting artist top tracks: %v", err)
		http.Error(w, "Failed to get artist top tracks: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"tracks": tracks,
	})
}

func handleSpotifyAlbumSaved(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	albumID := chi.URLParam(r, "id")
	if albumID == "" {
		http.Error(w, "Album ID required", http.StatusBadRequest)
		return
	}

	saved, err := spotifyClient.CheckAlbumSaved(r.Context(), albumID)
	if err != nil {
		log.Printf("Error checking album saved: %v", err)
		http.Error(w, "Failed to check album saved: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]bool{"saved": saved})
}

func handleSpotifyAlbumSave(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	albumID := chi.URLParam(r, "id")
	if albumID == "" {
		http.Error(w, "Album ID required", http.StatusBadRequest)
		return
	}

	if err := spotifyClient.SaveAlbum(r.Context(), albumID); err != nil {
		log.Printf("Error saving album: %v", err)
		http.Error(w, "Failed to save album: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleSpotifyAlbumRemove(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	albumID := chi.URLParam(r, "id")
	if albumID == "" {
		http.Error(w, "Album ID required", http.StatusBadRequest)
		return
	}

	if err := spotifyClient.RemoveAlbum(r.Context(), albumID); err != nil {
		log.Printf("Error removing album: %v", err)
		http.Error(w, "Failed to remove album: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleSpotifyArtistFollowing(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	artistID := chi.URLParam(r, "id")
	if artistID == "" {
		http.Error(w, "Artist ID required", http.StatusBadRequest)
		return
	}

	following, err := spotifyClient.CheckFollowingArtist(r.Context(), artistID)
	if err != nil {
		log.Printf("Error checking artist following: %v", err)
		http.Error(w, "Failed to check artist following: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]bool{"following": following})
}

func handleSpotifyArtistFollow(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	artistID := chi.URLParam(r, "id")
	if artistID == "" {
		http.Error(w, "Artist ID required", http.StatusBadRequest)
		return
	}

	if err := spotifyClient.FollowArtist(r.Context(), artistID); err != nil {
		log.Printf("Error following artist: %v", err)
		http.Error(w, "Failed to follow artist: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleSpotifyArtistUnfollow(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	artistID := chi.URLParam(r, "id")
	if artistID == "" {
		http.Error(w, "Artist ID required", http.StatusBadRequest)
		return
	}

	if err := spotifyClient.UnfollowArtist(r.Context(), artistID); err != nil {
		log.Printf("Error unfollowing artist: %v", err)
		http.Error(w, "Failed to unfollow artist: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleSpotifyLibraryAlbums(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	limit := 50
	offset := 0
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil {
			limit = parsed
		}
	}
	if o := r.URL.Query().Get("offset"); o != "" {
		if parsed, err := strconv.Atoi(o); err == nil {
			offset = parsed
		}
	}

	albums, total, err := spotifyClient.GetSavedAlbums(r.Context(), limit, offset)
	if err != nil {
		log.Printf("Error getting saved albums: %v", err)
		http.Error(w, "Failed to get saved albums: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"items":  albums,
		"total":  total,
		"limit":  limit,
		"offset": offset,
	})
}

func handleSpotifyLibraryArtists(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	limit := 50
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil {
			limit = parsed
		}
	}
	after := r.URL.Query().Get("after")

	artists, nextAfter, err := spotifyClient.GetFollowedArtists(r.Context(), limit, after)
	if err != nil {
		log.Printf("Error getting followed artists: %v", err)
		http.Error(w, "Failed to get followed artists: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"items": artists,
		"after": nextAfter,
	})
}

func handleSpotifyLibraryTracks(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	limit := 50
	offset := 0
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil {
			limit = parsed
		}
	}
	if o := r.URL.Query().Get("offset"); o != "" {
		if parsed, err := strconv.Atoi(o); err == nil {
			offset = parsed
		}
	}

	tracks, total, err := spotifyClient.GetLikedSongs(r.Context(), limit, offset)
	if err != nil {
		log.Printf("Error getting liked songs: %v", err)
		http.Error(w, "Failed to get liked songs: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"items":  tracks,
		"total":  total,
		"limit":  limit,
		"offset": offset,
	})
}

func handleSpotifyLibraryShows(w http.ResponseWriter, r *http.Request) {
	if spotifyClient == nil || !spotifyClient.IsAuthenticated() {
		http.Error(w, "Spotify not authenticated", http.StatusUnauthorized)
		return
	}

	limit := 50
	offset := 0
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil {
			limit = parsed
		}
	}
	if o := r.URL.Query().Get("offset"); o != "" {
		if parsed, err := strconv.Atoi(o); err == nil {
			offset = parsed
		}
	}

	shows, total, err := spotifyClient.GetSavedShows(r.Context(), limit, offset)
	if err != nil {
		log.Printf("Error getting saved shows: %v", err)
		http.Error(w, "Failed to get saved shows: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"items":  shows,
		"total":  total,
		"limit":  limit,
		"offset": offset,
	})
}

// loadCalendarPrefs loads calendar preferences from disk
func loadCalendarPrefs() *CalendarPrefs {
	prefs := &CalendarPrefs{
		Calendars: make(map[string]CalendarPref),
	}

	if calendarPrefsFile == "" {
		return prefs
	}

	data, err := os.ReadFile(calendarPrefsFile)
	if err != nil {
		if !os.IsNotExist(err) {
			log.Printf("Warning: Failed to read calendar prefs: %v", err)
		}
		return prefs
	}

	if err := json.Unmarshal(data, prefs); err != nil {
		log.Printf("Warning: Failed to parse calendar prefs: %v", err)
		return &CalendarPrefs{Calendars: make(map[string]CalendarPref)}
	}

	return prefs
}

// saveCalendarPrefs saves calendar preferences to disk
func saveCalendarPrefs() error {
	if calendarPrefsFile == "" {
		return fmt.Errorf("calendar prefs file not configured")
	}

	data, err := json.MarshalIndent(calendarPrefs, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal calendar prefs: %w", err)
	}

	if err := os.WriteFile(calendarPrefsFile, data, 0644); err != nil {
		return fmt.Errorf("failed to write calendar prefs: %w", err)
	}

	return nil
}

// getCachedCalendarsWithPrefs returns cached calendars or fetches fresh ones
func getCachedCalendarsWithPrefs(ctx context.Context) ([]CalendarWithPrefs, error) {
	calendarCache.RLock()
	if time.Since(calendarCache.CalendarsUpdatedAt) < calendarCache.CacheDuration && len(calendarCache.Calendars) > 0 {
		result := calendarCache.Calendars
		calendarCache.RUnlock()
		return result, nil
	}
	calendarCache.RUnlock()

	// Cache miss or expired - fetch fresh data
	calendars, err := getCalendarsWithPrefs(ctx)
	if err != nil {
		return nil, err
	}

	calendarCache.Lock()
	calendarCache.Calendars = calendars
	calendarCache.CalendarsUpdatedAt = time.Now()
	calendarCache.Unlock()

	return calendars, nil
}

// getCachedEventsInRange returns cached events if the range overlaps and cache is fresh
func getCachedEventsInRange(ctx context.Context, start, end time.Time) ([]*calendar.Event, error) {
	calendarCache.RLock()
	cacheValid := time.Since(calendarCache.EventsUpdatedAt) < calendarCache.CacheDuration &&
		len(calendarCache.Events) > 0 &&
		!calendarCache.EventsStart.IsZero() &&
		(start.Equal(calendarCache.EventsStart) || start.After(calendarCache.EventsStart)) &&
		(end.Equal(calendarCache.EventsEnd) || end.Before(calendarCache.EventsEnd))

	if cacheValid {
		// Filter events to requested range
		var filtered []*calendar.Event
		for _, e := range calendarCache.Events {
			if (e.Start.Equal(start) || e.Start.After(start)) && e.Start.Before(end) {
				filtered = append(filtered, e)
			} else if e.End.After(start) && (e.End.Before(end) || e.End.Equal(end)) {
				filtered = append(filtered, e)
			} else if e.Start.Before(start) && e.End.After(end) {
				filtered = append(filtered, e)
			}
		}
		calendarCache.RUnlock()
		return filtered, nil
	}
	calendarCache.RUnlock()

	// Cache miss - fetch fresh data with a wider range for future requests
	// Always fetch 30 days to maximize cache hits
	wideStart := time.Date(start.Year(), start.Month(), start.Day(), 0, 0, 0, 0, start.Location())
	wideEnd := wideStart.AddDate(0, 0, 45) // 45 days ahead

	if calClient == nil || !calClient.IsAuthorized() {
		return nil, fmt.Errorf("calendar not authorized")
	}

	events, err := calClient.GetEventsInRange(ctx, wideStart, wideEnd)
	if err != nil {
		return nil, err
	}

	calendarCache.Lock()
	calendarCache.Events = events
	calendarCache.EventsStart = wideStart
	calendarCache.EventsEnd = wideEnd
	calendarCache.EventsUpdatedAt = time.Now()
	calendarCache.Unlock()

	// Filter to requested range
	var filtered []*calendar.Event
	for _, e := range events {
		if (e.Start.Equal(start) || e.Start.After(start)) && e.Start.Before(end) {
			filtered = append(filtered, e)
		} else if e.End.After(start) && (e.End.Before(end) || e.End.Equal(end)) {
			filtered = append(filtered, e)
		} else if e.Start.Before(start) && e.End.After(end) {
			filtered = append(filtered, e)
		}
	}

	return filtered, nil
}

// invalidateCalendarCache clears the calendar cache (call after creating/updating/deleting events)
func invalidateCalendarCache() {
	calendarCache.Lock()
	calendarCache.EventsUpdatedAt = time.Time{}
	calendarCache.CalendarsUpdatedAt = time.Time{}
	calendarCache.Unlock()
}

// getCalendarsWithPrefs merges calendar info with user preferences
func getCalendarsWithPrefs(ctx context.Context) ([]CalendarWithPrefs, error) {
	if calClient == nil {
		return nil, fmt.Errorf("calendar client not initialized")
	}

	calendars, err := calClient.GetConfiguredCalendars(ctx)
	if err != nil {
		return nil, err
	}

	result := make([]CalendarWithPrefs, 0, len(calendars))
	for _, cal := range calendars {
		pref, exists := calendarPrefs.Calendars[cal.ID]

		cwp := CalendarWithPrefs{
			ID:      cal.ID,
			Name:    cal.Name,
			Color:   cal.Color,
			Visible: true, // Default to visible
		}

		if exists {
			cwp.Visible = pref.Visible
			if pref.Color != "" {
				cwp.Color = pref.Color
			}
		}

		result = append(result, cwp)
	}

	return result, nil
}

// handleTabletProximity receives proximity sensor data from the app
func handleTabletProximity(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Near        bool `json:"near"`
		IdleTimeout int  `json:"idleTimeout"` // seconds
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON: "+err.Error(), http.StatusBadRequest)
		return
	}

	sensorState.Lock()
	wasNear := sensorState.ProximityNear
	sensorState.ProximityNear = req.Near
	sensorState.LastProximityAt = time.Now()
	if req.IdleTimeout > 0 {
		sensorState.IdleTimeoutSecs = req.IdleTimeout
		tabletIdleTimeout = time.Duration(req.IdleTimeout) * time.Second
	}

	// Track proximity state changes
	if req.Near && !wasNear {
		// Someone approached - clear idle timer
		sensorState.ScreenIdleAt = time.Time{}
		sensorState.Unlock()
		log.Println("Tablet proximity: someone approached")
		// Broadcast wake event to all connected clients
		if wsHub != nil {
			wsHub.Broadcast(websocket.Event{Type: "proximity_wake"})
		}
	} else if !req.Near && wasNear {
		// Someone left - start idle timer
		sensorState.ScreenIdleAt = time.Now().Add(tabletIdleTimeout)
		sensorState.Unlock()
	} else {
		sensorState.Unlock()
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
	})
}

// handleTabletLight receives light sensor data from the app
func handleTabletLight(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Lux float64 `json:"lux"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON: "+err.Error(), http.StatusBadRequest)
		return
	}

	sensorState.Lock()
	sensorState.LightLevel = req.Lux
	sensorState.LastLightAt = time.Now()
	sensorState.Unlock()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
	})
}

// handleGetSensorState returns the current sensor state
func handleGetSensorState(w http.ResponseWriter, r *http.Request) {
	sensorState.RLock()
	defer sensorState.RUnlock()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"proximityNear":   sensorState.ProximityNear,
		"lightLevel":      sensorState.LightLevel,
		"lastProximityAt": sensorState.LastProximityAt,
		"lastLightAt":     sensorState.LastLightAt,
		"screenIdleAt":    sensorState.ScreenIdleAt,
		"idleTimeoutSecs": sensorState.IdleTimeoutSecs,
	})
}

// handleExitKiosk broadcasts a command to exit kiosk mode via WebSocket
func handleExitKiosk(w http.ResponseWriter, r *http.Request) {
	if wsHub != nil {
		wsHub.Broadcast(websocket.Event{Type: "kiosk_exit"})
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]bool{"success": true})
}

// handleTabletReload broadcasts a reload command via WebSocket
func handleTabletReload(w http.ResponseWriter, r *http.Request) {
	if wsHub != nil {
		wsHub.Broadcast(websocket.Event{Type: "tablet_reload"})
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]bool{"success": true})
}

// wakeTablet broadcasts a wake event via WebSocket to dismiss screensaver
// This is called asynchronously when doorbell events occur
func wakeTablet() {
	if wsHub != nil {
		log.Println("Broadcasting tablet_wake to WebSocket clients")
		wsHub.Broadcast(websocket.Event{Type: "tablet_wake"})
	}
}

// handleGetTabletTheme returns the theme configuration
func handleGetTabletTheme(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"theme": "dark",
	})
}
