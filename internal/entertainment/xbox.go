package entertainment

import (
	"bytes"
	"crypto/rand"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"sync"
	"time"
)

// XboxDevice represents an Xbox console
type XboxDevice struct {
	Name     string
	Host     string
	LiveID   string // Xbox Live Device ID (for power on)
	mu       sync.Mutex
	conn     net.Conn
}

// XboxManager manages Xbox consoles
type XboxManager struct {
	devices    map[string]*XboxDevice
	restServer string // Optional: URL to xbox-smartglass-rest server
	httpClient *http.Client
}

// SmartGlass protocol constants
const (
	sgDiscoveryPort = 5050
	sgControlPort   = 5050
	sgMessagePort   = 5051
)

// Message types
const (
	msgDiscoveryRequest  = 0xDD00
	msgDiscoveryResponse = 0xDD01
	msgPowerOn           = 0xDD02
)

// NewXboxManager creates a new Xbox manager
func NewXboxManager(restServerURL string) *XboxManager {
	return &XboxManager{
		devices:    make(map[string]*XboxDevice),
		restServer: restServerURL,
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// AddDevice adds an Xbox device
func (m *XboxManager) AddDevice(name, host, liveID string) {
	m.devices[name] = &XboxDevice{
		Name:   name,
		Host:   host,
		LiveID: liveID,
	}
	log.Printf("Added Xbox: %s (%s, LiveID: %s)", name, host, liveID)
}

// GetDevice returns a device by name
func (m *XboxManager) GetDevice(name string) *XboxDevice {
	return m.devices[name]
}

// GetDevices returns all devices
func (m *XboxManager) GetDevices() map[string]*XboxDevice {
	return m.devices
}

// ========== REST Server Methods ==========
// These methods use the xbox-smartglass-rest Python server if configured

// restCall makes a call to the SmartGlass REST server
func (m *XboxManager) restCall(method, endpoint string, body interface{}) ([]byte, error) {
	if m.restServer == "" {
		return nil, fmt.Errorf("REST server not configured")
	}

	url := m.restServer + endpoint

	var reqBody io.Reader
	if body != nil {
		jsonData, err := json.Marshal(body)
		if err != nil {
			return nil, err
		}
		reqBody = bytes.NewReader(jsonData)
	}

	req, err := http.NewRequest(method, url, reqBody)
	if err != nil {
		return nil, err
	}

	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	resp, err := m.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("REST server error %d: %s", resp.StatusCode, string(respBody))
	}

	return respBody, nil
}

// DiscoverDevices discovers Xbox devices on the network via REST server
func (m *XboxManager) DiscoverDevices() ([]map[string]interface{}, error) {
	data, err := m.restCall("GET", "/device", nil)
	if err != nil {
		return nil, err
	}

	var devices []map[string]interface{}
	if err := json.Unmarshal(data, &devices); err != nil {
		return nil, err
	}

	return devices, nil
}

// ========== Direct SmartGlass Protocol ==========

// buildDiscoveryPacket creates a SmartGlass discovery request packet
func buildDiscoveryPacket() []byte {
	buf := new(bytes.Buffer)

	// Header
	binary.Write(buf, binary.BigEndian, uint16(msgDiscoveryRequest))
	binary.Write(buf, binary.BigEndian, uint16(2)) // Payload length

	// Flags
	binary.Write(buf, binary.BigEndian, uint16(0))

	return buf.Bytes()
}

// buildPowerOnPacket creates a SmartGlass power on packet
func buildPowerOnPacket(liveID string) []byte {
	buf := new(bytes.Buffer)

	// Header: Type (2) + Payload length (2)
	binary.Write(buf, binary.BigEndian, uint16(msgPowerOn))

	// Live ID length + Live ID + padding
	liveIDBytes := []byte(liveID)
	payloadLen := uint16(2 + len(liveIDBytes))
	binary.Write(buf, binary.BigEndian, payloadLen)

	// Unprotected payload length
	binary.Write(buf, binary.BigEndian, uint16(len(liveIDBytes)))

	// Live ID
	buf.Write(liveIDBytes)

	return buf.Bytes()
}

// Discover discovers Xbox devices on the local network
func (m *XboxManager) Discover(timeout time.Duration) ([]*XboxDevice, error) {
	// Create UDP socket
	conn, err := net.ListenUDP("udp4", &net.UDPAddr{Port: 0})
	if err != nil {
		return nil, fmt.Errorf("failed to create UDP socket: %w", err)
	}
	defer conn.Close()

	// Set read deadline
	conn.SetReadDeadline(time.Now().Add(timeout))

	// Build discovery packet
	packet := buildDiscoveryPacket()

	// Broadcast to 255.255.255.255:5050
	broadcastAddr := &net.UDPAddr{
		IP:   net.IPv4(255, 255, 255, 255),
		Port: sgDiscoveryPort,
	}

	_, err = conn.WriteToUDP(packet, broadcastAddr)
	if err != nil {
		return nil, fmt.Errorf("failed to send discovery packet: %w", err)
	}

	// Collect responses
	devices := make([]*XboxDevice, 0)
	buf := make([]byte, 1024)

	for {
		n, addr, err := conn.ReadFromUDP(buf)
		if err != nil {
			// Timeout or error - stop listening
			break
		}

		if n < 4 {
			continue
		}

		// Parse response
		msgType := binary.BigEndian.Uint16(buf[0:2])
		if msgType != msgDiscoveryResponse {
			continue
		}

		// Extract device info from response
		// This is a simplified parsing - full protocol is more complex
		device := &XboxDevice{
			Name: fmt.Sprintf("Xbox-%s", addr.IP.String()),
			Host: addr.IP.String(),
		}

		// Try to extract Live ID from payload
		if n > 20 {
			// Live ID is usually in the payload
			// Format varies by Xbox version
		}

		devices = append(devices, device)
	}

	return devices, nil
}

// ========== Power Control ==========

// PowerOn sends a power on packet to the Xbox
func (d *XboxDevice) PowerOn() error {
	if d.LiveID == "" {
		return fmt.Errorf("Live ID required for power on")
	}

	// Create UDP socket
	conn, err := net.DialUDP("udp4", nil, &net.UDPAddr{
		IP:   net.ParseIP(d.Host),
		Port: sgDiscoveryPort,
	})
	if err != nil {
		return fmt.Errorf("failed to connect: %w", err)
	}
	defer conn.Close()

	// Build and send power on packet (send multiple times for reliability)
	packet := buildPowerOnPacket(d.LiveID)

	for i := 0; i < 5; i++ {
		_, err = conn.Write(packet)
		if err != nil {
			return fmt.Errorf("failed to send power on packet: %w", err)
		}
		time.Sleep(100 * time.Millisecond)
	}

	log.Printf("Sent power on to Xbox %s (Live ID: %s)", d.Name, d.LiveID)
	return nil
}

// PowerOff requires an established SmartGlass connection
// For now, this is a placeholder
func (d *XboxDevice) PowerOff() error {
	// Power off requires authenticated SmartGlass connection
	// which is significantly more complex
	return fmt.Errorf("power off requires SmartGlass REST server")
}

// PowerOnViaREST uses the REST server to power on
func (m *XboxManager) PowerOnViaREST(deviceName string) error {
	device := m.devices[deviceName]
	if device == nil {
		return fmt.Errorf("device not found: %s", deviceName)
	}

	_, err := m.restCall("GET", fmt.Sprintf("/device/%s/poweron", device.LiveID), nil)
	return err
}

// PowerOffViaREST uses the REST server to power off
func (m *XboxManager) PowerOffViaREST(deviceName string) error {
	device := m.devices[deviceName]
	if device == nil {
		return fmt.Errorf("device not found: %s", deviceName)
	}

	_, err := m.restCall("GET", fmt.Sprintf("/device/%s/poweroff", device.LiveID), nil)
	return err
}

// ========== Remote Control via REST ==========

// Input sends a remote control input via REST server
func (m *XboxManager) Input(deviceName, button string) error {
	device := m.devices[deviceName]
	if device == nil {
		return fmt.Errorf("device not found: %s", deviceName)
	}

	body := map[string]string{
		"button": button,
	}

	_, err := m.restCall("POST", fmt.Sprintf("/device/%s/input", device.LiveID), body)
	return err
}

// Common button names for Xbox remote
var XboxButtons = map[string]string{
	"a":        "a",
	"b":        "b",
	"x":        "x",
	"y":        "y",
	"up":       "dpad_up",
	"down":     "dpad_down",
	"left":     "dpad_left",
	"right":    "dpad_right",
	"menu":     "menu",
	"view":     "view",
	"nexus":    "nexus", // Xbox button
	"home":     "nexus",
	"lb":       "left_shoulder",
	"rb":       "right_shoulder",
	"lt":       "left_trigger",
	"rt":       "right_trigger",
	"lstick":   "left_thumbstick",
	"rstick":   "right_thumbstick",
}

// SendButton sends a button press
func (m *XboxManager) SendButton(deviceName, button string) error {
	// Map common names to Xbox button names
	if mappedButton, ok := XboxButtons[button]; ok {
		button = mappedButton
	}
	return m.Input(deviceName, button)
}

// Navigate sends navigation commands
func (m *XboxManager) Navigate(deviceName, direction string) error {
	return m.SendButton(deviceName, direction)
}

// ========== Media Control via REST ==========

// MediaCommand sends a media control command
func (m *XboxManager) MediaCommand(deviceName, command string) error {
	device := m.devices[deviceName]
	if device == nil {
		return fmt.Errorf("device not found: %s", deviceName)
	}

	body := map[string]string{
		"command": command,
	}

	_, err := m.restCall("POST", fmt.Sprintf("/device/%s/media", device.LiveID), body)
	return err
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

// Next skips to next
func (m *XboxManager) Next(deviceName string) error {
	return m.MediaCommand(deviceName, "next_track")
}

// Previous goes to previous
func (m *XboxManager) Previous(deviceName string) error {
	return m.MediaCommand(deviceName, "prev_track")
}

// ========== App Control via REST ==========

// LaunchApp launches an app by title ID or name
func (m *XboxManager) LaunchApp(deviceName, appID string) error {
	device := m.devices[deviceName]
	if device == nil {
		return fmt.Errorf("device not found: %s", deviceName)
	}

	_, err := m.restCall("GET", fmt.Sprintf("/device/%s/launch/%s", device.LiveID, appID), nil)
	return err
}

// GetInstalledApps gets list of installed apps
func (m *XboxManager) GetInstalledApps(deviceName string) ([]map[string]interface{}, error) {
	device := m.devices[deviceName]
	if device == nil {
		return nil, fmt.Errorf("device not found: %s", deviceName)
	}

	data, err := m.restCall("GET", fmt.Sprintf("/device/%s/apps", device.LiveID), nil)
	if err != nil {
		return nil, err
	}

	var apps []map[string]interface{}
	if err := json.Unmarshal(data, &apps); err != nil {
		return nil, err
	}

	return apps, nil
}

// ========== Device State ==========

// XboxState represents the state of an Xbox
type XboxState struct {
	Name   string `json:"name"`
	Host   string `json:"host"`
	LiveID string `json:"live_id,omitempty"`
	Online bool   `json:"online"`
	Error  string `json:"error,omitempty"`
}

// GetState returns the current state of the Xbox
func (d *XboxDevice) GetState() *XboxState {
	state := &XboxState{
		Name:   d.Name,
		Host:   d.Host,
		LiveID: d.LiveID,
	}

	// Try to ping the Xbox
	conn, err := net.DialTimeout("udp", fmt.Sprintf("%s:%d", d.Host, sgDiscoveryPort), 2*time.Second)
	if err != nil {
		state.Online = false
		state.Error = "Device not reachable"
	} else {
		// Send discovery and wait for response
		conn.SetDeadline(time.Now().Add(2 * time.Second))
		packet := buildDiscoveryPacket()
		conn.Write(packet)

		buf := make([]byte, 256)
		n, err := conn.Read(buf)
		if err != nil || n == 0 {
			state.Online = false
			state.Error = "No response"
		} else {
			state.Online = true
		}
		conn.Close()
	}

	return state
}

// GetStateViaREST gets state via REST server
func (m *XboxManager) GetStateViaREST(deviceName string) (*XboxState, error) {
	device := m.devices[deviceName]
	if device == nil {
		return nil, fmt.Errorf("device not found: %s", deviceName)
	}

	data, err := m.restCall("GET", fmt.Sprintf("/device/%s", device.LiveID), nil)
	if err != nil {
		return &XboxState{
			Name:   device.Name,
			Host:   device.Host,
			LiveID: device.LiveID,
			Online: false,
			Error:  err.Error(),
		}, nil
	}

	var stateData map[string]interface{}
	if err := json.Unmarshal(data, &stateData); err != nil {
		return nil, err
	}

	state := &XboxState{
		Name:   device.Name,
		Host:   device.Host,
		LiveID: device.LiveID,
		Online: true,
	}

	return state, nil
}

// GetAllStates returns states of all devices
func (m *XboxManager) GetAllStates() []*XboxState {
	states := make([]*XboxState, 0, len(m.devices))
	for _, device := range m.devices {
		states = append(states, device.GetState())
	}
	return states
}

// generateNonce generates a random nonce for SmartGlass protocol
func generateNonce() []byte {
	nonce := make([]byte, 16)
	rand.Read(nonce)
	return nonce
}
