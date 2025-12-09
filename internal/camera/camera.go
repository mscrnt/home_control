package camera

import (
	"crypto/md5"
	"fmt"
	"io"
	"log"
	"net/http"
	"regexp"
	"strings"
	"time"
)

// Camera represents a camera configuration
type Camera struct {
	Name     string
	Host     string
	Username string
	Password string
}

// Manager handles camera operations
type Manager struct {
	cameras     map[string]*Camera
	httpClient  *http.Client
	frigateHost string // Optional Frigate server URL
}

// NewManager creates a new camera manager
func NewManager() *Manager {
	return &Manager{
		cameras: make(map[string]*Camera),
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// SetFrigateHost sets the Frigate server URL for camera proxying
func (m *Manager) SetFrigateHost(host string) {
	m.frigateHost = strings.TrimSuffix(host, "/")
	if m.frigateHost != "" {
		log.Printf("Frigate integration enabled: %s", m.frigateHost)
	}
}

// AddCamera adds a camera to the manager
func (m *Manager) AddCamera(name, rtspURL string) error {
	var cam *Camera

	if rtspURL == "" {
		// Frigate-only mode - no direct camera access
		cam = &Camera{Name: name}
		log.Printf("Added camera: %s (Frigate-only)", name)
	} else {
		// Parse RTSP URL to extract host and credentials
		// Format: rtsp://user:pass@host:port/path
		var err error
		cam, err = parseRTSPURL(rtspURL)
		if err != nil {
			return err
		}
		cam.Name = name
		log.Printf("Added camera: %s at %s", name, cam.Host)
	}

	m.cameras[name] = cam
	return nil
}

// parseRTSPURL extracts credentials and host from RTSP URL
func parseRTSPURL(rtspURL string) (*Camera, error) {
	// Remove rtsp:// prefix
	url := strings.TrimPrefix(rtspURL, "rtsp://")

	// Split by @ to get credentials and host
	parts := strings.SplitN(url, "@", 2)
	if len(parts) != 2 {
		return nil, fmt.Errorf("invalid RTSP URL format")
	}

	// Parse credentials (user:pass)
	credParts := strings.SplitN(parts[0], ":", 2)
	if len(credParts) != 2 {
		return nil, fmt.Errorf("invalid credentials in RTSP URL")
	}

	// Parse host (may include port and path)
	hostParts := strings.SplitN(parts[1], ":", 2)
	host := hostParts[0]
	if len(hostParts) > 1 {
		// Remove port and path, just keep host
		portPath := hostParts[1]
		if idx := strings.Index(portPath, "/"); idx != -1 {
			// Has path, just use the host
		}
	}

	return &Camera{
		Host:     host,
		Username: credParts[0],
		Password: credParts[1],
	}, nil
}

// GetCamera returns a camera by name
func (m *Manager) GetCamera(name string) *Camera {
	return m.cameras[name]
}

// GetCameras returns all cameras
func (m *Manager) GetCameras() map[string]*Camera {
	return m.cameras
}

// GetSnapshotURL returns the HTTP snapshot URL for a camera (without auth in URL)
func (c *Camera) GetSnapshotURL() string {
	return fmt.Sprintf("http://%s/cgi-bin/snapshot.cgi", c.Host)
}

// GetMJPEGURL returns the MJPEG stream URL for a camera (substream for lower bandwidth)
func (c *Camera) GetMJPEGURL() string {
	return fmt.Sprintf("http://%s/cgi-bin/mjpg/video.cgi?channel=1&subtype=1", c.Host)
}

// doDigestRequest performs an HTTP request with digest authentication
func (m *Manager) doDigestRequest(cam *Camera, url string) (*http.Response, error) {
	// First request to get the WWW-Authenticate challenge
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}

	resp, err := m.httpClient.Do(req)
	if err != nil {
		return nil, err
	}

	// If not 401, return the response (no auth needed or different error)
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

	// Calculate digest response
	ha1 := md5sum(fmt.Sprintf("%s:%s:%s", cam.Username, realm, cam.Password))
	ha2 := md5sum(fmt.Sprintf("GET:%s", req.URL.RequestURI()))

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
		cam.Username, realm, nonce, req.URL.RequestURI(), response)
	if qop != "" {
		authValue += fmt.Sprintf(`, qop=%s, nc=%s, cnonce="%s"`, qop, nc, cnonce)
	}

	// Make the authenticated request
	req2, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}
	req2.Header.Set("Authorization", authValue)

	return m.httpClient.Do(req2)
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

// ProxySnapshot proxies a camera snapshot through the server
func (m *Manager) ProxySnapshot(w http.ResponseWriter, r *http.Request, cameraName string) {
	cam := m.cameras[cameraName]
	if cam == nil {
		http.Error(w, "Camera not found", http.StatusNotFound)
		return
	}

	// Try Frigate first if configured
	if m.frigateHost != "" {
		if m.proxyFrigateSnapshot(w, r, cameraName) {
			return
		}
		log.Printf("Frigate snapshot failed for %s", cameraName)
	}

	// Fall back to direct camera access (only if we have host info)
	if cam.Host == "" {
		http.Error(w, "Camera not available (Frigate required)", http.StatusBadGateway)
		return
	}

	resp, err := m.doDigestRequest(cam, cam.GetSnapshotURL())
	if err != nil {
		log.Printf("Failed to get snapshot from %s: %v", cameraName, err)
		http.Error(w, "Failed to get snapshot", http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		log.Printf("Camera %s returned status %d", cameraName, resp.StatusCode)
		http.Error(w, "Camera error", resp.StatusCode)
		return
	}

	// Copy headers
	for k, v := range resp.Header {
		if k == "Content-Type" || k == "Content-Length" {
			w.Header()[k] = v
		}
	}

	w.WriteHeader(resp.StatusCode)
	io.Copy(w, resp.Body)
}

// proxyFrigateSnapshot proxies a snapshot from Frigate - returns true if successful
func (m *Manager) proxyFrigateSnapshot(w http.ResponseWriter, r *http.Request, cameraName string) bool {
	frigateURL := fmt.Sprintf("%s/api/%s/latest.jpg", m.frigateHost, cameraName)

	resp, err := m.httpClient.Get(frigateURL)
	if err != nil {
		log.Printf("Frigate snapshot request failed for %s: %v", cameraName, err)
		return false
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		log.Printf("Frigate returned status %d for camera %s snapshot", resp.StatusCode, cameraName)
		return false
	}

	// Copy headers
	for k, v := range resp.Header {
		if k == "Content-Type" || k == "Content-Length" {
			w.Header()[k] = v
		}
	}

	w.WriteHeader(http.StatusOK)
	io.Copy(w, resp.Body)
	return true
}

// ProxyMJPEG proxies an MJPEG stream through the server
func (m *Manager) ProxyMJPEG(w http.ResponseWriter, r *http.Request, cameraName string) {
	cam := m.cameras[cameraName]
	if cam == nil {
		http.Error(w, "Camera not found", http.StatusNotFound)
		return
	}

	// Try Frigate/go2rtc first if configured
	if m.frigateHost != "" {
		if m.proxyFrigateMJPEG(w, r, cameraName) {
			return
		}
		log.Printf("Frigate MJPEG failed for %s", cameraName)
	}

	// Fall back to direct camera access (only if we have host info)
	if cam.Host == "" {
		http.Error(w, "Camera not available (Frigate required)", http.StatusBadGateway)
		return
	}

	// Use a client with no timeout for streaming
	streamClient := &http.Client{Timeout: 0}
	streamManager := &Manager{
		cameras:    m.cameras,
		httpClient: streamClient,
	}

	resp, err := streamManager.doDigestRequest(cam, cam.GetMJPEGURL())
	if err != nil {
		log.Printf("Failed to connect to camera stream %s: %v", cameraName, err)
		http.Error(w, "Failed to connect to camera", http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		log.Printf("Camera stream %s returned status %d", cameraName, resp.StatusCode)
		http.Error(w, "Camera error", resp.StatusCode)
		return
	}

	m.streamResponse(w, r, resp)
}

// proxyFrigateMJPEG proxies an MJPEG stream from Frigate - returns true if successful
func (m *Manager) proxyFrigateMJPEG(w http.ResponseWriter, r *http.Request, cameraName string) bool {
	// Frigate MJPEG endpoint: /api/<camera_name>?fps=5&height=720
	frigateURL := fmt.Sprintf("%s/api/%s?fps=5&height=720", m.frigateHost, cameraName)
	return m.tryFrigateStream(w, r, cameraName, frigateURL)
}

// tryFrigateStream attempts to stream from a single Frigate URL
func (m *Manager) tryFrigateStream(w http.ResponseWriter, r *http.Request, cameraName, frigateURL string) bool {
	log.Printf("Trying Frigate stream URL: %s", frigateURL)

	// Create a client with no timeout for streaming
	streamClient := &http.Client{Timeout: 0}

	req, err := http.NewRequestWithContext(r.Context(), "GET", frigateURL, nil)
	if err != nil {
		log.Printf("Failed to create Frigate stream request for %s: %v", cameraName, err)
		return false
	}

	resp, err := streamClient.Do(req)
	if err != nil {
		log.Printf("Frigate stream request failed for %s: %v", cameraName, err)
		return false
	}

	if resp.StatusCode != http.StatusOK {
		resp.Body.Close()
		log.Printf("Frigate returned status %d for camera %s stream at %s", resp.StatusCode, cameraName, frigateURL)
		return false
	}

	log.Printf("Frigate stream connected for %s: content-type=%s", cameraName, resp.Header.Get("Content-Type"))

	// Don't defer close - streamResponse handles it
	m.streamResponse(w, r, resp)
	resp.Body.Close()
	return true
}

// streamResponse streams an HTTP response to the client
func (m *Manager) streamResponse(w http.ResponseWriter, r *http.Request, resp *http.Response) {
	// Set headers for MJPEG streaming
	contentType := resp.Header.Get("Content-Type")
	if contentType == "" {
		contentType = "multipart/x-mixed-replace; boundary=myboundary"
	}
	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")

	// Stream the response
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "Streaming not supported", http.StatusInternalServerError)
		return
	}

	buf := make([]byte, 32*1024)
	for {
		select {
		case <-r.Context().Done():
			return
		default:
			n, err := resp.Body.Read(buf)
			if n > 0 {
				w.Write(buf[:n])
				flusher.Flush()
			}
			if err != nil {
				return
			}
		}
	}
}
