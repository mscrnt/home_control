// Package adb provides an ADB client for controlling Android tablets
package adb

import (
	"bytes"
	"context"
	"fmt"
	"net"
	"os/exec"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Client represents an ADB connection to a device
type Client struct {
	deviceAddr   string
	mu           sync.Mutex
	connected    bool
	connMu       sync.RWMutex
	onReconnect  func()
}

// DeviceStatus contains information about the tablet
type DeviceStatus struct {
	Connected       bool    `json:"connected"`
	ScreenOn        bool    `json:"screenOn"`
	BatteryLevel    int     `json:"batteryLevel"`
	BatteryCharging bool    `json:"batteryCharging"`
	Brightness      int     `json:"brightness"`
	ScreenTimeout   int     `json:"screenTimeout"` // in seconds
	LightLevel      float64 `json:"lightLevel"`
}

// NewClient creates a new ADB client for the given device
func NewClient(deviceAddr string) *Client {
	return &Client{
		deviceAddr: deviceAddr,
	}
}

// SetAddress updates the device address and reconnects
func (c *Client) SetAddress(ctx context.Context, addr string) error {
	c.mu.Lock()
	oldAddr := c.deviceAddr
	c.deviceAddr = addr
	c.mu.Unlock()

	c.connMu.Lock()
	c.connected = false
	c.connMu.Unlock()

	// Try to connect to new address
	if c.tryConnect(ctx) {
		return nil
	}

	// Revert if connection failed
	c.mu.Lock()
	c.deviceAddr = oldAddr
	c.mu.Unlock()
	return fmt.Errorf("failed to connect to %s", addr)
}

// GetAddress returns the current device address
func (c *Client) GetAddress() string {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.deviceAddr
}

// GetIP extracts just the IP from the device address
func (c *Client) GetIP() string {
	c.mu.Lock()
	addr := c.deviceAddr
	c.mu.Unlock()

	if idx := strings.LastIndex(addr, ":"); idx > 0 {
		return addr[:idx]
	}
	return addr
}

// ScanForPort scans the device IP for an open ADB port in the wireless debugging range
// Uses nmap for fast scanning, falls back to socket connections if nmap unavailable
func ScanForPort(ctx context.Context, ip string) (int, error) {
	fmt.Printf("ADB: Scanning %s for wireless debugging port...\n", ip)

	// Method 1: Use nmap (fast, scans 35000-50000 in ~2-3 seconds)
	// Add -Pn to skip host detection and -sT for TCP connect scan
	cmd := exec.CommandContext(ctx, "nmap", "-Pn", "-sT", "-p", "35000-50000", "--open", "-T5", ip)
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	if err := cmd.Run(); err == nil {
		// Parse nmap output for open ports
		// Format: "43313/tcp open  unknown"
		portRegex := regexp.MustCompile(`(\d{5})/tcp\s+open`)
		matches := portRegex.FindAllStringSubmatch(stdout.String(), -1)
		for _, match := range matches {
			if len(match) >= 2 {
				port, err := strconv.Atoi(match[1])
				if err == nil && port >= 35000 && port <= 50000 {
					fmt.Printf("ADB: Found port %d via nmap\n", port)
					return port, nil
				}
			}
		}
		fmt.Printf("ADB: nmap completed but found no open ports\n")
	} else {
		fmt.Printf("ADB: nmap failed (%v), falling back to socket scan\n", err)
	}

	// Method 2: Parallel socket scan (fallback if nmap unavailable)
	found := make(chan int, 1)
	done := make(chan struct{})

	// Use more goroutines for faster scanning
	numWorkers := 50
	ports := make(chan int, numWorkers*2)

	// Start workers
	var wg sync.WaitGroup
	for i := 0; i < numWorkers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for port := range ports {
				select {
				case <-done:
					return
				default:
				}

				addr := fmt.Sprintf("%s:%d", ip, port)
				conn, err := net.DialTimeout("tcp", addr, 50*time.Millisecond)
				if err == nil {
					conn.Close()
					select {
					case found <- port:
						close(done)
					default:
					}
					return
				}
			}
		}()
	}

	// Feed ports to workers
	go func() {
		for port := 35000; port <= 50000; port++ {
			select {
			case <-done:
				break
			case ports <- port:
			}
		}
		close(ports)
	}()

	// Wait for result or timeout
	go func() {
		wg.Wait()
		select {
		case found <- 0: // Signal no port found
		default:
		}
	}()

	select {
	case port := <-found:
		if port > 0 {
			fmt.Printf("ADB: Found port %d via socket scan\n", port)
			return port, nil
		}
		return 0, fmt.Errorf("no open ports found")
	case <-ctx.Done():
		close(done)
		return 0, ctx.Err()
	case <-time.After(60 * time.Second):
		close(done)
		return 0, fmt.Errorf("port scan timed out")
	}
}

// OnReconnect sets a callback that fires when connection is restored
func (c *Client) OnReconnect(fn func()) {
	c.onReconnect = fn
}

// StartConnectionMonitor starts a background goroutine that monitors
// the ADB connection and reconnects if it drops
func (c *Client) StartConnectionMonitor(ctx context.Context, checkInterval time.Duration) {
	go func() {
		ticker := time.NewTicker(checkInterval)
		defer ticker.Stop()

		failedAttempts := 0
		lastScanTime := time.Time{}

		// Initial connection attempt
		c.tryConnect(ctx)

		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				c.connMu.RLock()
				wasConnected := c.connected
				c.connMu.RUnlock()

				isConnected := c.IsConnected(ctx)

				c.connMu.Lock()
				c.connected = isConnected
				c.connMu.Unlock()

				if !isConnected {
					failedAttempts++

					// Try to reconnect with current address first
					if c.tryConnect(ctx) {
						failedAttempts = 0
						if wasConnected {
							fmt.Printf("ADB: Reconnected to %s\n", c.deviceAddr)
						} else {
							fmt.Printf("ADB: Connected to %s\n", c.deviceAddr)
						}
						if c.onReconnect != nil {
							c.onReconnect()
						}
						continue
					}

					// After 3 failed attempts, scan for a new port (but not more than once per minute)
					if failedAttempts >= 3 && time.Since(lastScanTime) > time.Minute {
						fmt.Printf("ADB: Connection failed %d times, scanning for new port...\n", failedAttempts)
						lastScanTime = time.Now()

						ip := c.GetIP()
						scanCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
						port, err := ScanForPort(scanCtx, ip)
						cancel()

						if err == nil && port > 0 {
							newAddr := fmt.Sprintf("%s:%d", ip, port)
							fmt.Printf("ADB: Found new port %d, trying to connect to %s\n", port, newAddr)

							if err := c.SetAddress(ctx, newAddr); err == nil {
								failedAttempts = 0
								fmt.Printf("ADB: Successfully connected to new address %s\n", newAddr)
								if c.onReconnect != nil {
									c.onReconnect()
								}
							}
						} else {
							fmt.Printf("ADB: Port scan failed: %v\n", err)
						}
					}
				} else {
					failedAttempts = 0
				}
			}
		}
	}()
}

// tryConnect attempts to connect to the device
func (c *Client) tryConnect(ctx context.Context) bool {
	err := c.Connect(ctx)
	if err != nil {
		return false
	}
	// Verify connection worked
	if c.IsConnected(ctx) {
		c.connMu.Lock()
		c.connected = true
		c.connMu.Unlock()
		return true
	}
	return false
}

// IsReady returns true if the connection is currently established
func (c *Client) IsReady() bool {
	c.connMu.RLock()
	defer c.connMu.RUnlock()
	return c.connected
}

// exec runs an ADB command and returns the output
func (c *Client) exec(ctx context.Context, args ...string) (string, error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	fullArgs := append([]string{"-s", c.deviceAddr}, args...)
	cmd := exec.CommandContext(ctx, "adb", fullArgs...)

	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	if err := cmd.Run(); err != nil {
		return "", fmt.Errorf("adb command failed: %v, stderr: %s", err, stderr.String())
	}

	return strings.TrimSpace(stdout.String()), nil
}

// shell runs a shell command on the device
func (c *Client) shell(ctx context.Context, cmd string) (string, error) {
	return c.exec(ctx, "shell", cmd)
}

// Connect establishes connection to the device
func (c *Client) Connect(ctx context.Context) error {
	_, err := c.exec(ctx, "connect", c.deviceAddr)
	return err
}

// IsConnected checks if the device is connected
func (c *Client) IsConnected(ctx context.Context) bool {
	out, err := c.exec(ctx, "get-state")
	if err != nil {
		return false
	}
	return strings.Contains(out, "device")
}

// GetStatus returns the current device status
func (c *Client) GetStatus(ctx context.Context) (*DeviceStatus, error) {
	status := &DeviceStatus{}

	// Check connection
	status.Connected = c.IsConnected(ctx)
	if !status.Connected {
		return status, nil
	}

	// Get screen state
	out, err := c.shell(ctx, "dumpsys power | grep mWakefulness")
	if err == nil {
		status.ScreenOn = strings.Contains(out, "Awake")
	}

	// Get battery info
	out, err = c.shell(ctx, "dumpsys battery")
	if err == nil {
		for _, line := range strings.Split(out, "\n") {
			line = strings.TrimSpace(line)
			if strings.HasPrefix(line, "level:") {
				level, _ := strconv.Atoi(strings.TrimSpace(strings.TrimPrefix(line, "level:")))
				status.BatteryLevel = level
			}
			if strings.HasPrefix(line, "status:") {
				// 2 = charging, 5 = full
				statusVal, _ := strconv.Atoi(strings.TrimSpace(strings.TrimPrefix(line, "status:")))
				status.BatteryCharging = statusVal == 2 || statusVal == 5
			}
		}
	}

	// Get brightness
	out, err = c.shell(ctx, "settings get system screen_brightness")
	if err == nil {
		brightness, _ := strconv.Atoi(strings.TrimSpace(out))
		status.Brightness = brightness
	}

	return status, nil
}

// WakeScreen turns the screen on
func (c *Client) WakeScreen(ctx context.Context) error {
	_, err := c.shell(ctx, "input keyevent KEYCODE_WAKEUP")
	return err
}

// SleepScreen turns the screen off
func (c *Client) SleepScreen(ctx context.Context) error {
	_, err := c.shell(ctx, "input keyevent KEYCODE_SLEEP")
	return err
}

// SetBrightness sets the screen brightness (0-255)
func (c *Client) SetBrightness(ctx context.Context, level int) error {
	if level < 0 {
		level = 0
	}
	if level > 255 {
		level = 255
	}
	_, err := c.shell(ctx, fmt.Sprintf("settings put system screen_brightness %d", level))
	return err
}

// SetAutoBrightness enables or disables auto brightness
func (c *Client) SetAutoBrightness(ctx context.Context, enabled bool) error {
	val := 0
	if enabled {
		val = 1
	}
	_, err := c.shell(ctx, fmt.Sprintf("settings put system screen_brightness_mode %d", val))
	return err
}

// GetScreenTimeout returns the screen timeout in seconds
func (c *Client) GetScreenTimeout(ctx context.Context) (int, error) {
	out, err := c.shell(ctx, "settings get system screen_off_timeout")
	if err != nil {
		return 0, err
	}
	ms, err := strconv.Atoi(strings.TrimSpace(out))
	if err != nil {
		return 0, err
	}
	return ms / 1000, nil
}

// SetScreenTimeout sets the screen timeout in seconds
func (c *Client) SetScreenTimeout(ctx context.Context, seconds int) error {
	ms := seconds * 1000
	_, err := c.shell(ctx, fmt.Sprintf("settings put system screen_off_timeout %d", ms))
	return err
}

// GetProximity returns the current proximity sensor value
// Returns true if something is near the sensor
func (c *Client) GetProximity(ctx context.Context) (bool, error) {
	out, err := c.shell(ctx, "dumpsys sensorservice | grep -A2 'Proximity Sensor:' | tail -1")
	if err != nil {
		return false, err
	}

	// Parse the sensor value - proximity sensor typically returns 0 (near) or 5 (far)
	// Format: " 1 (ts=...) 5.00, 0.00, 0.00,"
	parts := strings.Split(out, ")")
	if len(parts) < 2 {
		return false, fmt.Errorf("unexpected proximity output: %s", out)
	}

	valuePart := strings.TrimSpace(parts[1])
	values := strings.Split(valuePart, ",")
	if len(values) < 1 {
		return false, fmt.Errorf("no proximity value found")
	}

	val, err := strconv.ParseFloat(strings.TrimSpace(values[0]), 64)
	if err != nil {
		return false, err
	}

	// Typically 0 = near, 5 = far (varies by device)
	return val < 1.0, nil
}

// GetLightLevel returns the current ambient light level in lux
func (c *Client) GetLightLevel(ctx context.Context) (float64, error) {
	out, err := c.shell(ctx, "dumpsys sensorservice | grep -A2 'Light Sensor:' | tail -1")
	if err != nil {
		return 0, err
	}

	// Parse the sensor value
	// Format: " 1 (ts=...) 123.00, 0.00, 0.00,"
	parts := strings.Split(out, ")")
	if len(parts) < 2 {
		return 0, fmt.Errorf("unexpected light output: %s", out)
	}

	valuePart := strings.TrimSpace(parts[1])
	values := strings.Split(valuePart, ",")
	if len(values) < 1 {
		return 0, fmt.Errorf("no light value found")
	}

	return strconv.ParseFloat(strings.TrimSpace(values[0]), 64)
}

// ProximityMonitor watches the proximity sensor and triggers callbacks
type ProximityMonitor struct {
	client       *Client
	interval     time.Duration
	onApproach   func()
	onDepart     func()
	stopChan     chan struct{}
	lastNear     bool
	screenOffAt  time.Time
	idleTimeout  time.Duration
	mu           sync.Mutex
}

// NewProximityMonitor creates a proximity monitor
func NewProximityMonitor(client *Client, interval time.Duration, idleTimeout time.Duration) *ProximityMonitor {
	return &ProximityMonitor{
		client:      client,
		interval:    interval,
		idleTimeout: idleTimeout,
		stopChan:    make(chan struct{}),
	}
}

// OnApproach sets the callback for when something approaches
func (pm *ProximityMonitor) OnApproach(fn func()) {
	pm.onApproach = fn
}

// OnDepart sets the callback for when proximity clears
func (pm *ProximityMonitor) OnDepart(fn func()) {
	pm.onDepart = fn
}

// Start begins monitoring
func (pm *ProximityMonitor) Start(ctx context.Context) {
	go func() {
		ticker := time.NewTicker(pm.interval)
		defer ticker.Stop()

		for {
			select {
			case <-ctx.Done():
				return
			case <-pm.stopChan:
				return
			case <-ticker.C:
				near, err := pm.client.GetProximity(ctx)
				if err != nil {
					continue
				}

				pm.mu.Lock()
				wasNear := pm.lastNear
				pm.lastNear = near
				pm.mu.Unlock()

				if near && !wasNear && pm.onApproach != nil {
					pm.onApproach()
				} else if !near && wasNear && pm.onDepart != nil {
					pm.onDepart()
				}
			}
		}
	}()
}

// Stop stops the monitor
func (pm *ProximityMonitor) Stop() {
	close(pm.stopChan)
}

// BrightnessController manages auto-brightness based on light sensor
type BrightnessController struct {
	client     *Client
	interval   time.Duration
	minBright  int
	maxBright  int
	stopChan   chan struct{}
	enabled    bool
	mu         sync.Mutex
}

// NewBrightnessController creates a brightness controller
func NewBrightnessController(client *Client, interval time.Duration, minBright, maxBright int) *BrightnessController {
	return &BrightnessController{
		client:    client,
		interval:  interval,
		minBright: minBright,
		maxBright: maxBright,
		stopChan:  make(chan struct{}),
		enabled:   true,
	}
}

// SetEnabled enables or disables the controller
func (bc *BrightnessController) SetEnabled(enabled bool) {
	bc.mu.Lock()
	bc.enabled = enabled
	bc.mu.Unlock()
}

// Start begins brightness control
func (bc *BrightnessController) Start(ctx context.Context) {
	go func() {
		ticker := time.NewTicker(bc.interval)
		defer ticker.Stop()

		for {
			select {
			case <-ctx.Done():
				return
			case <-bc.stopChan:
				return
			case <-ticker.C:
				bc.mu.Lock()
				enabled := bc.enabled
				bc.mu.Unlock()

				if !enabled {
					continue
				}

				lux, err := bc.client.GetLightLevel(ctx)
				if err != nil {
					continue
				}

				// Map lux to brightness
				// Typical indoor: 100-500 lux
				// Dim room: 10-50 lux
				// Bright sunlight: 10000+ lux
				brightness := bc.luxToBrightness(lux)
				bc.client.SetBrightness(ctx, brightness)
			}
		}
	}()
}

// luxToBrightness maps light level to screen brightness
func (bc *BrightnessController) luxToBrightness(lux float64) int {
	// Logarithmic mapping works well for human perception
	// 0 lux -> minBright
	// 1000 lux -> maxBright
	if lux <= 0 {
		return bc.minBright
	}

	// Use log scale: log10(1) = 0, log10(1000) = 3
	logLux := 0.0
	if lux > 1 {
		logLux = (lux / 1000.0)
		if logLux > 1 {
			logLux = 1
		}
	}

	brightness := bc.minBright + int(float64(bc.maxBright-bc.minBright)*logLux)
	if brightness < bc.minBright {
		brightness = bc.minBright
	}
	if brightness > bc.maxBright {
		brightness = bc.maxBright
	}

	return brightness
}

// Stop stops the controller
func (bc *BrightnessController) Stop() {
	close(bc.stopChan)
}
