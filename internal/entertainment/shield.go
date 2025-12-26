package entertainment

import (
	"bufio"
	"fmt"
	"log"
	"net"
	"os/exec"
	"strings"
	"sync"
	"time"
)

// ShieldDevice represents an Nvidia Shield TV
type ShieldDevice struct {
	Name    string
	Host    string
	Port    int // Default 5555 for ADB
	mu      sync.Mutex
	conn    net.Conn
}

// ShieldManager manages Nvidia Shield devices
type ShieldManager struct {
	devices map[string]*ShieldDevice
}

// Common key event codes for Android
const (
	KeyHome       = 3
	KeyBack       = 4
	KeyDpadUp     = 19
	KeyDpadDown   = 20
	KeyDpadLeft   = 21
	KeyDpadRight  = 22
	KeyDpadCenter = 23
	KeyVolumeUp   = 24
	KeyVolumeDown = 25
	KeyPower      = 26
	KeyMenu       = 82
	KeyPlay       = 126
	KeyPause      = 127
	KeyStop       = 86
	KeyNext       = 87
	KeyPrevious   = 88
	KeyRewind     = 89
	KeyFastForward = 90
	KeyMute       = 164
	KeySleep      = 223
	KeyWakeUp     = 224
)

// Common app package names
var ShieldApps = map[string]string{
	"netflix":     "com.netflix.ninja",
	"youtube":     "com.google.android.youtube.tv",
	"plex":        "com.plexapp.android",
	"prime":       "com.amazon.amazonvideo.livingroom",
	"disney":      "com.disney.disneyplus",
	"hulu":        "com.hulu.livingroomplus",
	"hbo":         "com.hbo.hbomax",
	"spotify":     "com.spotify.tv.android",
	"kodi":        "org.xbmc.kodi",
	"settings":    "com.android.tv.settings",
	"gamestream":  "com.nvidia.tegrazone3",
}

// NewShieldManager creates a new Shield manager
func NewShieldManager() *ShieldManager {
	return &ShieldManager{
		devices: make(map[string]*ShieldDevice),
	}
}

// AddDevice adds a Shield device
func (m *ShieldManager) AddDevice(name, host string, port int) {
	if port <= 0 {
		port = 5555
	}
	m.devices[name] = &ShieldDevice{
		Name: name,
		Host: host,
		Port: port,
	}
	log.Printf("Added Nvidia Shield: %s (%s:%d)", name, host, port)
}

// GetDevice returns a device by name
func (m *ShieldManager) GetDevice(name string) *ShieldDevice {
	return m.devices[name]
}

// GetDevices returns all devices
func (m *ShieldManager) GetDevices() map[string]*ShieldDevice {
	return m.devices
}

// connect establishes ADB connection to the device
func (d *ShieldDevice) connect() error {
	d.mu.Lock()
	defer d.mu.Unlock()

	if d.conn != nil {
		return nil
	}

	addr := fmt.Sprintf("%s:%d", d.Host, d.Port)
	conn, err := net.DialTimeout("tcp", addr, 5*time.Second)
	if err != nil {
		return fmt.Errorf("failed to connect to Shield: %w", err)
	}

	d.conn = conn
	return nil
}

// disconnect closes the ADB connection
func (d *ShieldDevice) disconnect() {
	d.mu.Lock()
	defer d.mu.Unlock()

	if d.conn != nil {
		d.conn.Close()
		d.conn = nil
	}
}

// sendADBCommand sends a shell command via ADB protocol
// Note: This is a simplified implementation. For full ADB protocol,
// consider using an ADB library or calling adb binary
func (d *ShieldDevice) sendADBCommand(cmd string) (string, error) {
	addr := fmt.Sprintf("%s:%d", d.Host, d.Port)

	conn, err := net.DialTimeout("tcp", addr, 5*time.Second)
	if err != nil {
		return "", fmt.Errorf("failed to connect: %w", err)
	}
	defer conn.Close()

	// Set read deadline
	conn.SetDeadline(time.Now().Add(10 * time.Second))

	// ADB protocol: send CNXN, receive CNXN, send OPEN shell:cmd, read response
	// This is complex - for simplicity, we'll use a helper approach

	// For now, return an indicator that we need to use adb binary
	return "", fmt.Errorf("direct ADB protocol not implemented - use adb binary")
}

// execADB executes an ADB command using the adb binary
// This requires adb to be installed and the device to be connected
func (d *ShieldDevice) execADB(args ...string) error {
	// First ensure we're connected to the device
	addr := fmt.Sprintf("%s:%d", d.Host, d.Port)

	// Try to connect first (adb connect is idempotent)
	connectCmd := exec.Command("adb", "connect", addr)
	connectOut, _ := connectCmd.CombinedOutput()
	log.Printf("ADB connect %s: %s", addr, strings.TrimSpace(string(connectOut)))

	// Build full command with host
	fullArgs := append([]string{"-s", addr}, args...)
	log.Printf("ADB command for %s: adb %s", d.Name, strings.Join(fullArgs, " "))

	cmd := exec.Command("adb", fullArgs...)
	output, err := cmd.CombinedOutput()
	if err != nil {
		log.Printf("ADB command failed: %v, output: %s", err, string(output))
		return fmt.Errorf("adb command failed: %w", err)
	}

	if len(output) > 0 {
		log.Printf("ADB output: %s", strings.TrimSpace(string(output)))
	}
	return nil
}

// SendKeyEvent sends a key event to the Shield
func (d *ShieldDevice) SendKeyEvent(keyCode int) error {
	cmd := fmt.Sprintf("input keyevent %d", keyCode)
	return d.Shell(cmd)
}

// Shell executes a shell command on the Shield
func (d *ShieldDevice) Shell(cmd string) error {
	log.Printf("Shield %s shell: %s", d.Name, cmd)
	return d.execADB("shell", cmd)
}

// ========== Power Control ==========

// WakeUp wakes the Shield from sleep
func (d *ShieldDevice) WakeUp() error {
	return d.SendKeyEvent(KeyWakeUp)
}

// Sleep puts the Shield to sleep
func (d *ShieldDevice) Sleep() error {
	return d.SendKeyEvent(KeySleep)
}

// ========== Navigation ==========

// Home goes to the home screen
func (d *ShieldDevice) Home() error {
	return d.SendKeyEvent(KeyHome)
}

// Back goes back
func (d *ShieldDevice) Back() error {
	return d.SendKeyEvent(KeyBack)
}

// Up navigates up
func (d *ShieldDevice) Up() error {
	return d.SendKeyEvent(KeyDpadUp)
}

// Down navigates down
func (d *ShieldDevice) Down() error {
	return d.SendKeyEvent(KeyDpadDown)
}

// Left navigates left
func (d *ShieldDevice) Left() error {
	return d.SendKeyEvent(KeyDpadLeft)
}

// Right navigates right
func (d *ShieldDevice) Right() error {
	return d.SendKeyEvent(KeyDpadRight)
}

// Select/Enter
func (d *ShieldDevice) Select() error {
	return d.SendKeyEvent(KeyDpadCenter)
}

// Menu opens the menu
func (d *ShieldDevice) Menu() error {
	return d.SendKeyEvent(KeyMenu)
}

// ========== Media Control ==========

// Play plays media
func (d *ShieldDevice) Play() error {
	return d.SendKeyEvent(KeyPlay)
}

// Pause pauses media
func (d *ShieldDevice) Pause() error {
	return d.SendKeyEvent(KeyPause)
}

// PlayPause toggles play/pause
func (d *ShieldDevice) PlayPause() error {
	return d.SendKeyEvent(85) // KEYCODE_MEDIA_PLAY_PAUSE
}

// Stop stops media
func (d *ShieldDevice) Stop() error {
	return d.SendKeyEvent(KeyStop)
}

// Next skips to next
func (d *ShieldDevice) Next() error {
	return d.SendKeyEvent(KeyNext)
}

// Previous goes to previous
func (d *ShieldDevice) Previous() error {
	return d.SendKeyEvent(KeyPrevious)
}

// Rewind rewinds
func (d *ShieldDevice) Rewind() error {
	return d.SendKeyEvent(KeyRewind)
}

// FastForward fast forwards
func (d *ShieldDevice) FastForward() error {
	return d.SendKeyEvent(KeyFastForward)
}

// ========== Volume Control ==========

// VolumeUp increases volume
func (d *ShieldDevice) VolumeUp() error {
	return d.SendKeyEvent(KeyVolumeUp)
}

// VolumeDown decreases volume
func (d *ShieldDevice) VolumeDown() error {
	return d.SendKeyEvent(KeyVolumeDown)
}

// Mute toggles mute
func (d *ShieldDevice) Mute() error {
	return d.SendKeyEvent(KeyMute)
}

// ========== App Control ==========

// LaunchApp launches an app by package name
func (d *ShieldDevice) LaunchApp(packageName string) error {
	// Look up common app names
	if pkg, ok := ShieldApps[strings.ToLower(packageName)]; ok {
		packageName = pkg
	}

	cmd := fmt.Sprintf("monkey -p %s -c android.intent.category.LAUNCHER 1", packageName)
	return d.Shell(cmd)
}

// ForceStopApp force stops an app
func (d *ShieldDevice) ForceStopApp(packageName string) error {
	if pkg, ok := ShieldApps[strings.ToLower(packageName)]; ok {
		packageName = pkg
	}

	cmd := fmt.Sprintf("am force-stop %s", packageName)
	return d.Shell(cmd)
}

// GetCurrentApp returns the currently focused app
func (d *ShieldDevice) GetCurrentApp() (string, error) {
	// This would need to parse output of: dumpsys window | grep mCurrentFocus
	return "", fmt.Errorf("not implemented - requires adb shell output parsing")
}

// ========== Device State ==========

// ShieldState represents the state of a Shield device
type ShieldState struct {
	Name    string `json:"name"`
	Host    string `json:"host"`
	Online  bool   `json:"online"`
	Error   string `json:"error,omitempty"`
}

// GetState returns the current state
func (d *ShieldDevice) GetState() *ShieldState {
	state := &ShieldState{
		Name: d.Name,
		Host: d.Host,
	}

	// Try to connect to check if online
	addr := fmt.Sprintf("%s:%d", d.Host, d.Port)
	conn, err := net.DialTimeout("tcp", addr, 2*time.Second)
	if err != nil {
		state.Online = false
		state.Error = "Device not reachable"
	} else {
		state.Online = true
		conn.Close()
	}

	return state
}

// GetAllStates returns states of all devices
func (m *ShieldManager) GetAllStates() []*ShieldState {
	states := make([]*ShieldState, 0, len(m.devices))
	for _, device := range m.devices {
		states = append(states, device.GetState())
	}
	return states
}

// ADB-based implementation using TCP
// This is a more complete ADB wire protocol implementation

const (
	adbCNXN = 0x4e584e43
	adbOPEN = 0x4e45504f
	adbOKAY = 0x59414b4f
	adbCLSE = 0x45534c43
	adbWRTE = 0x45545257
)

type adbMessage struct {
	command     uint32
	arg0        uint32
	arg1        uint32
	dataLength  uint32
	dataCRC     uint32
	magic       uint32
	data        []byte
}

func (d *ShieldDevice) adbConnect() (net.Conn, error) {
	addr := fmt.Sprintf("%s:%d", d.Host, d.Port)
	conn, err := net.DialTimeout("tcp", addr, 5*time.Second)
	if err != nil {
		return nil, err
	}

	return conn, nil
}

// IsReachable checks if the Shield is reachable
func (d *ShieldDevice) IsReachable() bool {
	addr := fmt.Sprintf("%s:%d", d.Host, d.Port)
	conn, err := net.DialTimeout("tcp", addr, 2*time.Second)
	if err != nil {
		return false
	}
	conn.Close()
	return true
}

// ReadLine reads a line from connection (for simple protocol testing)
func readLine(conn net.Conn) (string, error) {
	reader := bufio.NewReader(conn)
	line, err := reader.ReadString('\n')
	if err != nil {
		return "", err
	}
	return strings.TrimSpace(line), nil
}
