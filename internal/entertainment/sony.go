package entertainment

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"sync"
	"time"
)

// Cache settings
const (
	SonyCacheTTL      = 5 * time.Second  // How long cached state is valid
	SonyPollInterval  = 5 * time.Second  // Background polling interval
	SonyControlDelay  = 500 * time.Millisecond // Delay after control command before refreshing
)

// SonyDevice represents a Sony device (soundbar or TV)
type SonyDevice struct {
	Name       string
	Host       string
	Port       int
	PSK        string // Pre-Shared Key
	DeviceType string // "soundbar" or "tv"
	httpClient *http.Client

	// Cache
	cacheMu     sync.RWMutex
	cachedState *DeviceState
	cacheTime   time.Time
}

// SonyManager manages multiple Sony devices
type SonyManager struct {
	devices    map[string]*SonyDevice
	httpClient *http.Client

	// Background polling
	pollStop chan struct{}
	pollWg   sync.WaitGroup
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

// App info from appControl
type AppInfo struct {
	Title string `json:"title"`
	URI   string `json:"uri"`
	Icon  string `json:"icon,omitempty"`
	Data  string `json:"data,omitempty"`
}

// System information
type SystemInfo struct {
	Product    string `json:"product"`
	Region     string `json:"region"`
	Language   string `json:"language"`
	Model      string `json:"model"`
	Serial     string `json:"serial"`
	MacAddr    string `json:"macAddr"`
	Name       string `json:"name"`
	Generation string `json:"generation"`
	Area       string `json:"area"`
	CID        string `json:"cid"`
}

// LED status
type LEDStatus struct {
	Mode   string `json:"mode"`
	Status string `json:"status"`
}

// Power saving mode
type PowerSavingMode struct {
	Mode string `json:"mode"` // "off", "low", "high", "pictureOff"
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
	// TVs use version 1.0, soundbars use 1.1
	version := "1.0"
	if d.DeviceType == "soundbar" {
		version = "1.1"
	}
	resp, err := d.callV("system", "getPowerStatus", version, nil)
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
	// TVs use version 1.0, soundbars use 1.1
	version := "1.0"
	if d.DeviceType == "soundbar" {
		version = "1.1"
	}
	params := []interface{}{
		map[string]interface{}{
			"status": status,
		},
	}
	_, err := d.callV("system", "setPowerStatus", version, params)
	return err
}

// PowerOn turns on the device
func (d *SonyDevice) PowerOn() error {
	var err error
	if d.DeviceType == "soundbar" {
		err = d.SetPowerStatus("on")
	} else {
		err = d.SetPowerStatus("active")
	}
	if err == nil {
		d.InvalidateCache()
	}
	return err
}

// PowerOff puts the device in standby/off
func (d *SonyDevice) PowerOff() error {
	var err error
	if d.DeviceType == "soundbar" {
		err = d.SetPowerStatus("off")
	} else {
		err = d.SetPowerStatus("standby")
	}
	if err == nil {
		d.InvalidateCache()
	}
	return err
}

// ========== Volume Control ==========

// GetVolume returns the current volume info
func (d *SonyDevice) GetVolume() (*VolumeInfo, error) {
	// TVs use version 1.0, soundbars use 1.1
	version := "1.0"
	if d.DeviceType == "soundbar" {
		version = "1.1"
	}
	// Soundbars require [{}] as params instead of []
	params := []interface{}{map[string]interface{}{}}
	resp, err := d.callV("audio", "getVolumeInformation", version, params)
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
	// TVs use version 1.0, soundbars use 1.2
	version := "1.0"
	if d.DeviceType == "soundbar" {
		version = "1.2"
	}
	params := []interface{}{
		map[string]interface{}{
			"target": "",
			"volume": fmt.Sprintf("%d", volume),
		},
	}
	_, err := d.callV("audio", "setAudioVolume", version, params)
	if err == nil {
		d.InvalidateCache()
	}
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
	// Both TV and soundbar use version 1.0
	_, err := d.callV("audio", "setAudioMute", "1.0", params)
	if err == nil {
		d.InvalidateCache()
	}
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
	// TV uses version 1.0
	_, err := d.callV("avContent", "setPlayContent", "1.0", params)
	if err == nil {
		d.InvalidateCache()
	}
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
	// Both TV and soundbar use version 1.1
	resp, err := d.callV("audio", "getSoundSettings", "1.1", params)
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
	// Both TV and soundbar use version 1.1
	_, err := d.callV("audio", "setSoundSettings", "1.1", params)
	return err
}

// ========== App Control ==========

// GetApplicationList returns all installed apps
func (d *SonyDevice) GetApplicationList() ([]AppInfo, error) {
	resp, err := d.call("appControl", "getApplicationList", nil)
	if err != nil {
		return nil, err
	}

	var results []AppInfo
	if err := json.Unmarshal(resp.Result, &results); err != nil {
		return nil, fmt.Errorf("failed to parse app list: %w", err)
	}

	return results, nil
}

// LaunchApp launches an app by URI (e.g., "com.sony.dtv.tvx" or full URI)
func (d *SonyDevice) LaunchApp(uri string) error {
	params := []interface{}{
		map[string]interface{}{
			"uri": uri,
		},
	}
	_, err := d.call("appControl", "setActiveApp", params)
	return err
}

// TerminateApps closes all running apps
func (d *SonyDevice) TerminateApps() error {
	_, err := d.call("appControl", "terminateApps", nil)
	return err
}

// ========== System Control ==========

// GetSystemInfo returns system information
func (d *SonyDevice) GetSystemInfo() (*SystemInfo, error) {
	resp, err := d.call("system", "getSystemInformation", nil)
	if err != nil {
		return nil, err
	}

	var result SystemInfo
	if err := json.Unmarshal(resp.Result, &result); err != nil {
		return nil, fmt.Errorf("failed to parse system info: %w", err)
	}

	return &result, nil
}

// RequestReboot reboots the device
func (d *SonyDevice) RequestReboot() error {
	_, err := d.call("system", "requestReboot", nil)
	return err
}

// GetLEDStatus returns LED indicator status
func (d *SonyDevice) GetLEDStatus() (*LEDStatus, error) {
	resp, err := d.call("system", "getLEDIndicatorStatus", nil)
	if err != nil {
		return nil, err
	}

	var result LEDStatus
	if err := json.Unmarshal(resp.Result, &result); err != nil {
		return nil, fmt.Errorf("failed to parse LED status: %w", err)
	}

	return &result, nil
}

// SetLEDStatus sets LED indicator mode and status
// mode: "Demo", "AutoBrightnessAdjust", "Dark", "SimpleResponse", "Off"
// status: depends on mode
func (d *SonyDevice) SetLEDStatus(mode, status string) error {
	params := []interface{}{
		map[string]interface{}{
			"mode":   mode,
			"status": status,
		},
	}
	_, err := d.callV("system", "setLEDIndicatorStatus", "1.1", params)
	return err
}

// GetPowerSavingMode returns current power saving mode
func (d *SonyDevice) GetPowerSavingMode() (*PowerSavingMode, error) {
	resp, err := d.call("system", "getPowerSavingMode", nil)
	if err != nil {
		return nil, err
	}

	var result PowerSavingMode
	if err := json.Unmarshal(resp.Result, &result); err != nil {
		return nil, fmt.Errorf("failed to parse power saving mode: %w", err)
	}

	return &result, nil
}

// SetPowerSavingMode sets power saving mode
// mode: "off", "low", "high", "pictureOff"
func (d *SonyDevice) SetPowerSavingMode(mode string) error {
	params := []interface{}{
		map[string]interface{}{
			"mode": mode,
		},
	}
	_, err := d.call("system", "setPowerSavingMode", params)
	return err
}

// PictureOff turns off the picture (audio continues)
func (d *SonyDevice) PictureOff() error {
	return d.SetPowerSavingMode("pictureOff")
}

// PictureOn turns the picture back on
func (d *SonyDevice) PictureOn() error {
	return d.SetPowerSavingMode("off")
}

// ========== Remote Control (IRCC) ==========

// Common IRCC codes for Sony TVs
var IRCCCodes = map[string]string{
	// Power & Basic
	"power":       "AAAAAQAAAAEAAAAVAw==",
	"input":       "AAAAAQAAAAEAAAAlAw==",
	"mute":        "AAAAAQAAAAEAAAAUAw==",
	"volumeUp":    "AAAAAQAAAAEAAAASAw==",
	"volumeDown":  "AAAAAQAAAAEAAAATAw==",
	"channelUp":   "AAAAAQAAAAEAAAAQAw==",
	"channelDown": "AAAAAQAAAAEAAAARAw==",

	// Navigation
	"up":      "AAAAAQAAAAEAAAB0Aw==",
	"down":    "AAAAAQAAAAEAAAB1Aw==",
	"left":    "AAAAAQAAAAEAAAA0Aw==",
	"right":   "AAAAAQAAAAEAAAAzAw==",
	"enter":   "AAAAAQAAAAEAAABlAw==",
	"confirm": "AAAAAQAAAAEAAABlAw==",
	"return":  "AAAAAgAAAJcAAAAjAw==",
	"back":    "AAAAAgAAAJcAAAAjAw==",
	"home":    "AAAAAQAAAAEAAABgAw==",
	"options": "AAAAAgAAAJcAAAA2Aw==",
	"menu":    "AAAAAgAAAJcAAAA2Aw==",
	"guide":   "AAAAAgAAAKQAAABbAw==",
	"info":    "AAAAAQAAAAEAAAB/Aw==",
	"display": "AAAAAQAAAAEAAAB/Aw==",

	// Playback
	"play":       "AAAAAgAAAJcAAAAaAw==",
	"pause":      "AAAAAgAAAJcAAAAZAw==",
	"stop":       "AAAAAgAAAJcAAAAYAw==",
	"rewind":     "AAAAAgAAAJcAAAAbAw==",
	"forward":    "AAAAAgAAAJcAAAAcAw==",
	"prev":       "AAAAAgAAAJcAAAA8Aw==",
	"next":       "AAAAAgAAAJcAAAA9Aw==",
	"rec":        "AAAAAgAAAJcAAAAgAw==",
	"flashPlus":  "AAAAAgAAAJcAAAB4Aw==",
	"flashMinus": "AAAAAgAAAJcAAAB5Aw==",

	// HDMI Inputs
	"hdmi1": "AAAAAgAAABoAAABaAw==",
	"hdmi2": "AAAAAgAAABoAAABbAw==",
	"hdmi3": "AAAAAgAAABoAAABcAw==",
	"hdmi4": "AAAAAgAAABoAAABdAw==",

	// Streaming Apps
	"netflix":   "AAAAAgAAABoAAAB8Aw==",
	"youtube":   "AAAAAgAAAMQAAABHAw==",
	"primevideo": "AAAAAgAAABoAAAB9Aw==",
	"disney":    "AAAAAgAAAMQAAAA/Aw==",
	"appletv":   "AAAAAgAAAMQAAABGAw==",

	// Picture modes
	"pictureMode":   "AAAAAgAAAJcAAAA9Aw==",
	"pictureOff":    "AAAAAgAAAKQAAAASAw==",
	"wideMode":      "AAAAAgAAAKQAAAA9Aw==",
	"3D":            "AAAAAgAAAKQAAABkAw==",

	// Audio
	"audio":      "AAAAAQAAAAEAAAAXAw==",
	"syncMenu":   "AAAAAgAAABoAAABYAw==",
	"soundField": "AAAAAgAAAKQAAACyAw==",

	// Numbers
	"num0": "AAAAAQAAAAEAAAAJAw==",
	"num1": "AAAAAQAAAAEAAAAAAw==",
	"num2": "AAAAAQAAAAEAAAABAw==",
	"num3": "AAAAAQAAAAEAAAACAw==",
	"num4": "AAAAAQAAAAEAAAADAw==",
	"num5": "AAAAAQAAAAEAAAAEAw==",
	"num6": "AAAAAQAAAAEAAAAFAw==",
	"num7": "AAAAAQAAAAEAAAAGAw==",
	"num8": "AAAAAQAAAAEAAAAHAw==",
	"num9": "AAAAAQAAAAEAAAAIAw==",

	// Color buttons
	"red":    "AAAAAgAAAJcAAAAlAw==",
	"green":  "AAAAAgAAAJcAAAAmAw==",
	"yellow": "AAAAAgAAAJcAAAAnAw==",
	"blue":   "AAAAAgAAAJcAAAAkAw==",

	// Additional
	"actionMenu": "AAAAAgAAAMQAAABLAw==",
	"help":       "AAAAAgAAAMQAAABNAw==",
	"sleep":      "AAAAAgAAAJcAAAA4Aw==",
	"sleepTimer": "AAAAAgAAABoAAABvAw==",
	"subtitle":   "AAAAAgAAAJcAAAAoAw==",
	"closedCaption": "AAAAAgAAAKQAAAAQAw==",
	"teletext":   "AAAAAgAAAJcAAABBAw==",
}

// SendIRCC sends a remote control command
// For newer Sony TVs without IRCC SOAP support, this handles special commands via JSON-RPC
func (d *SonyDevice) SendIRCC(code string) error {
	// Handle HDMI switching commands via JSON-RPC (works on all Sony TVs)
	switch code {
	case "hdmi1":
		return d.SetHDMI(1)
	case "hdmi2":
		return d.SetHDMI(2)
	case "hdmi3":
		return d.SetHDMI(3)
	case "hdmi4":
		return d.SetHDMI(4)
	}

	// Handle streaming app commands via app launcher
	appURIs := map[string]string{
		"netflix":    "com.sony.dtv.com.netflix.ninja.com.netflix.ninja.MainActivity",
		"youtube":    "com.sony.dtv.com.google.android.youtube.tv.com.google.android.apps.youtube.tv.activity.ShellActivity",
		"primevideo": "com.sony.dtv.com.amazon.amazonvideo.livingroom.com.amazon.ignition.IgnitionActivity",
		"disney":     "com.sony.dtv.com.disney.disneyplus.com.bamtechmedia.domern.main.MainActivity",
		"appletv":    "com.sony.dtv.com.apple.atve.sony.appletv.com.apple.atve.sony.appletv.MainActivity",
	}
	if uri, ok := appURIs[code]; ok {
		return d.LaunchApp(uri)
	}

	// Handle home/menu commands via app control
	switch code {
	case "home":
		// Launch home screen
		return d.LaunchApp("com.sony.dtv.tvx")
	case "guide":
		return d.LaunchApp("com.sony.dtv.com.google.android.tvrecommendations.com.google.android.tvrecommendations.MainActivity")
	}

	// For navigation and other commands, try IRCC SOAP (may not work on newer TVs)
	// Look up code name if provided
	if ircc, ok := IRCCCodes[code]; ok {
		code = ircc
	}

	// Build SOAP envelope for IRCC command
	soapBody := fmt.Sprintf(`<?xml version="1.0"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1">
      <IRCCCode>%s</IRCCCode>
    </u:X_SendIRCC>
  </s:Body>
</s:Envelope>`, code)

	url := fmt.Sprintf("http://%s:%d/sony/ircc", d.Host, d.Port)
	req, err := http.NewRequest("POST", url, bytes.NewReader([]byte(soapBody)))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "text/xml; charset=UTF-8")
	req.Header.Set("SOAPACTION", `"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC"`)
	if d.PSK != "" {
		req.Header.Set("X-Auth-PSK", d.PSK)
	}

	resp, err := d.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(body))
	}

	return nil
}

// GetAvailableCommands returns a list of available IRCC command names
func GetAvailableCommands() []string {
	commands := make([]string, 0, len(IRCCCodes))
	for name := range IRCCCodes {
		commands = append(commands, name)
	}
	return commands
}

// ========== Device State Summary ==========

// DeviceState represents the full state of a Sony device
type DeviceState struct {
	Name       string `json:"name"`
	DeviceType string `json:"type"`
	Power      bool   `json:"power"`
	Volume     int    `json:"volume"`
	Mute       bool   `json:"mute"`
	Input      string `json:"input,omitempty"`
	Online     bool   `json:"online"`
	Error      string `json:"error,omitempty"`
}

// GetCachedState returns the cached state if valid, otherwise fetches fresh state
func (d *SonyDevice) GetCachedState() *DeviceState {
	d.cacheMu.RLock()
	if d.cachedState != nil && time.Since(d.cacheTime) < SonyCacheTTL {
		state := *d.cachedState // Copy
		d.cacheMu.RUnlock()
		return &state
	}
	d.cacheMu.RUnlock()

	// Cache miss or expired - fetch fresh state
	return d.RefreshState()
}

// RefreshState fetches fresh state and updates the cache
func (d *SonyDevice) RefreshState() *DeviceState {
	state := d.fetchState()

	d.cacheMu.Lock()
	d.cachedState = state
	d.cacheTime = time.Now()
	d.cacheMu.Unlock()

	return state
}

// InvalidateCache clears the cached state
func (d *SonyDevice) InvalidateCache() {
	d.cacheMu.Lock()
	d.cachedState = nil
	d.cacheMu.Unlock()
}

// fetchState actually fetches the state from the device (no caching)
func (d *SonyDevice) fetchState() *DeviceState {
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
	// Convert string status to boolean (TV uses "active", soundbar uses "on")
	state.Power = power.Status == "active" || power.Status == "on"

	// Get volume if device is on
	if state.Power {
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

// GetState returns the current state (uses cache)
func (d *SonyDevice) GetState() *DeviceState {
	return d.GetCachedState()
}

// GetAllStates returns states of all devices (uses cache)
func (m *SonyManager) GetAllStates() []*DeviceState {
	states := make([]*DeviceState, 0, len(m.devices))
	for _, device := range m.devices {
		states = append(states, device.GetCachedState())
	}
	return states
}

// ========== Background Polling ==========

// StartPolling starts background polling for all devices
func (m *SonyManager) StartPolling() {
	if m.pollStop != nil {
		return // Already polling
	}

	m.pollStop = make(chan struct{})
	m.pollWg.Add(1)

	go func() {
		defer m.pollWg.Done()
		ticker := time.NewTicker(SonyPollInterval)
		defer ticker.Stop()

		log.Printf("Sony: Started background polling every %v", SonyPollInterval)

		// Initial poll
		m.pollAllDevices()

		for {
			select {
			case <-ticker.C:
				m.pollAllDevices()
			case <-m.pollStop:
				log.Printf("Sony: Stopped background polling")
				return
			}
		}
	}()
}

// StopPolling stops background polling
func (m *SonyManager) StopPolling() {
	if m.pollStop != nil {
		close(m.pollStop)
		m.pollWg.Wait()
		m.pollStop = nil
	}
}

// pollAllDevices refreshes state for all devices
func (m *SonyManager) pollAllDevices() {
	for _, device := range m.devices {
		device.RefreshState()
	}
}

// RefreshAll forces a refresh of all device states
func (m *SonyManager) RefreshAll() {
	m.pollAllDevices()
}
