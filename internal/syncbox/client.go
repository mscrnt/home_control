package syncbox

import (
	"bytes"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// Client represents a Hue Sync Box API client
type Client struct {
	ip          string
	accessToken string
	name        string
	httpClient  *http.Client
}

// DeviceInfo contains basic Sync Box device information
type DeviceInfo struct {
	Name             string `json:"name"`
	DeviceType       string `json:"deviceType"`
	UniqueID         string `json:"uniqueId"`
	IPAddress        string `json:"ipAddress,omitempty"`
	FirmwareVersion  string `json:"firmwareVersion,omitempty"`
	LastCheckedIn    string `json:"lastCheckedIn,omitempty"`
	BuildNumber      int    `json:"buildNumber,omitempty"`
	WiFiStrength     int    `json:"wifiStrength,omitempty"`
	LEDMode          int    `json:"ledMode,omitempty"`
}

// Execution contains the current execution/sync state
type Execution struct {
	SyncActive   bool   `json:"syncActive"`
	HDMISource   string `json:"hdmiSource"`
	HDMIActive   bool   `json:"hdmiActive,omitempty"`
	Mode         string `json:"mode"`
	LastSyncMode string `json:"lastSyncMode,omitempty"`
	Brightness   int    `json:"brightness"`
	HueTarget    string `json:"hueTarget,omitempty"` // Currently selected entertainment area
	Intensity    string `json:"intensity,omitempty"`
}

// HueState contains Hue bridge connection information
type HueState struct {
	ConnectionState string           `json:"connectionState"`
	BridgeUniqueID  string           `json:"bridgeUniqueId,omitempty"`
	BridgeIP        string           `json:"bridgeIpAddress,omitempty"`
	GroupID         string           `json:"groupId"`
	Groups          map[string]Group `json:"groups,omitempty"`
}

// Group represents an entertainment area available to the Sync Box
type Group struct {
	Name      string `json:"name"`
	NumLights int    `json:"numLights"`
	Active    bool   `json:"active,omitempty"`
}

// HDMIInput represents a single HDMI input on the Sync Box
type HDMIInput struct {
	Name   string `json:"name"`
	Type   string `json:"type"`
	Status string `json:"status"`
}

// HDMIState contains all HDMI input information
type HDMIState struct {
	Input1          *HDMIInput `json:"input1,omitempty"`
	Input2          *HDMIInput `json:"input2,omitempty"`
	Input3          *HDMIInput `json:"input3,omitempty"`
	Input4          *HDMIInput `json:"input4,omitempty"`
	ContentSpecs    string     `json:"contentSpecs,omitempty"`
	VideoSyncActive bool       `json:"videoSyncActive,omitempty"`
	AudioSyncActive bool       `json:"audioSyncActive,omitempty"`
}

// Status combines device info, execution state, hue state, and hdmi state
type Status struct {
	Device    *DeviceInfo `json:"device,omitempty"`
	Execution *Execution  `json:"execution,omitempty"`
	Hue       *HueState   `json:"hue,omitempty"`
	HDMI      *HDMIState  `json:"hdmi,omitempty"`
}

// RegistrationRequest is sent to register a new app with the Sync Box
type RegistrationRequest struct {
	AppName      string `json:"appName"`
	InstanceName string `json:"instanceName"`
}

// RegistrationResponse is returned after successful registration
type RegistrationResponse struct {
	RegistrationID string `json:"registrationId"`
	AccessToken    string `json:"accessToken"`
}

// NewClient creates a new Sync Box client
func NewClient(ip, accessToken, name string) *Client {
	// Sync Box uses self-signed certs
	tr := &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}
	return &Client{
		ip:          ip,
		accessToken: accessToken,
		name:        name,
		httpClient: &http.Client{
			Timeout:   10 * time.Second,
			Transport: tr,
		},
	}
}

// GetIP returns the Sync Box IP address
func (c *Client) GetIP() string {
	return c.ip
}

// GetName returns the configured name for this Sync Box
func (c *Client) GetName() string {
	return c.name
}

func (c *Client) apiURL(path string) string {
	return fmt.Sprintf("https://%s/api/v1%s", c.ip, path)
}

func (c *Client) doRequest(method, path string, body interface{}) ([]byte, error) {
	var reqBody io.Reader
	if body != nil {
		jsonData, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("failed to marshal request: %w", err)
		}
		reqBody = bytes.NewBuffer(jsonData)
	}

	req, err := http.NewRequest(method, c.apiURL(path), reqBody)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if c.accessToken != "" {
		req.Header.Set("Authorization", "Bearer "+c.accessToken)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("syncbox API request failed: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("syncbox API error (status %d): %s", resp.StatusCode, string(respBody))
	}

	return respBody, nil
}

// Register registers a new application with the Sync Box
// The user must press the button on the Sync Box before calling this
func (c *Client) Register(appName, instanceName string) (*RegistrationResponse, error) {
	body, err := c.doRequest("POST", "/registrations", RegistrationRequest{
		AppName:      appName,
		InstanceName: instanceName,
	})
	if err != nil {
		return nil, err
	}

	var reg RegistrationResponse
	if err := json.Unmarshal(body, &reg); err != nil {
		return nil, fmt.Errorf("failed to parse registration response: %w", err)
	}

	// Update client with new token
	c.accessToken = reg.AccessToken
	return &reg, nil
}

// GetDevice returns device information
func (c *Client) GetDevice() (*DeviceInfo, error) {
	body, err := c.doRequest("GET", "/device", nil)
	if err != nil {
		return nil, err
	}

	var device DeviceInfo
	if err := json.Unmarshal(body, &device); err != nil {
		return nil, fmt.Errorf("failed to parse device info: %w", err)
	}
	device.IPAddress = c.ip

	return &device, nil
}

// GetExecution returns the current execution/sync state
func (c *Client) GetExecution() (*Execution, error) {
	body, err := c.doRequest("GET", "/execution", nil)
	if err != nil {
		return nil, err
	}

	var exec Execution
	if err := json.Unmarshal(body, &exec); err != nil {
		return nil, fmt.Errorf("failed to parse execution state: %w", err)
	}

	return &exec, nil
}

// GetHue returns Hue bridge connection information
func (c *Client) GetHue() (*HueState, error) {
	body, err := c.doRequest("GET", "/hue", nil)
	if err != nil {
		return nil, err
	}

	var hue HueState
	if err := json.Unmarshal(body, &hue); err != nil {
		return nil, fmt.Errorf("failed to parse hue state: %w", err)
	}

	return &hue, nil
}

// GetHDMI returns HDMI input information
func (c *Client) GetHDMI() (*HDMIState, error) {
	body, err := c.doRequest("GET", "/hdmi", nil)
	if err != nil {
		return nil, err
	}

	var hdmi HDMIState
	if err := json.Unmarshal(body, &hdmi); err != nil {
		return nil, fmt.Errorf("failed to parse hdmi state: %w", err)
	}

	return &hdmi, nil
}

// GetStatus returns combined device, execution, hue, and hdmi state
func (c *Client) GetStatus() (*Status, error) {
	status := &Status{}

	// Get device info (ignore error - may not have access to all endpoints)
	device, _ := c.GetDevice()
	status.Device = device

	// Get execution state
	exec, err := c.GetExecution()
	if err != nil {
		return nil, fmt.Errorf("failed to get execution state: %w", err)
	}
	status.Execution = exec

	// Get hue state
	hue, err := c.GetHue()
	if err != nil {
		return nil, fmt.Errorf("failed to get hue state: %w", err)
	}
	status.Hue = hue

	// Get HDMI state (ignore error - optional)
	hdmi, _ := c.GetHDMI()
	status.HDMI = hdmi

	return status, nil
}

// SetSyncActive starts or stops sync
func (c *Client) SetSyncActive(active bool) error {
	_, err := c.doRequest("PUT", "/execution", map[string]interface{}{
		"syncActive": active,
	})
	return err
}

// SetMode sets the sync mode (video, music, game, passthrough, powersave)
func (c *Client) SetMode(mode string) error {
	_, err := c.doRequest("PUT", "/execution", map[string]interface{}{
		"mode": mode,
	})
	return err
}

// SetHDMISource selects the HDMI input (input1, input2, input3, input4)
func (c *Client) SetHDMISource(source string) error {
	_, err := c.doRequest("PUT", "/execution", map[string]interface{}{
		"hdmiSource": source,
	})
	return err
}

// SetBrightness sets the brightness (0-200)
func (c *Client) SetBrightness(brightness int) error {
	_, err := c.doRequest("PUT", "/execution", map[string]interface{}{
		"brightness": brightness,
	})
	return err
}

// SetIntensity sets the intensity (subtle, moderate, high, intense)
func (c *Client) SetIntensity(intensity string) error {
	_, err := c.doRequest("PUT", "/execution", map[string]interface{}{
		"intensity": intensity,
	})
	return err
}

// SetEntertainmentArea changes which entertainment area the Sync Box uses
func (c *Client) SetEntertainmentArea(groupID string) error {
	_, err := c.doRequest("PUT", "/execution", map[string]interface{}{
		"hueTarget": groupID,
	})
	return err
}
