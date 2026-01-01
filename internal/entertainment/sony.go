package entertainment

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"time"
)

// SonyDevice represents a Sony device (soundbar or TV)
type SonyDevice struct {
	Name       string
	Host       string
	Port       int
	PSK        string // Pre-Shared Key
	DeviceType string // "soundbar" or "tv"
	httpClient *http.Client
}

// SonyManager manages multiple Sony devices
type SonyManager struct {
	devices    map[string]*SonyDevice
	httpClient *http.Client
}

// JSON-RPC request/response structures
type sonyRequest struct {
	Method  string        `json:"method"`
	Params  []interface{} `json:"params"`
	ID      int           `json:"id"`
	Version string        `json:"version"`
}

type sonyResponse struct {
	ID     int             `json:"id"`
	Result json.RawMessage `json:"result,omitempty"`
	Error  []interface{}   `json:"error,omitempty"`
}

// Volume info from Sony API
type VolumeInfo struct {
	Target  string `json:"target"`
	Volume  int    `json:"volume"`
	Mute    bool   `json:"mute"`
	MinVol  int    `json:"minVolume"`
	MaxVol  int    `json:"maxVolume"`
}

// Power status
type PowerStatus struct {
	Status string `json:"status"` // "active", "standby"
}

// Sound setting candidate option
type SoundSettingCandidate struct {
	Value       string `json:"value"`
	Title       string `json:"title"`
	IsAvailable bool   `json:"isAvailable"`
	Min         int    `json:"min,omitempty"`
	Max         int    `json:"max,omitempty"`
	Step        int    `json:"step,omitempty"`
}

// Sound setting
type SoundSetting struct {
	Target       string                  `json:"target"`
	CurrentValue string                  `json:"currentValue"`
	Title        string                  `json:"title,omitempty"`
	Type         string                  `json:"type,omitempty"`
	IsAvailable  bool                    `json:"isAvailable,omitempty"`
	Candidate    []SoundSettingCandidate `json:"candidate,omitempty"`
}

// Input source
type InputSource struct {
	URI   string `json:"uri"`
	Title string `json:"title"`
	Icon  string `json:"icon,omitempty"`
}

// PlayingContent info
type PlayingContent struct {
	URI   string `json:"uri"`
	Title string `json:"title"`
}

// NewSonyManager creates a new Sony device manager
func NewSonyManager() *SonyManager {
	return &SonyManager{
		devices: make(map[string]*SonyDevice),
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// AddDevice adds a Sony device to the manager
func (m *SonyManager) AddDevice(name, host string, port int, psk, deviceType string) {
	m.devices[name] = &SonyDevice{
		Name:       name,
		Host:       host,
		Port:       port,
		PSK:        psk,
		DeviceType: deviceType,
		httpClient: m.httpClient,
	}
	log.Printf("Added Sony %s: %s (%s:%d)", deviceType, name, host, port)
}

// GetDevice returns a device by name
func (m *SonyManager) GetDevice(name string) *SonyDevice {
	return m.devices[name]
}

// GetDevices returns all devices
func (m *SonyManager) GetDevices() map[string]*SonyDevice {
	return m.devices
}

// call makes a JSON-RPC call to a Sony device (default version 1.0)
func (d *SonyDevice) call(service, method string, params []interface{}) (*sonyResponse, error) {
	return d.callV(service, method, "1.0", params)
}

// callV makes a JSON-RPC call with a specific API version
func (d *SonyDevice) callV(service, method, version string, params []interface{}) (*sonyResponse, error) {
	if params == nil {
		params = []interface{}{}
	}

	reqBody := sonyRequest{
		Method:  method,
		Params:  params,
		ID:      1,
		Version: version,
	}

	jsonData, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	url := fmt.Sprintf("http://%s:%d/sony/%s", d.Host, d.Port, service)
	req, err := http.NewRequest("POST", url, bytes.NewReader(jsonData))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json; charset=UTF-8")
	// Only set PSK header if configured (TVs need it, soundbars don't)
	if d.PSK != "" {
		req.Header.Set("X-Auth-PSK", d.PSK)
	}

	resp, err := d.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(body))
	}

	var sonyResp sonyResponse
	if err := json.Unmarshal(body, &sonyResp); err != nil {
		return nil, fmt.Errorf("failed to unmarshal response: %w", err)
	}

	if len(sonyResp.Error) > 0 {
		return nil, fmt.Errorf("Sony API error: %v", sonyResp.Error)
	}

	return &sonyResp, nil
}

// ========== Power Control ==========

// GetPowerStatus returns the current power status
func (d *SonyDevice) GetPowerStatus() (*PowerStatus, error) {
	resp, err := d.callV("system", "getPowerStatus", "1.1", nil)
	if err != nil {
		return nil, err
	}

	var results []PowerStatus
	if err := json.Unmarshal(resp.Result, &results); err != nil {
		return nil, fmt.Errorf("failed to parse power status: %w", err)
	}

	if len(results) == 0 {
		return nil, fmt.Errorf("no power status returned")
	}

	return &results[0], nil
}

// SetPowerStatus sets the power status
// Soundbars use "on"/"off", TVs use "active"/"standby"
func (d *SonyDevice) SetPowerStatus(status string) error {
	params := []interface{}{
		map[string]interface{}{
			"status": status,
		},
	}
	_, err := d.callV("system", "setPowerStatus", "1.1", params)
	return err
}

// PowerOn turns on the device
func (d *SonyDevice) PowerOn() error {
	if d.DeviceType == "soundbar" {
		return d.SetPowerStatus("on")
	}
	return d.SetPowerStatus("active")
}

// PowerOff puts the device in standby/off
func (d *SonyDevice) PowerOff() error {
	if d.DeviceType == "soundbar" {
		return d.SetPowerStatus("off")
	}
	return d.SetPowerStatus("standby")
}

// ========== Volume Control ==========

// GetVolume returns the current volume info
func (d *SonyDevice) GetVolume() (*VolumeInfo, error) {
	// Soundbars require [{}] as params instead of []
	params := []interface{}{map[string]interface{}{}}
	resp, err := d.callV("audio", "getVolumeInformation", "1.1", params)
	if err != nil {
		return nil, err
	}

	var results [][]VolumeInfo
	if err := json.Unmarshal(resp.Result, &results); err != nil {
		return nil, fmt.Errorf("failed to parse volume info: %w", err)
	}

	if len(results) == 0 || len(results[0]) == 0 {
		return nil, fmt.Errorf("no volume info returned")
	}

	return &results[0][0], nil
}

// SetVolume sets the volume level (0-50 for soundbars, 0-100 for TVs)
func (d *SonyDevice) SetVolume(volume int) error {
	params := []interface{}{
		map[string]interface{}{
			"target": "",
			"volume": fmt.Sprintf("%d", volume),
		},
	}
	_, err := d.callV("audio", "setAudioVolume", "1.1", params)
	return err
}

// SetMute sets the mute state
func (d *SonyDevice) SetMute(mute bool) error {
	muteStr := "off"
	if mute {
		muteStr = "on"
	}
	params := []interface{}{
		map[string]interface{}{
			"mute": muteStr,
		},
	}
	_, err := d.callV("audio", "setAudioMute", "1.1", params)
	return err
}

// VolumeUp increases volume by step (default 1)
func (d *SonyDevice) VolumeUp(step int) error {
	if step <= 0 {
		step = 1
	}
	vol, err := d.GetVolume()
	if err != nil {
		return err
	}
	newVol := vol.Volume + step
	if newVol > vol.MaxVol {
		newVol = vol.MaxVol
	}
	return d.SetVolume(newVol)
}

// VolumeDown decreases volume by step (default 1)
func (d *SonyDevice) VolumeDown(step int) error {
	if step <= 0 {
		step = 1
	}
	vol, err := d.GetVolume()
	if err != nil {
		return err
	}
	newVol := vol.Volume - step
	if newVol < vol.MinVol {
		newVol = vol.MinVol
	}
	return d.SetVolume(newVol)
}

// ToggleMute toggles the mute state
func (d *SonyDevice) ToggleMute() error {
	vol, err := d.GetVolume()
	if err != nil {
		return err
	}
	return d.SetMute(!vol.Mute)
}

// ========== Input Control ==========

// GetInputs returns available input sources (TV only)
func (d *SonyDevice) GetInputs() ([]InputSource, error) {
	params := []interface{}{
		map[string]interface{}{
			"scheme": "extInput",
		},
	}
	resp, err := d.call("avContent", "getCurrentExternalInputsStatus", params)
	if err != nil {
		return nil, err
	}

	var results [][]InputSource
	if err := json.Unmarshal(resp.Result, &results); err != nil {
		return nil, fmt.Errorf("failed to parse inputs: %w", err)
	}

	if len(results) == 0 {
		return []InputSource{}, nil
	}

	return results[0], nil
}

// SetInput sets the active input source
func (d *SonyDevice) SetInput(uri string) error {
	params := []interface{}{
		map[string]interface{}{
			"uri": uri,
		},
	}
	_, err := d.callV("avContent", "setPlayContent", "1.2", params)
	return err
}

// SetHDMI switches to a specific HDMI input (1-4)
func (d *SonyDevice) SetHDMI(port int) error {
	uri := fmt.Sprintf("extInput:hdmi?port=%d", port)
	return d.SetInput(uri)
}

// SetTV switches to TV input (for soundbars connected via ARC/eARC)
func (d *SonyDevice) SetTV() error {
	return d.SetInput("extInput:tv")
}

// SetBluetooth switches to Bluetooth audio input
func (d *SonyDevice) SetBluetooth() error {
	return d.SetInput("extInput:btAudio")
}

// SetAnalog switches to analog line input
func (d *SonyDevice) SetAnalog() error {
	return d.SetInput("extInput:line")
}

// GetPlayingContent returns currently playing content info
func (d *SonyDevice) GetPlayingContent() (*PlayingContent, error) {
	resp, err := d.call("avContent", "getPlayingContentInfo", nil)
	if err != nil {
		return nil, err
	}

	var results []PlayingContent
	if err := json.Unmarshal(resp.Result, &results); err != nil {
		return nil, fmt.Errorf("failed to parse playing content: %w", err)
	}

	if len(results) == 0 {
		return nil, nil
	}

	return &results[0], nil
}

// ========== Sound Settings (Soundbar) ==========

// GetSoundSettings returns current sound settings
func (d *SonyDevice) GetSoundSettings() ([]SoundSetting, error) {
	params := []interface{}{
		map[string]interface{}{
			"target": "",
		},
	}
	resp, err := d.call("audio", "getSoundSettings", params)
	if err != nil {
		return nil, err
	}

	var results [][]SoundSetting
	if err := json.Unmarshal(resp.Result, &results); err != nil {
		return nil, fmt.Errorf("failed to parse sound settings: %w", err)
	}

	if len(results) == 0 {
		return []SoundSetting{}, nil
	}

	return results[0], nil
}

// SetSoundField sets the sound field/mode (e.g., "clearAudio", "movie", "music")
func (d *SonyDevice) SetSoundField(mode string) error {
	return d.SetSoundSetting("soundField", mode)
}

// SetSoundSetting sets any sound setting by target and value
func (d *SonyDevice) SetSoundSetting(target, value string) error {
	params := []interface{}{
		map[string]interface{}{
			"settings": []map[string]interface{}{
				{
					"target": target,
					"value":  value,
				},
			},
		},
	}
	_, err := d.call("audio", "setSoundSettings", params)
	return err
}

// ========== Remote Control (IRCC) ==========

// Common IRCC codes
var IRCCCodes = map[string]string{
	"power":       "AAAAAQAAAAEAAAAVAw==",
	"input":       "AAAAAQAAAAEAAAAlAw==",
	"mute":        "AAAAAQAAAAEAAAAUAw==",
	"volumeUp":    "AAAAAQAAAAEAAAASAw==",
	"volumeDown":  "AAAAAQAAAAEAAAATAw==",
	"channelUp":   "AAAAAQAAAAEAAAAQAw==",
	"channelDown": "AAAAAQAAAAEAAAARAw==",
	"up":          "AAAAAQAAAAEAAAB0Aw==",
	"down":        "AAAAAQAAAAEAAAB1Aw==",
	"left":        "AAAAAQAAAAEAAAB2Aw==",
	"right":       "AAAAAQAAAAEAAAB3Aw==",
	"enter":       "AAAAAQAAAAEAAABlAw==",
	"return":      "AAAAAgAAAJcAAAAjAw==",
	"home":        "AAAAAQAAAAEAAABgAw==",
	"options":     "AAAAAgAAAJcAAAA2Aw==",
	"play":        "AAAAAgAAAJcAAAAaAw==",
	"pause":       "AAAAAgAAAJcAAAAZAw==",
	"stop":        "AAAAAgAAAJcAAAAYAw==",
	"rewind":      "AAAAAgAAAJcAAAAbAw==",
	"forward":     "AAAAAgAAAJcAAAAcAw==",
	"netflix":     "AAAAAgAAABoAAAB8Aw==",
}

// SendIRCC sends an IRCC remote control command
func (d *SonyDevice) SendIRCC(code string) error {
	// Look up code name if provided
	if ircc, ok := IRCCCodes[code]; ok {
		code = ircc
	}

	params := []interface{}{
		map[string]interface{}{
			"IRCCCode": code,
		},
	}
	_, err := d.call("system", "setTextForm", params)
	if err != nil {
		// Try IRCC endpoint for TV
		_, err = d.call("IRCC", "sendIRCC", params)
	}
	return err
}

// ========== Device State Summary ==========

// DeviceState represents the full state of a Sony device
type DeviceState struct {
	Name       string       `json:"name"`
	DeviceType string       `json:"type"`
	Power      string       `json:"power"`
	Volume     int          `json:"volume"`
	Mute       bool         `json:"mute"`
	Input      string       `json:"input,omitempty"`
	Online     bool         `json:"online"`
	Error      string       `json:"error,omitempty"`
}

// GetState returns the current state of the device
func (d *SonyDevice) GetState() *DeviceState {
	state := &DeviceState{
		Name:       d.Name,
		DeviceType: d.DeviceType,
		Online:     true,
	}

	// Get power status
	power, err := d.GetPowerStatus()
	if err != nil {
		state.Online = false
		state.Error = err.Error()
		return state
	}
	state.Power = power.Status

	// Get volume if device is on
	if power.Status == "active" {
		if vol, err := d.GetVolume(); err == nil {
			state.Volume = vol.Volume
			state.Mute = vol.Mute
		}

		// Get current input (TV only)
		if d.DeviceType == "tv" {
			if content, err := d.GetPlayingContent(); err == nil && content != nil {
				state.Input = content.URI
			}
		}
	}

	return state
}

// GetAllStates returns states of all devices
func (m *SonyManager) GetAllStates() []*DeviceState {
	states := make([]*DeviceState, 0, len(m.devices))
	for _, device := range m.devices {
		states = append(states, device.GetState())
	}
	return states
}
