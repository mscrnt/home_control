package amcrest

import (
	"bytes"
	"crypto/md5"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Client represents an Amcrest camera API client
type Client struct {
	host       string
	username   string
	password   string
	httpClient *http.Client
	mu         sync.RWMutex
	deviceInfo *DeviceInfo
}

// DeviceInfo contains camera device information
type DeviceInfo struct {
	DeviceType      string `json:"deviceType"`
	HardwareVersion string `json:"hardwareVersion"`
	SoftwareVersion string `json:"softwareVersion"`
	BuildDate       string `json:"buildDate"`
	SerialNumber    string `json:"serialNumber"`
	IsDoorbell      bool   `json:"isDoorbell"`
}

// MotionDetectConfig represents motion detection settings
type MotionDetectConfig struct {
	Enable      bool `json:"enable"`
	Sensitivity int  `json:"sensitivity"` // 1-6 (1=lowest, 6=highest)
}

// VideoConfig represents video settings
type VideoConfig struct {
	NightVisionMode string `json:"nightVisionMode"` // "Auto", "Color", "BlackWhite"
	IRLedMode       string `json:"irLedMode"`       // "Auto", "Manual", "Off"
	Brightness      int    `json:"brightness"`      // 0-100
	Contrast        int    `json:"contrast"`        // 0-100
	Saturation      int    `json:"saturation"`      // 0-100
	Sharpness       int    `json:"sharpness"`       // 0-100
}

// DoorbellConfig represents doorbell-specific settings
type DoorbellConfig struct {
	RingEnable     bool   `json:"ringEnable"`
	RingVolume     int    `json:"ringVolume"`     // 0-100
	TalkVolume     int    `json:"talkVolume"`     // 0-100
	RingTone       string `json:"ringTone"`       // Ring tone file
	LEDEnabled     bool   `json:"ledEnabled"`
	LEDBrightness  int    `json:"ledBrightness"`  // 0-100
	ButtonLightMode string `json:"buttonLightMode"` // "Auto", "AlwaysOn", "AlwaysOff"
}

// PTZCommand represents a PTZ operation
type PTZCommand string

const (
	PTZUp        PTZCommand = "Up"
	PTZDown      PTZCommand = "Down"
	PTZLeft      PTZCommand = "Left"
	PTZRight     PTZCommand = "Right"
	PTZZoomIn    PTZCommand = "ZoomTele"
	PTZZoomOut   PTZCommand = "ZoomWide"
	PTZFocusNear PTZCommand = "FocusNear"
	PTZFocusFar  PTZCommand = "FocusFar"
	PTZStop      PTZCommand = "Stop"
)

// EventType represents camera event types
type EventType string

const (
	EventMotion   EventType = "VideoMotion"
	EventDoorbell EventType = "CallNoAnswer" // Doorbell ring
	EventAudio    EventType = "AudioMutation"
	EventPIR      EventType = "PIR"          // Passive infrared
)

// Event represents a camera event
type Event struct {
	Type      EventType `json:"type"`
	Code      string    `json:"code"`
	Action    string    `json:"action"` // "Start" or "Stop"
	Index     int       `json:"index"`
	Timestamp time.Time `json:"timestamp"`
}

// NewClient creates a new Amcrest API client
func NewClient(host, username, password string) *Client {
	return &Client{
		host:     host,
		username: username,
		password: password,
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// doDigestRequest performs an HTTP request with digest authentication
func (c *Client) doDigestRequest(method, endpoint string, body []byte) (*http.Response, error) {
	requestURL := fmt.Sprintf("http://%s%s", c.host, endpoint)

	// First request to get the WWW-Authenticate challenge
	var bodyReader io.Reader
	if body != nil {
		bodyReader = bytes.NewReader(body)
	}
	req, err := http.NewRequest(method, requestURL, bodyReader)
	if err != nil {
		return nil, err
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}

	// If not 401, return the response
	if resp.StatusCode != http.StatusUnauthorized {
		return resp, nil
	}
	resp.Body.Close()

	// Parse the WWW-Authenticate header
	authHeader := resp.Header.Get("WWW-Authenticate")
	if authHeader == "" || !strings.HasPrefix(authHeader, "Digest ") {
		return nil, fmt.Errorf("no digest auth challenge received")
	}

	// Extract digest parameters
	realm := extractParam(authHeader, "realm")
	nonce := extractParam(authHeader, "nonce")
	qop := extractParam(authHeader, "qop")

	// Parse the URL for the URI
	parsedURL, _ := url.Parse(requestURL)
	uri := parsedURL.RequestURI()

	// Calculate digest response
	ha1 := md5sum(fmt.Sprintf("%s:%s:%s", c.username, realm, c.password))
	ha2 := md5sum(fmt.Sprintf("%s:%s", method, uri))

	var response string
	nc := "00000001"
	cnonce := fmt.Sprintf("%08x", time.Now().UnixNano())

	if qop != "" {
		response = md5sum(fmt.Sprintf("%s:%s:%s:%s:%s:%s", ha1, nonce, nc, cnonce, qop, ha2))
	} else {
		response = md5sum(fmt.Sprintf("%s:%s:%s", ha1, nonce, ha2))
	}

	// Build Authorization header
	authValue := fmt.Sprintf(`Digest username="%s", realm="%s", nonce="%s", uri="%s", response="%s"`,
		c.username, realm, nonce, uri, response)
	if qop != "" {
		authValue += fmt.Sprintf(`, qop=%s, nc=%s, cnonce="%s"`, qop, nc, cnonce)
	}

	// Make the authenticated request
	if body != nil {
		bodyReader = bytes.NewReader(body)
	}
	req2, err := http.NewRequest(method, requestURL, bodyReader)
	if err != nil {
		return nil, err
	}
	req2.Header.Set("Authorization", authValue)

	return c.httpClient.Do(req2)
}

// get performs an authenticated GET request
func (c *Client) get(endpoint string) (string, error) {
	resp, err := c.doDigestRequest("GET", endpoint, nil)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("request failed with status %d", resp.StatusCode)
	}

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}

	return string(bodyBytes), nil
}

// set performs an authenticated GET request for setting a config value
func (c *Client) set(endpoint string) error {
	resp, err := c.doDigestRequest("GET", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("request failed with status %d: %s", resp.StatusCode, string(bodyBytes))
	}

	// Check for OK response
	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return err
	}

	body := string(bodyBytes)
	if !strings.Contains(body, "OK") && !strings.Contains(body, "ok") {
		return fmt.Errorf("unexpected response: %s", body)
	}

	return nil
}

// extractParam extracts a parameter value from a WWW-Authenticate header
func extractParam(header, param string) string {
	re := regexp.MustCompile(param + `="([^"]*)"`)
	matches := re.FindStringSubmatch(header)
	if len(matches) >= 2 {
		return matches[1]
	}
	// Try without quotes
	re = regexp.MustCompile(param + `=([^,\s]*)`)
	matches = re.FindStringSubmatch(header)
	if len(matches) >= 2 {
		return matches[1]
	}
	return ""
}

// md5sum returns the MD5 hash of a string as a hex string
func md5sum(s string) string {
	return fmt.Sprintf("%x", md5.Sum([]byte(s)))
}

// parseConfigValue extracts a value from Amcrest config response
// Format: table.Config[0].Key=Value or Config.Key=Value
func parseConfigValue(body, key string) string {
	lines := strings.Split(body, "\n")
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if strings.Contains(line, key+"=") {
			parts := strings.SplitN(line, "=", 2)
			if len(parts) == 2 {
				return strings.TrimSpace(parts[1])
			}
		}
	}
	return ""
}

// parseConfigBool parses a boolean value from config
func parseConfigBool(body, key string) bool {
	val := parseConfigValue(body, key)
	return val == "true" || val == "1"
}

// parseConfigInt parses an integer value from config
func parseConfigInt(body, key string) int {
	val := parseConfigValue(body, key)
	i, _ := strconv.Atoi(val)
	return i
}

// ==================== Device Info Methods ====================

// GetDeviceType returns the camera's device type (e.g., "AD410", "IP4M-1041B")
func (c *Client) GetDeviceType() (string, error) {
	body, err := c.get("/cgi-bin/magicBox.cgi?action=getDeviceType")
	if err != nil {
		return "", err
	}
	// Response: type=AD410
	return parseConfigValue(body, "type"), nil
}

// GetSoftwareVersion returns the camera's software/firmware version
func (c *Client) GetSoftwareVersion() (string, string, error) {
	body, err := c.get("/cgi-bin/magicBox.cgi?action=getSoftwareVersion")
	if err != nil {
		return "", "", err
	}
	// Response: version=1.000.00AC002.0.R,build=2023-08-10
	version := parseConfigValue(body, "version")
	build := parseConfigValue(body, "build")
	return version, build, nil
}

// GetSerialNumber returns the camera's serial number
func (c *Client) GetSerialNumber() (string, error) {
	body, err := c.get("/cgi-bin/magicBox.cgi?action=getSerialNo")
	if err != nil {
		return "", err
	}
	return parseConfigValue(body, "sn"), nil
}

// GetHardwareVersion returns the camera's hardware version
func (c *Client) GetHardwareVersion() (string, error) {
	body, err := c.get("/cgi-bin/magicBox.cgi?action=getHardwareVersion")
	if err != nil {
		return "", err
	}
	return parseConfigValue(body, "version"), nil
}

// GetDeviceInfo retrieves and caches comprehensive device information
func (c *Client) GetDeviceInfo() (*DeviceInfo, error) {
	c.mu.RLock()
	if c.deviceInfo != nil {
		info := c.deviceInfo
		c.mu.RUnlock()
		return info, nil
	}
	c.mu.RUnlock()

	c.mu.Lock()
	defer c.mu.Unlock()

	// Double-check after acquiring write lock
	if c.deviceInfo != nil {
		return c.deviceInfo, nil
	}

	deviceType, err := c.GetDeviceType()
	if err != nil {
		return nil, fmt.Errorf("failed to get device type: %w", err)
	}

	version, buildDate, _ := c.GetSoftwareVersion()
	serialNumber, _ := c.GetSerialNumber()
	hwVersion, _ := c.GetHardwareVersion()

	// Detect if it's a doorbell based on device type
	isDoorbell := strings.Contains(strings.ToUpper(deviceType), "AD") ||
		strings.Contains(strings.ToLower(deviceType), "doorbell")

	c.deviceInfo = &DeviceInfo{
		DeviceType:      deviceType,
		HardwareVersion: hwVersion,
		SoftwareVersion: version,
		BuildDate:       buildDate,
		SerialNumber:    serialNumber,
		IsDoorbell:      isDoorbell,
	}

	log.Printf("Amcrest device info: %s (doorbell=%v, fw=%s)", deviceType, isDoorbell, version)
	return c.deviceInfo, nil
}

// IsDoorbell checks if this camera is a doorbell
func (c *Client) IsDoorbell() bool {
	info, err := c.GetDeviceInfo()
	if err != nil {
		return false
	}
	return info.IsDoorbell
}

// ==================== Snapshot Methods ====================

// GetSnapshot returns a JPEG snapshot from the camera
func (c *Client) GetSnapshot() ([]byte, error) {
	resp, err := c.doDigestRequest("GET", "/cgi-bin/snapshot.cgi", nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("snapshot request failed with status %d", resp.StatusCode)
	}

	return io.ReadAll(resp.Body)
}

// ==================== Motion Detection Methods ====================

// GetMotionDetectConfig retrieves motion detection settings
func (c *Client) GetMotionDetectConfig(channel int) (*MotionDetectConfig, error) {
	body, err := c.get("/cgi-bin/configManager.cgi?action=getConfig&name=MotionDetect")
	if err != nil {
		return nil, err
	}

	prefix := fmt.Sprintf("MotionDetect[%d]", channel)
	return &MotionDetectConfig{
		Enable:      parseConfigBool(body, prefix+".Enable"),
		Sensitivity: parseConfigInt(body, prefix+".Level"),
	}, nil
}

// SetMotionDetectEnable enables or disables motion detection
func (c *Client) SetMotionDetectEnable(channel int, enable bool) error {
	enableStr := "false"
	if enable {
		enableStr = "true"
	}
	endpoint := fmt.Sprintf("/cgi-bin/configManager.cgi?action=setConfig&MotionDetect[%d].Enable=%s", channel, enableStr)
	return c.set(endpoint)
}

// SetMotionDetectSensitivity sets motion detection sensitivity (1-6)
func (c *Client) SetMotionDetectSensitivity(channel int, level int) error {
	if level < 1 {
		level = 1
	} else if level > 6 {
		level = 6
	}
	endpoint := fmt.Sprintf("/cgi-bin/configManager.cgi?action=setConfig&MotionDetect[%d].Level=%d", channel, level)
	return c.set(endpoint)
}

// ==================== Video Settings Methods ====================

// GetVideoConfig retrieves video settings for a channel
func (c *Client) GetVideoConfig(channel int) (*VideoConfig, error) {
	// Get video color settings
	colorBody, err := c.get("/cgi-bin/configManager.cgi?action=getConfig&name=VideoColor")
	if err != nil {
		return nil, err
	}

	// Get video input options (night vision)
	inputBody, err := c.get("/cgi-bin/configManager.cgi?action=getConfig&name=VideoInOptions")
	if err != nil {
		return nil, err
	}

	colorPrefix := fmt.Sprintf("VideoColor[%d][0]", channel)
	return &VideoConfig{
		NightVisionMode: parseConfigValue(inputBody, fmt.Sprintf("VideoInOptions[%d].NightOptions.SwitchMode", channel)),
		IRLedMode:       parseConfigValue(inputBody, fmt.Sprintf("VideoInOptions[%d].InfraRed.Mode", channel)),
		Brightness:      parseConfigInt(colorBody, colorPrefix+".Brightness"),
		Contrast:        parseConfigInt(colorBody, colorPrefix+".Contrast"),
		Saturation:      parseConfigInt(colorBody, colorPrefix+".Saturation"),
		Sharpness:       parseConfigInt(colorBody, colorPrefix+".Sharpness"),
	}, nil
}

// SetNightVisionMode sets the night vision mode ("Auto", "Color", "BlackWhite")
func (c *Client) SetNightVisionMode(channel int, mode string) error {
	endpoint := fmt.Sprintf("/cgi-bin/configManager.cgi?action=setConfig&VideoInOptions[%d].NightOptions.SwitchMode=%s", channel, mode)
	return c.set(endpoint)
}

// SetIRLedMode sets the IR LED mode ("Auto", "Manual", "Off")
func (c *Client) SetIRLedMode(channel int, mode string) error {
	endpoint := fmt.Sprintf("/cgi-bin/configManager.cgi?action=setConfig&VideoInOptions[%d].InfraRed.Mode=%s", channel, mode)
	return c.set(endpoint)
}

// SetBrightness sets video brightness (0-100)
func (c *Client) SetBrightness(channel int, value int) error {
	endpoint := fmt.Sprintf("/cgi-bin/configManager.cgi?action=setConfig&VideoColor[%d][0].Brightness=%d", channel, clamp(value, 0, 100))
	return c.set(endpoint)
}

// SetContrast sets video contrast (0-100)
func (c *Client) SetContrast(channel int, value int) error {
	endpoint := fmt.Sprintf("/cgi-bin/configManager.cgi?action=setConfig&VideoColor[%d][0].Contrast=%d", channel, clamp(value, 0, 100))
	return c.set(endpoint)
}

// ==================== PTZ Control Methods ====================

// PTZControl sends a PTZ command to the camera
func (c *Client) PTZControl(channel int, cmd PTZCommand, arg1, arg2 int, arg3 bool) error {
	endpoint := fmt.Sprintf("/cgi-bin/ptz.cgi?action=start&channel=%d&code=%s&arg1=%d&arg2=%d&arg3=%d",
		channel, cmd, arg1, arg2, boolToInt(arg3))
	return c.set(endpoint)
}

// PTZStop stops PTZ movement
func (c *Client) PTZStop(channel int) error {
	return c.PTZControl(channel, PTZStop, 0, 0, false)
}

// PTZMove moves the camera in the specified direction with speed (1-8)
func (c *Client) PTZMove(channel int, direction PTZCommand, speed int) error {
	if speed < 1 {
		speed = 1
	} else if speed > 8 {
		speed = 8
	}
	return c.PTZControl(channel, direction, 0, speed, false)
}

// PTZZoom zooms the camera (positive for zoom in, negative for zoom out)
func (c *Client) PTZZoom(channel int, zoomIn bool, speed int) error {
	cmd := PTZZoomOut
	if zoomIn {
		cmd = PTZZoomIn
	}
	return c.PTZControl(channel, cmd, 0, speed, false)
}

// PTZGotoPreset moves camera to a preset position
func (c *Client) PTZGotoPreset(channel int, preset int) error {
	endpoint := fmt.Sprintf("/cgi-bin/ptz.cgi?action=start&channel=%d&code=GotoPreset&arg1=0&arg2=%d&arg3=0", channel, preset)
	return c.set(endpoint)
}

// PTZSetPreset saves current position as a preset
func (c *Client) PTZSetPreset(channel int, preset int) error {
	endpoint := fmt.Sprintf("/cgi-bin/ptz.cgi?action=start&channel=%d&code=SetPreset&arg1=0&arg2=%d&arg3=0", channel, preset)
	return c.set(endpoint)
}

// ==================== Doorbell-Specific Methods ====================

// GetDoorbellConfig retrieves doorbell-specific settings (only for doorbell cameras)
func (c *Client) GetDoorbellConfig() (*DoorbellConfig, error) {
	if !c.IsDoorbell() {
		return nil, fmt.Errorf("not a doorbell camera")
	}

	// Get doorbell talk settings
	talkBody, err := c.get("/cgi-bin/configManager.cgi?action=getConfig&name=VideoTalkPhoneGeneral")
	if err != nil {
		return nil, err
	}

	// Get LED settings
	ledBody, err := c.get("/cgi-bin/configManager.cgi?action=getConfig&name=LightGlobal")
	if err != nil {
		// LED settings might not be available on all doorbells
		ledBody = ""
	}

	// Get ring settings
	ringBody, err := c.get("/cgi-bin/configManager.cgi?action=getConfig&name=VideoTalk")
	if err != nil {
		ringBody = ""
	}

	config := &DoorbellConfig{
		RingEnable:    parseConfigBool(talkBody, "VideoTalkPhoneGeneral.RingEnable"),
		RingVolume:    parseConfigInt(talkBody, "VideoTalkPhoneGeneral.RingVolume"),
		TalkVolume:    parseConfigInt(talkBody, "VideoTalkPhoneGeneral.TalkVolume"),
		RingTone:      parseConfigValue(talkBody, "VideoTalkPhoneGeneral.RingFile"),
		LEDEnabled:    parseConfigBool(ledBody, "LightGlobal[0].Enable"),
		LEDBrightness: parseConfigInt(ledBody, "LightGlobal[0].Brightness"),
		ButtonLightMode: parseConfigValue(ledBody, "LightGlobal[0].Mode"),
	}

	// Get button light config if available
	if ringBody != "" {
		config.RingEnable = parseConfigBool(ringBody, "VideoTalk[0].RingEnable")
	}

	return config, nil
}

// SetDoorbellRingVolume sets the ring volume (0-100)
func (c *Client) SetDoorbellRingVolume(volume int) error {
	if !c.IsDoorbell() {
		return fmt.Errorf("not a doorbell camera")
	}
	endpoint := fmt.Sprintf("/cgi-bin/configManager.cgi?action=setConfig&VideoTalkPhoneGeneral.RingVolume=%d", clamp(volume, 0, 100))
	return c.set(endpoint)
}

// SetDoorbellTalkVolume sets the two-way talk volume (0-100)
func (c *Client) SetDoorbellTalkVolume(volume int) error {
	if !c.IsDoorbell() {
		return fmt.Errorf("not a doorbell camera")
	}
	endpoint := fmt.Sprintf("/cgi-bin/configManager.cgi?action=setConfig&VideoTalkPhoneGeneral.TalkVolume=%d", clamp(volume, 0, 100))
	return c.set(endpoint)
}

// SetDoorbellRingEnable enables or disables the doorbell ring
func (c *Client) SetDoorbellRingEnable(enable bool) error {
	if !c.IsDoorbell() {
		return fmt.Errorf("not a doorbell camera")
	}
	enableStr := "false"
	if enable {
		enableStr = "true"
	}
	endpoint := fmt.Sprintf("/cgi-bin/configManager.cgi?action=setConfig&VideoTalkPhoneGeneral.RingEnable=%s", enableStr)
	return c.set(endpoint)
}

// SetDoorbellLED controls the doorbell LED
func (c *Client) SetDoorbellLED(enable bool, brightness int) error {
	if !c.IsDoorbell() {
		return fmt.Errorf("not a doorbell camera")
	}
	enableStr := "false"
	if enable {
		enableStr = "true"
	}
	endpoint := fmt.Sprintf("/cgi-bin/configManager.cgi?action=setConfig&LightGlobal[0].Enable=%s&LightGlobal[0].Brightness=%d",
		enableStr, clamp(brightness, 0, 100))
	return c.set(endpoint)
}

// TriggerDoorbellRing manually triggers the doorbell ring
func (c *Client) TriggerDoorbellRing() error {
	if !c.IsDoorbell() {
		return fmt.Errorf("not a doorbell camera")
	}
	endpoint := "/cgi-bin/devAudioOutput.cgi?action=audioOutput&channel=1&open=1"
	return c.set(endpoint)
}

// ==================== Audio Methods ====================

// GetAudioInputConfig retrieves audio input settings
func (c *Client) GetAudioInputConfig(channel int) (map[string]string, error) {
	body, err := c.get(fmt.Sprintf("/cgi-bin/configManager.cgi?action=getConfig&name=AudioInput[%d]", channel))
	if err != nil {
		return nil, err
	}

	config := make(map[string]string)
	lines := strings.Split(body, "\n")
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if strings.Contains(line, "=") {
			parts := strings.SplitN(line, "=", 2)
			if len(parts) == 2 {
				config[parts[0]] = parts[1]
			}
		}
	}
	return config, nil
}

// SetAudioVolume sets the audio output volume
func (c *Client) SetAudioVolume(channel int, volume int) error {
	endpoint := fmt.Sprintf("/cgi-bin/configManager.cgi?action=setConfig&AudioOutput[%d].Volume=%d", channel, clamp(volume, 0, 100))
	return c.set(endpoint)
}

// ==================== Event Methods ====================

// SubscribeEvents starts a long-polling event subscription
// The callback is called for each event received
// This function blocks until the context is cancelled or an error occurs
func (c *Client) SubscribeEvents(eventTypes []EventType, callback func(Event)) error {
	// Build event code list
	codes := make([]string, len(eventTypes))
	for i, et := range eventTypes {
		codes[i] = string(et)
	}
	codeList := strings.Join(codes, ",")

	endpoint := fmt.Sprintf("/cgi-bin/eventManager.cgi?action=attach&codes=[%s]", codeList)

	// Use a client with no timeout for event streaming
	streamClient := &http.Client{Timeout: 0}

	requestURL := fmt.Sprintf("http://%s%s", c.host, endpoint)

	// Get auth challenge first
	req, err := http.NewRequest("GET", requestURL, nil)
	if err != nil {
		return err
	}

	resp, err := streamClient.Do(req)
	if err != nil {
		return err
	}

	if resp.StatusCode == http.StatusUnauthorized {
		resp.Body.Close()

		// Parse the WWW-Authenticate header
		authHeader := resp.Header.Get("WWW-Authenticate")
		if authHeader == "" || !strings.HasPrefix(authHeader, "Digest ") {
			return fmt.Errorf("no digest auth challenge received")
		}

		realm := extractParam(authHeader, "realm")
		nonce := extractParam(authHeader, "nonce")
		qop := extractParam(authHeader, "qop")

		parsedURL, _ := url.Parse(requestURL)
		uri := parsedURL.RequestURI()

		ha1 := md5sum(fmt.Sprintf("%s:%s:%s", c.username, realm, c.password))
		ha2 := md5sum(fmt.Sprintf("GET:%s", uri))

		var response string
		nc := "00000001"
		cnonce := fmt.Sprintf("%08x", time.Now().UnixNano())

		if qop != "" {
			response = md5sum(fmt.Sprintf("%s:%s:%s:%s:%s:%s", ha1, nonce, nc, cnonce, qop, ha2))
		} else {
			response = md5sum(fmt.Sprintf("%s:%s:%s", ha1, nonce, ha2))
		}

		authValue := fmt.Sprintf(`Digest username="%s", realm="%s", nonce="%s", uri="%s", response="%s"`,
			c.username, realm, nonce, uri, response)
		if qop != "" {
			authValue += fmt.Sprintf(`, qop=%s, nc=%s, cnonce="%s"`, qop, nc, cnonce)
		}

		req2, err := http.NewRequest("GET", requestURL, nil)
		if err != nil {
			return err
		}
		req2.Header.Set("Authorization", authValue)

		resp, err = streamClient.Do(req2)
		if err != nil {
			return err
		}
	}

	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("event subscription failed with status %d", resp.StatusCode)
	}

	// Read events from the multipart stream
	buf := make([]byte, 4096)
	var eventBuffer strings.Builder

	for {
		n, err := resp.Body.Read(buf)
		if err != nil {
			if err == io.EOF {
				break
			}
			return err
		}

		eventBuffer.Write(buf[:n])
		content := eventBuffer.String()

		// Parse events from the buffer
		// Events are in format: Code=VideoMotion;action=Start;index=0
		lines := strings.Split(content, "\n")
		for i, line := range lines {
			line = strings.TrimSpace(line)
			if strings.HasPrefix(line, "Code=") {
				event := parseEventLine(line)
				if event != nil {
					callback(*event)
				}
				// Clear processed lines
				if i < len(lines)-1 {
					eventBuffer.Reset()
					eventBuffer.WriteString(strings.Join(lines[i+1:], "\n"))
				} else {
					eventBuffer.Reset()
				}
				break
			}
		}
	}

	return nil
}

// parseEventLine parses an event line like "Code=VideoMotion;action=Start;index=0"
func parseEventLine(line string) *Event {
	event := &Event{
		Timestamp: time.Now(),
	}

	parts := strings.Split(line, ";")
	for _, part := range parts {
		kv := strings.SplitN(part, "=", 2)
		if len(kv) != 2 {
			continue
		}
		key := strings.ToLower(kv[0])
		value := kv[1]

		switch key {
		case "code":
			event.Code = value
			event.Type = EventType(value)
		case "action":
			event.Action = value
		case "index":
			event.Index, _ = strconv.Atoi(value)
		}
	}

	if event.Code == "" {
		return nil
	}

	return event
}

// ==================== Recording Methods ====================

// StartManualRecord starts manual recording on a channel
func (c *Client) StartManualRecord(channel int) error {
	endpoint := fmt.Sprintf("/cgi-bin/recManager.cgi?action=startManualRecord&channel=%d", channel)
	return c.set(endpoint)
}

// StopManualRecord stops manual recording on a channel
func (c *Client) StopManualRecord(channel int) error {
	endpoint := fmt.Sprintf("/cgi-bin/recManager.cgi?action=stopManualRecord&channel=%d", channel)
	return c.set(endpoint)
}

// GetRecordMode retrieves the recording mode for a channel
func (c *Client) GetRecordMode(channel int) (string, error) {
	body, err := c.get(fmt.Sprintf("/cgi-bin/configManager.cgi?action=getConfig&name=RecordMode[%d]", channel))
	if err != nil {
		return "", err
	}
	return parseConfigValue(body, fmt.Sprintf("RecordMode[%d].Mode", channel)), nil
}

// ==================== System Methods ====================

// Reboot reboots the camera
func (c *Client) Reboot() error {
	endpoint := "/cgi-bin/magicBox.cgi?action=reboot"
	_, err := c.get(endpoint)
	return err
}

// GetCurrentTime gets the camera's current time
func (c *Client) GetCurrentTime() (time.Time, error) {
	body, err := c.get("/cgi-bin/global.cgi?action=getCurrentTime")
	if err != nil {
		return time.Time{}, err
	}
	// Response: result=2024-01-15 10:30:45
	timeStr := parseConfigValue(body, "result")
	return time.Parse("2006-01-02 15:04:05", timeStr)
}

// GetStorageInfo gets storage information
func (c *Client) GetStorageInfo() (map[string]string, error) {
	body, err := c.get("/cgi-bin/storageDevice.cgi?action=getDeviceAllInfo")
	if err != nil {
		return nil, err
	}

	info := make(map[string]string)
	lines := strings.Split(body, "\n")
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if strings.Contains(line, "=") {
			parts := strings.SplitN(line, "=", 2)
			if len(parts) == 2 {
				info[parts[0]] = parts[1]
			}
		}
	}
	return info, nil
}

// ==================== Utility Functions ====================

func clamp(value, min, max int) int {
	if value < min {
		return min
	}
	if value > max {
		return max
	}
	return value
}

func boolToInt(b bool) int {
	if b {
		return 1
	}
	return 0
}

// ToJSON returns the device info as JSON
func (d *DeviceInfo) ToJSON() string {
	b, _ := json.Marshal(d)
	return string(b)
}
