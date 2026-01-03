package entertainment

import (
	"fmt"
	"log"
	"strings"

	"home_control/internal/homeassistant"
)

// XboxDevice represents an Xbox console controlled via Home Assistant
type XboxDevice struct {
	Name              string
	MediaPlayerID     string // e.g., "media_player.xbox"
	RemoteID          string // e.g., "remote.xbox_remote"
	NowPlayingSensorID string // e.g., "sensor.the_og_ninja_now_playing" (optional)
}

// XboxManager manages Xbox consoles via Home Assistant
type XboxManager struct {
	devices  map[string]*XboxDevice
	haClient *homeassistant.Client
}

// NewXboxManager creates a new Xbox manager using Home Assistant
func NewXboxManager(haClient *homeassistant.Client) *XboxManager {
	return &XboxManager{
		devices:  make(map[string]*XboxDevice),
		haClient: haClient,
	}
}

// AddDevice adds an Xbox device with its HA entity IDs
func (m *XboxManager) AddDevice(name, mediaPlayerID, remoteID, nowPlayingSensorID string) {
	m.devices[name] = &XboxDevice{
		Name:              name,
		MediaPlayerID:     mediaPlayerID,
		RemoteID:          remoteID,
		NowPlayingSensorID: nowPlayingSensorID,
	}
	if nowPlayingSensorID != "" {
		log.Printf("Added Xbox: %s (media_player: %s, remote: %s, now_playing: %s)", name, mediaPlayerID, remoteID, nowPlayingSensorID)
	} else {
		log.Printf("Added Xbox: %s (media_player: %s, remote: %s)", name, mediaPlayerID, remoteID)
	}
}

// GetDevice returns a device by name
func (m *XboxManager) GetDevice(name string) *XboxDevice {
	return m.devices[name]
}

// GetDevices returns all devices
func (m *XboxManager) GetDevices() map[string]*XboxDevice {
	return m.devices
}

// XboxState represents the state of an Xbox
type XboxState struct {
	Name         string  `json:"name,omitempty"`
	Power        bool    `json:"power"`
	State        string  `json:"state,omitempty"`
	MediaType    string  `json:"media_type,omitempty"`
	CurrentTitle string  `json:"current_title,omitempty"`
	ImageUrl     string  `json:"image_url,omitempty"`
	// Extra info from now_playing sensor
	Genre       string  `json:"genre,omitempty"`
	Developer   string  `json:"developer,omitempty"`
	Publisher   string  `json:"publisher,omitempty"`
	Progress    float64 `json:"progress,omitempty"`
	Gamerscore  int     `json:"gamerscore,omitempty"`
	Error       string  `json:"error,omitempty"`
}

// GetState returns the current state of the Xbox via Home Assistant
func (m *XboxManager) GetState(deviceName string) (*XboxState, error) {
	device := m.devices[deviceName]
	if device == nil {
		return nil, fmt.Errorf("device not found: %s", deviceName)
	}

	state := &XboxState{
		Name: device.Name,
	}

	// Get media player state from HA
	entity, err := m.haClient.GetState(device.MediaPlayerID)
	if err != nil {
		state.Error = err.Error()
		return state, nil
	}

	// Parse state
	state.State = entity.State
	state.Power = entity.State != "off" && entity.State != "unavailable"

	// Get media type if available
	if mediaType, ok := entity.Attributes["media_content_type"].(string); ok {
		state.MediaType = mediaType
	}

	// Get current title/app from media player
	if title, ok := entity.Attributes["media_title"].(string); ok {
		state.CurrentTitle = title
	} else if app, ok := entity.Attributes["app_name"].(string); ok {
		state.CurrentTitle = app
	}

	// Get entity picture (game/app art) from media player
	if entityPicture, ok := entity.Attributes["entity_picture"].(string); ok && entityPicture != "" {
		// If it's a relative URL, prepend the HA base URL
		if strings.HasPrefix(entityPicture, "/") {
			state.ImageUrl = m.haClient.GetBaseURL() + entityPicture
		} else {
			state.ImageUrl = entityPicture
		}
	}

	// If we have a now_playing sensor, get richer info
	if device.NowPlayingSensorID != "" {
		nowPlaying, err := m.haClient.GetState(device.NowPlayingSensorID)
		if err == nil && nowPlaying.State != "unknown" && nowPlaying.State != "unavailable" {
			// Use the sensor state as the title (game name)
			state.CurrentTitle = nowPlaying.State

			// Get extra attributes if available
			if genres, ok := nowPlaying.Attributes["genres"].(string); ok {
				state.Genre = genres
			}
			if developer, ok := nowPlaying.Attributes["developer"].(string); ok {
				state.Developer = developer
			}
			if publisher, ok := nowPlaying.Attributes["publisher"].(string); ok {
				state.Publisher = publisher
			}
			if progress, ok := nowPlaying.Attributes["progress"].(float64); ok {
				state.Progress = progress
			}
			if gamerscore, ok := nowPlaying.Attributes["gamerscore"].(float64); ok {
				state.Gamerscore = int(gamerscore)
			}
			// Get entity picture from now_playing sensor (often has better game art)
			if entityPicture, ok := nowPlaying.Attributes["entity_picture"].(string); ok && entityPicture != "" {
				if strings.HasPrefix(entityPicture, "/") {
					state.ImageUrl = m.haClient.GetBaseURL() + entityPicture
				} else {
					state.ImageUrl = entityPicture
				}
			}
		}
	}

	return state, nil
}

// Power controls Xbox power via Home Assistant
func (m *XboxManager) Power(deviceName string, on bool) error {
	device := m.devices[deviceName]
	if device == nil {
		return fmt.Errorf("device not found: %s", deviceName)
	}

	service := "turn_off"
	if on {
		service = "turn_on"
	}

	return m.haClient.CallService("media_player", service, device.MediaPlayerID)
}

// Input sends a remote control input via Home Assistant
func (m *XboxManager) Input(deviceName, button string) error {
	device := m.devices[deviceName]
	if device == nil {
		return fmt.Errorf("device not found: %s", deviceName)
	}

	// Map common button names to Xbox remote commands
	command := mapButtonToCommand(button)

	data := map[string]interface{}{
		"entity_id": device.RemoteID,
		"command":   command,
	}

	return m.haClient.CallServiceWithData("remote", "send_command", data)
}

// mapButtonToCommand maps UI button names to HA remote commands
func mapButtonToCommand(button string) string {
	// Standard mappings
	buttonMap := map[string]string{
		// D-pad
		"up":    "up",
		"down":  "down",
		"left":  "left",
		"right": "right",
		// Face buttons
		"a": "a",
		"b": "b",
		"x": "x",
		"y": "y",
		// Menu buttons
		"menu":  "menu",
		"view":  "view",
		"nexus": "nexus",
		"home":  "nexus",
		"xbox":  "nexus",
		// Shoulder/triggers
		"lb":             "left_shoulder",
		"rb":             "right_shoulder",
		"lt":             "left_trigger",
		"rt":             "right_trigger",
		"left_shoulder":  "left_shoulder",
		"right_shoulder": "right_shoulder",
		"left_trigger":   "left_trigger",
		"right_trigger":  "right_trigger",
		// Thumbsticks
		"lstick":           "left_thumbstick",
		"rstick":           "right_thumbstick",
		"left_thumbstick":  "left_thumbstick",
		"right_thumbstick": "right_thumbstick",
		// D-pad alternate names
		"dpad_up":    "up",
		"dpad_down":  "down",
		"dpad_left":  "left",
		"dpad_right": "right",
	}

	if mapped, ok := buttonMap[strings.ToLower(button)]; ok {
		return mapped
	}
	return button
}

// MediaCommand sends a media control command
func (m *XboxManager) MediaCommand(deviceName, command string) error {
	device := m.devices[deviceName]
	if device == nil {
		return fmt.Errorf("device not found: %s", deviceName)
	}

	// Map media commands to HA services
	serviceMap := map[string]string{
		"play":       "media_play",
		"pause":      "media_pause",
		"play_pause": "media_play_pause",
		"stop":       "media_stop",
		"next":       "media_next_track",
		"previous":   "media_previous_track",
	}

	service, ok := serviceMap[strings.ToLower(command)]
	if !ok {
		return fmt.Errorf("unknown media command: %s", command)
	}

	return m.haClient.CallService("media_player", service, device.MediaPlayerID)
}

// LaunchApp launches an app on the Xbox using its product ID
// Use "Home" to go to the Xbox home screen
func (m *XboxManager) LaunchApp(deviceName, appID string) error {
	device := m.devices[deviceName]
	if device == nil {
		return fmt.Errorf("device not found: %s", deviceName)
	}

	data := map[string]interface{}{
		"entity_id":          device.MediaPlayerID,
		"media_content_type": "app",
		"media_content_id":   appID,
	}

	return m.haClient.CallServiceWithData("media_player", "play_media", data)
}

// Play starts playback
func (m *XboxManager) Play(deviceName string) error {
	return m.MediaCommand(deviceName, "play")
}

// Pause pauses playback
func (m *XboxManager) Pause(deviceName string) error {
	return m.MediaCommand(deviceName, "pause")
}

// PlayPause toggles play/pause
func (m *XboxManager) PlayPause(deviceName string) error {
	return m.MediaCommand(deviceName, "play_pause")
}

// Stop stops playback
func (m *XboxManager) Stop(deviceName string) error {
	return m.MediaCommand(deviceName, "stop")
}

// Next skips to next track
func (m *XboxManager) Next(deviceName string) error {
	return m.MediaCommand(deviceName, "next")
}

// Previous goes to previous track
func (m *XboxManager) Previous(deviceName string) error {
	return m.MediaCommand(deviceName, "previous")
}

// GetAllStates returns states of all devices
func (m *XboxManager) GetAllStates() []*XboxState {
	states := make([]*XboxState, 0, len(m.devices))
	for name := range m.devices {
		state, _ := m.GetState(name)
		if state != nil {
			states = append(states, state)
		}
	}
	return states
}
