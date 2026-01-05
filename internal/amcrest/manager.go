package amcrest

import (
	"fmt"
	"log"
	"net/url"
	"strings"
	"sync"
	"time"
)

// DoorbellHandler is called when a doorbell button is pressed
type DoorbellHandler func(cameraName string)

// Manager handles multiple Amcrest cameras
type Manager struct {
	clients         map[string]*Client
	mu              sync.RWMutex
	doorbellHandler DoorbellHandler
	stopMonitoring  chan struct{}
}

// NewManager creates a new Amcrest camera manager
func NewManager() *Manager {
	return &Manager{
		clients: make(map[string]*Client),
	}
}

// AddCamera adds a camera from an RTSP URL
// Format: rtsp://user:pass@host:port/path
func (m *Manager) AddCamera(name, rtspURL string) error {
	if rtspURL == "" {
		log.Printf("Amcrest: Skipping %s (no RTSP URL)", name)
		return nil
	}

	host, username, password, err := parseRTSPURL(rtspURL)
	if err != nil {
		return fmt.Errorf("failed to parse RTSP URL for %s: %w", name, err)
	}

	client := NewClient(host, username, password)

	// Test connectivity
	deviceType, err := client.GetDeviceType()
	if err != nil {
		log.Printf("Amcrest: Camera %s at %s - HTTP API not available: %v", name, host, err)
		// Still add the client - it may become available later or be partially functional
		m.mu.Lock()
		m.clients[name] = client
		m.mu.Unlock()
		return nil
	}

	log.Printf("Amcrest: Added %s at %s (model: %s)", name, host, deviceType)

	m.mu.Lock()
	m.clients[name] = client
	m.mu.Unlock()

	return nil
}

// AddCameraWithCredentials adds a camera with explicit credentials
func (m *Manager) AddCameraWithCredentials(name, host, username, password string) error {
	client := NewClient(host, username, password)

	// Test connectivity
	deviceType, err := client.GetDeviceType()
	if err != nil {
		log.Printf("Amcrest: Camera %s at %s - HTTP API not available: %v", name, host, err)
		m.mu.Lock()
		m.clients[name] = client
		m.mu.Unlock()
		return nil
	}

	log.Printf("Amcrest: Added %s at %s (model: %s)", name, host, deviceType)

	m.mu.Lock()
	m.clients[name] = client
	m.mu.Unlock()

	return nil
}

// GetClient returns the client for a camera
func (m *Manager) GetClient(name string) *Client {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.clients[name]
}

// GetAllClients returns all camera clients
func (m *Manager) GetAllClients() map[string]*Client {
	m.mu.RLock()
	defer m.mu.RUnlock()

	result := make(map[string]*Client, len(m.clients))
	for k, v := range m.clients {
		result[k] = v
	}
	return result
}

// GetCameraNames returns all camera names
func (m *Manager) GetCameraNames() []string {
	m.mu.RLock()
	defer m.mu.RUnlock()

	names := make([]string, 0, len(m.clients))
	for name := range m.clients {
		names = append(names, name)
	}
	return names
}

// CameraStatus represents the status of a camera
type CameraStatus struct {
	Name        string      `json:"name"`
	Host        string      `json:"host"`
	Available   bool        `json:"available"`
	DeviceInfo  *DeviceInfo `json:"deviceInfo,omitempty"`
	Error       string      `json:"error,omitempty"`
}

// GetAllStatus returns status for all cameras
func (m *Manager) GetAllStatus() []CameraStatus {
	m.mu.RLock()
	defer m.mu.RUnlock()

	statuses := make([]CameraStatus, 0, len(m.clients))
	for name, client := range m.clients {
		status := CameraStatus{
			Name: name,
			Host: client.host,
		}

		info, err := client.GetDeviceInfo()
		if err != nil {
			status.Available = false
			status.Error = err.Error()
		} else {
			status.Available = true
			status.DeviceInfo = info
		}

		statuses = append(statuses, status)
	}

	return statuses
}

// parseRTSPURL extracts host and credentials from an RTSP URL
func parseRTSPURL(rtspURL string) (host, username, password string, err error) {
	// Parse the URL
	u, err := url.Parse(rtspURL)
	if err != nil {
		return "", "", "", err
	}

	// Extract credentials
	if u.User != nil {
		username = u.User.Username()
		password, _ = u.User.Password()
	}

	// Extract host (without port for HTTP access)
	host = u.Hostname()
	if host == "" {
		return "", "", "", fmt.Errorf("no host in URL")
	}

	return host, username, password, nil
}

// ParseCamerasEnv parses the CAMERAS environment variable format
// Format: name:rtsp://url,name2:rtsp://url2
// Or simple: name,name2,name3 (with separate RTSP URLs)
func ParseCamerasEnv(camerasEnv string, rtspURLs map[string]string) map[string]string {
	result := make(map[string]string)

	if camerasEnv == "" {
		return result
	}

	// Check if it's the simple format (just names)
	if !strings.Contains(camerasEnv, "rtsp://") {
		names := strings.Split(camerasEnv, ",")
		for _, name := range names {
			name = strings.TrimSpace(name)
			if name == "" {
				continue
			}
			// Look up RTSP URL from the provided map
			if rtspURL, ok := rtspURLs[name]; ok {
				result[name] = rtspURL
			} else {
				result[name] = "" // No RTSP URL available
			}
		}
		return result
	}

	// Parse name:rtsp://url format
	parts := strings.Split(camerasEnv, ",")
	for _, part := range parts {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}

		// Split on first colon that's not part of rtsp://
		idx := strings.Index(part, ":rtsp://")
		if idx == -1 {
			// Try splitting on first colon
			colonIdx := strings.Index(part, ":")
			if colonIdx == -1 {
				result[part] = ""
				continue
			}
			name := part[:colonIdx]
			rtspURL := part[colonIdx+1:]
			result[name] = rtspURL
		} else {
			name := part[:idx]
			rtspURL := part[idx+1:]
			result[name] = rtspURL
		}
	}

	return result
}

// SetDoorbellHandler sets the callback for doorbell button press events
func (m *Manager) SetDoorbellHandler(handler DoorbellHandler) {
	m.doorbellHandler = handler
}

// StartDoorbellMonitoring starts monitoring all doorbells for button presses
func (m *Manager) StartDoorbellMonitoring() {
	m.stopMonitoring = make(chan struct{})

	m.mu.RLock()
	defer m.mu.RUnlock()

	for name, client := range m.clients {
		if client.IsDoorbell() {
			go m.monitorDoorbell(name, client)
		}
	}
}

// StopDoorbellMonitoring stops all doorbell monitoring
func (m *Manager) StopDoorbellMonitoring() {
	if m.stopMonitoring != nil {
		close(m.stopMonitoring)
	}
}

// monitorDoorbell monitors a single doorbell for button press events
func (m *Manager) monitorDoorbell(name string, client *Client) {
	log.Printf("Amcrest: Starting doorbell monitoring for %s", name)

	// Retry loop - reconnects on failure
	for {
		select {
		case <-m.stopMonitoring:
			log.Printf("Amcrest: Stopping doorbell monitoring for %s", name)
			return
		default:
		}

		err := client.SubscribeEvents([]EventType{EventDoorbell}, func(event Event) {
			// Only trigger on "Start" action (button press, not release)
			if event.Action == "Start" {
				log.Printf("Amcrest: Doorbell button pressed on %s (event: %s)", name, event.Code)
				if m.doorbellHandler != nil {
					m.doorbellHandler(name)
				}
			}
		})

		if err != nil {
			log.Printf("Amcrest: Doorbell event stream error for %s: %v (reconnecting in 5s)", name, err)
		}

		// Wait before reconnecting
		select {
		case <-m.stopMonitoring:
			return
		case <-time.After(5 * time.Second):
		}
	}
}
