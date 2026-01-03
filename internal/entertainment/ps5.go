package entertainment

import (
	"fmt"
	"log"
	"strings"

	"home_control/internal/homeassistant"
)

// PS5Device represents a PlayStation 5 controlled via Home Assistant
type PS5Device struct {
	Name         string
	PowerSwitch  string // e.g., "switch.ps5_302_power" - for power control
	PSNAccountID string // e.g., "mscrnt" - used to find PSN sensors
}

// PS5Manager manages PlayStation 5 devices via Home Assistant
type PS5Manager struct {
	devices  map[string]*PS5Device
	haClient *homeassistant.Client
}

// NewPS5Manager creates a new PS5 manager using Home Assistant
func NewPS5Manager(haClient *homeassistant.Client) *PS5Manager {
	return &PS5Manager{
		devices:  make(map[string]*PS5Device),
		haClient: haClient,
	}
}

// AddDevice adds a PS5 device with its HA entity IDs
func (m *PS5Manager) AddDevice(name, powerSwitch, psnAccountID string) {
	m.devices[name] = &PS5Device{
		Name:         name,
		PowerSwitch:  powerSwitch,
		PSNAccountID: psnAccountID,
	}
	log.Printf("Added PS5: %s (power_switch: %s, psn_account: %s)", name, powerSwitch, psnAccountID)
}

// GetDevice returns a device by name
func (m *PS5Manager) GetDevice(name string) *PS5Device {
	return m.devices[name]
}

// GetDevices returns all devices
func (m *PS5Manager) GetDevices() map[string]*PS5Device {
	return m.devices
}

// PS5State represents the state of a PS5
type PS5State struct {
	Name         string `json:"name,omitempty"`
	Power        bool   `json:"power"`
	State        string `json:"state,omitempty"`         // "off", "on", "playing", "idle"
	OnlineStatus string `json:"online_status,omitempty"` // "offline", "availabletoplay", "busy"
	CurrentTitle string `json:"current_title,omitempty"`
	ImageUrl     string `json:"image_url,omitempty"`
	// Trophy info
	TrophyLevel     int `json:"trophy_level,omitempty"`
	PlatinumTrophies int `json:"platinum_trophies,omitempty"`
	GoldTrophies    int `json:"gold_trophies,omitempty"`
	SilverTrophies  int `json:"silver_trophies,omitempty"`
	BronzeTrophies  int `json:"bronze_trophies,omitempty"`
	// PSN info
	OnlineID string `json:"online_id,omitempty"`
	AvatarUrl string `json:"avatar_url,omitempty"`
	Error    string `json:"error,omitempty"`
}

// GetState returns the current state of the PS5 via Home Assistant
func (m *PS5Manager) GetState(deviceName string) (*PS5State, error) {
	device := m.devices[deviceName]
	if device == nil {
		return nil, fmt.Errorf("device not found: %s", deviceName)
	}

	state := &PS5State{
		Name: device.Name,
	}

	// Get power switch state from HA
	if device.PowerSwitch != "" {
		entity, err := m.haClient.GetState(device.PowerSwitch)
		if err != nil {
			state.Error = err.Error()
		} else {
			state.State = entity.State
			state.Power = entity.State == "on"
		}
	}

	// Get PSN sensors if account ID is provided
	if device.PSNAccountID != "" {
		m.fetchPSNData(device.PSNAccountID, state)
	}

	return state, nil
}

// fetchPSNData fetches PSN sensor data and populates the state
func (m *PS5Manager) fetchPSNData(psnAccountID string, state *PS5State) {
	// Now Playing sensor
	nowPlayingSensor := fmt.Sprintf("sensor.%s_now_playing", psnAccountID)
	if entity, err := m.haClient.GetState(nowPlayingSensor); err == nil {
		if entity.State != "unknown" && entity.State != "unavailable" {
			state.CurrentTitle = entity.State
		}
	}

	// Now Playing image
	nowPlayingImage := fmt.Sprintf("image.%s_now_playing", psnAccountID)
	if entity, err := m.haClient.GetState(nowPlayingImage); err == nil {
		if entityPicture, ok := entity.Attributes["entity_picture"].(string); ok && entityPicture != "" {
			if strings.HasPrefix(entityPicture, "/") {
				state.ImageUrl = m.haClient.GetBaseURL() + entityPicture
			} else {
				state.ImageUrl = entityPicture
			}
		}
	}

	// Online Status sensor
	onlineStatusSensor := fmt.Sprintf("sensor.%s_online_status", psnAccountID)
	if entity, err := m.haClient.GetState(onlineStatusSensor); err == nil {
		state.OnlineStatus = entity.State
	}

	// Online ID sensor
	onlineIDSensor := fmt.Sprintf("sensor.%s_online_id", psnAccountID)
	if entity, err := m.haClient.GetState(onlineIDSensor); err == nil {
		if entity.State != "unknown" && entity.State != "unavailable" {
			state.OnlineID = entity.State
		}
	}

	// Avatar image
	avatarImage := fmt.Sprintf("image.%s_avatar", psnAccountID)
	if entity, err := m.haClient.GetState(avatarImage); err == nil {
		if entityPicture, ok := entity.Attributes["entity_picture"].(string); ok && entityPicture != "" {
			if strings.HasPrefix(entityPicture, "/") {
				state.AvatarUrl = m.haClient.GetBaseURL() + entityPicture
			} else {
				state.AvatarUrl = entityPicture
			}
		}
	}

	// Trophy sensors
	trophyLevelSensor := fmt.Sprintf("sensor.%s_trophy_level", psnAccountID)
	if entity, err := m.haClient.GetState(trophyLevelSensor); err == nil {
		if level, ok := parseIntFromState(entity.State); ok {
			state.TrophyLevel = level
		}
	}

	platinumSensor := fmt.Sprintf("sensor.%s_platinum_trophies", psnAccountID)
	if entity, err := m.haClient.GetState(platinumSensor); err == nil {
		if count, ok := parseIntFromState(entity.State); ok {
			state.PlatinumTrophies = count
		}
	}

	goldSensor := fmt.Sprintf("sensor.%s_gold_trophies", psnAccountID)
	if entity, err := m.haClient.GetState(goldSensor); err == nil {
		if count, ok := parseIntFromState(entity.State); ok {
			state.GoldTrophies = count
		}
	}

	silverSensor := fmt.Sprintf("sensor.%s_silver_trophies", psnAccountID)
	if entity, err := m.haClient.GetState(silverSensor); err == nil {
		if count, ok := parseIntFromState(entity.State); ok {
			state.SilverTrophies = count
		}
	}

	bronzeSensor := fmt.Sprintf("sensor.%s_bronze_trophies", psnAccountID)
	if entity, err := m.haClient.GetState(bronzeSensor); err == nil {
		if count, ok := parseIntFromState(entity.State); ok {
			state.BronzeTrophies = count
		}
	}
}

// parseIntFromState parses an integer from an HA state string
func parseIntFromState(state string) (int, bool) {
	if state == "unknown" || state == "unavailable" || state == "" {
		return 0, false
	}
	var val int
	_, err := fmt.Sscanf(state, "%d", &val)
	return val, err == nil
}

// Power controls PS5 power via Home Assistant
func (m *PS5Manager) Power(deviceName string, on bool) error {
	device := m.devices[deviceName]
	if device == nil {
		return fmt.Errorf("device not found: %s", deviceName)
	}

	if device.PowerSwitch == "" {
		return fmt.Errorf("no power switch configured for %s", deviceName)
	}

	service := "turn_off"
	if on {
		service = "turn_on"
	}

	return m.haClient.CallService("switch", service, device.PowerSwitch)
}

// PowerOn turns on the PS5
func (m *PS5Manager) PowerOn(deviceName string) error {
	return m.Power(deviceName, true)
}

// PowerOff puts the PS5 into standby
func (m *PS5Manager) PowerOff(deviceName string) error {
	return m.Power(deviceName, false)
}

// TogglePower toggles power state
func (m *PS5Manager) TogglePower(deviceName string) error {
	state, err := m.GetState(deviceName)
	if err != nil {
		return err
	}

	return m.Power(deviceName, !state.Power)
}

// GetAllStates returns states of all devices
func (m *PS5Manager) GetAllStates() []*PS5State {
	states := make([]*PS5State, 0, len(m.devices))
	for name := range m.devices {
		state, _ := m.GetState(name)
		if state != nil {
			states = append(states, state)
		}
	}
	return states
}
