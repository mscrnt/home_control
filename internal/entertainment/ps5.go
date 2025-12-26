package entertainment

import (
	"encoding/json"
	"fmt"
	"log"
	"sync"
	"time"

	mqtt "github.com/eclipse/paho.mqtt.golang"
)

// PS5Device represents a PlayStation 5
type PS5Device struct {
	Name       string
	DeviceID   string // Unique device ID from PS5-MQTT
	PSNAccount string // PSN account name (optional)
	mu         sync.RWMutex
	state      *PS5State
}

// PS5State represents the current state of a PS5
type PS5State struct {
	Name       string `json:"name"`
	DeviceID   string `json:"device_id"`
	Power      string `json:"power"`       // "STANDBY", "AWAKE", "UNKNOWN"
	Activity   string `json:"activity"`    // Current activity/game
	Online     bool   `json:"online"`
	LastUpdate time.Time `json:"last_update"`
	Error      string `json:"error,omitempty"`
}

// PS5Manager manages PlayStation 5 devices via MQTT
type PS5Manager struct {
	devices     map[string]*PS5Device
	mqttClient  mqtt.Client
	baseTopic   string // e.g., "homeassistant" or custom discovery topic
	mu          sync.RWMutex
}

// PS5-MQTT topic structure:
// {baseTopic}/switch/{device_id}/set - Power control (ON/OFF)
// {baseTopic}/sensor/{device_id}/state - State updates
// {baseTopic}/switch/{device_id}/power/state - Power state (ON/OFF)

// NewPS5Manager creates a new PS5 manager
func NewPS5Manager(mqttClient mqtt.Client, baseTopic string) *PS5Manager {
	if baseTopic == "" {
		baseTopic = "homeassistant"
	}

	mgr := &PS5Manager{
		devices:    make(map[string]*PS5Device),
		mqttClient: mqttClient,
		baseTopic:  baseTopic,
	}

	// Subscribe to PS5 state updates if MQTT client is connected
	if mqttClient != nil && mqttClient.IsConnected() {
		mgr.subscribeToStates()
	}

	return mgr
}

// AddDevice adds a PS5 device
func (m *PS5Manager) AddDevice(name, deviceID, psnAccount string) {
	m.mu.Lock()
	defer m.mu.Unlock()

	m.devices[name] = &PS5Device{
		Name:       name,
		DeviceID:   deviceID,
		PSNAccount: psnAccount,
		state: &PS5State{
			Name:     name,
			DeviceID: deviceID,
			Power:    "UNKNOWN",
			Online:   false,
		},
	}
	log.Printf("Added PS5: %s (Device ID: %s)", name, deviceID)
}

// GetDevice returns a device by name
func (m *PS5Manager) GetDevice(name string) *PS5Device {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.devices[name]
}

// GetDevices returns all devices
func (m *PS5Manager) GetDevices() map[string]*PS5Device {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.devices
}

// subscribeToStates subscribes to MQTT state topics for all devices
func (m *PS5Manager) subscribeToStates() {
	// Subscribe to all PS5 power state topics
	topic := fmt.Sprintf("%s/switch/+/power/state", m.baseTopic)
	m.mqttClient.Subscribe(topic, 0, m.handlePowerState)

	// Subscribe to activity/sensor states
	topic = fmt.Sprintf("%s/sensor/+/state", m.baseTopic)
	m.mqttClient.Subscribe(topic, 0, m.handleActivityState)

	log.Printf("PS5 MQTT: Subscribed to state topics under %s", m.baseTopic)
}

// handlePowerState handles power state MQTT messages
func (m *PS5Manager) handlePowerState(client mqtt.Client, msg mqtt.Message) {
	// Extract device ID from topic
	// Topic format: {baseTopic}/switch/{device_id}/power/state
	topic := msg.Topic()
	payload := string(msg.Payload())

	log.Printf("PS5 MQTT power state: %s = %s", topic, payload)

	// Find matching device and update state
	m.mu.RLock()
	defer m.mu.RUnlock()

	for _, device := range m.devices {
		expectedTopic := fmt.Sprintf("%s/switch/%s/power/state", m.baseTopic, device.DeviceID)
		if topic == expectedTopic {
			device.mu.Lock()
			if payload == "ON" {
				device.state.Power = "AWAKE"
			} else {
				device.state.Power = "STANDBY"
			}
			device.state.Online = true
			device.state.LastUpdate = time.Now()
			device.mu.Unlock()
			break
		}
	}
}

// handleActivityState handles activity/sensor state MQTT messages
func (m *PS5Manager) handleActivityState(client mqtt.Client, msg mqtt.Message) {
	topic := msg.Topic()
	payload := string(msg.Payload())

	log.Printf("PS5 MQTT activity state: %s = %s", topic, payload)

	// Find matching device and update activity
	m.mu.RLock()
	defer m.mu.RUnlock()

	for _, device := range m.devices {
		// Check for activity sensor
		expectedTopic := fmt.Sprintf("%s/sensor/%s/state", m.baseTopic, device.DeviceID)
		if topic == expectedTopic {
			device.mu.Lock()
			device.state.Activity = payload
			device.state.LastUpdate = time.Now()
			device.mu.Unlock()
			break
		}
	}
}

// ========== Power Control ==========

// publish publishes an MQTT message
func (m *PS5Manager) publish(topic, payload string) error {
	if m.mqttClient == nil {
		return fmt.Errorf("MQTT client not configured")
	}

	if !m.mqttClient.IsConnected() {
		return fmt.Errorf("MQTT client not connected")
	}

	token := m.mqttClient.Publish(topic, 0, false, payload)
	token.Wait()
	return token.Error()
}

// PowerOn turns on a PS5
func (m *PS5Manager) PowerOn(deviceName string) error {
	m.mu.RLock()
	device := m.devices[deviceName]
	m.mu.RUnlock()

	if device == nil {
		return fmt.Errorf("device not found: %s", deviceName)
	}

	topic := fmt.Sprintf("%s/switch/%s/set", m.baseTopic, device.DeviceID)
	log.Printf("PS5 power on: %s -> %s", deviceName, topic)

	return m.publish(topic, "ON")
}

// PowerOff puts a PS5 into standby
func (m *PS5Manager) PowerOff(deviceName string) error {
	m.mu.RLock()
	device := m.devices[deviceName]
	m.mu.RUnlock()

	if device == nil {
		return fmt.Errorf("device not found: %s", deviceName)
	}

	topic := fmt.Sprintf("%s/switch/%s/set", m.baseTopic, device.DeviceID)
	log.Printf("PS5 power off: %s -> %s", deviceName, topic)

	return m.publish(topic, "OFF")
}

// TogglePower toggles power state
func (m *PS5Manager) TogglePower(deviceName string) error {
	m.mu.RLock()
	device := m.devices[deviceName]
	m.mu.RUnlock()

	if device == nil {
		return fmt.Errorf("device not found: %s", deviceName)
	}

	device.mu.RLock()
	currentPower := device.state.Power
	device.mu.RUnlock()

	if currentPower == "AWAKE" {
		return m.PowerOff(deviceName)
	}
	return m.PowerOn(deviceName)
}

// ========== State Management ==========

// GetState returns the current state of a PS5
func (m *PS5Manager) GetState(deviceName string) *PS5State {
	m.mu.RLock()
	device := m.devices[deviceName]
	m.mu.RUnlock()

	if device == nil {
		return nil
	}

	device.mu.RLock()
	defer device.mu.RUnlock()

	// Return a copy of the state
	stateCopy := *device.state
	return &stateCopy
}

// GetAllStates returns states of all devices
func (m *PS5Manager) GetAllStates() []*PS5State {
	m.mu.RLock()
	defer m.mu.RUnlock()

	states := make([]*PS5State, 0, len(m.devices))
	for _, device := range m.devices {
		device.mu.RLock()
		stateCopy := *device.state
		device.mu.RUnlock()
		states = append(states, &stateCopy)
	}
	return states
}

// RefreshState requests a state refresh for a device
func (m *PS5Manager) RefreshState(deviceName string) error {
	m.mu.RLock()
	device := m.devices[deviceName]
	m.mu.RUnlock()

	if device == nil {
		return fmt.Errorf("device not found: %s", deviceName)
	}

	// Request state refresh by publishing to the state topic
	// PS5-MQTT typically auto-publishes state changes
	topic := fmt.Sprintf("%s/switch/%s/power/state", m.baseTopic, device.DeviceID)
	log.Printf("Requesting PS5 state refresh: %s", topic)

	// For PS5-MQTT, we might need to check specific topics
	// The actual refresh mechanism depends on PS5-MQTT implementation
	return nil
}

// ========== Serialization ==========

// MarshalJSON marshals a PS5State to JSON
func (s *PS5State) MarshalJSON() ([]byte, error) {
	type Alias PS5State
	return json.Marshal(&struct {
		*Alias
		LastUpdate string `json:"last_update"`
	}{
		Alias:      (*Alias)(s),
		LastUpdate: s.LastUpdate.Format(time.RFC3339),
	})
}
