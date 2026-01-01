package entertainment

import (
	"bufio"
	"fmt"
	"log"
	"net"
	"os/exec"
	"regexp"
	"strconv"
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
	KeyHome        = 3
	KeyBack        = 4
	KeyCall        = 5
	KeyEndCall     = 6
	KeyDpadUp      = 19
	KeyDpadDown    = 20
	KeyDpadLeft    = 21
	KeyDpadRight   = 22
	KeyDpadCenter  = 23
	KeyVolumeUp    = 24
	KeyVolumeDown  = 25
	KeyPower       = 26
	KeyCamera      = 27
	KeyEnter       = 66
	KeyMenu        = 82
	KeySearch      = 84
	KeyPlayPause   = 85
	KeyStop        = 86
	KeyNext        = 87
	KeyPrevious    = 88
	KeyRewind      = 89
	KeyFastForward = 90
	KeyMute        = 164
	KeyPageUp      = 92
	KeyPageDown    = 93
	KeyPlay        = 126
	KeyPause       = 127
	KeyClose       = 128
	KeySleep       = 223
	KeyWakeUp      = 224
	KeyVoiceAssist = 231
	KeyTV          = 170
	KeyTVInput     = 178
	KeyGuide       = 172
	KeyDVR         = 173
	KeyBookmark    = 174
	KeyCaptions    = 175
	KeySettings    = 176
	KeyZoomIn      = 168
	KeyZoomOut     = 169
	KeyInfo        = 165
	KeyChannelUp   = 166
	KeyChannelDown = 167
)

// Common app package names
var ShieldApps = map[string]string{
	"netflix":      "com.netflix.ninja",
	"youtube":      "com.google.android.youtube.tv",
	"youtubetv":    "com.google.android.youtube.tvunplugged",
	"youtubemusic": "com.google.android.youtube.tvmusic",
	"plex":         "com.plexapp.android",
	"prime":        "com.amazon.amazonvideo.livingroom",
	"amazon":       "com.amazon.amazonvideo.livingroom",
	"disney":       "com.disney.disneyplus",
	"hulu":         "com.hulu.livingroomplus",
	"hbo":          "com.hbo.hbomax",
	"max":          "com.hbo.hbomax",
	"hbonow":       "com.hbo.hbonow",
	"spotify":      "com.spotify.tv.android",
	"kodi":         "org.xbmc.kodi",
	"settings":     "com.android.tv.settings",
	"gamestream":   "com.nvidia.tegrazone3",
	"twitch":       "tv.twitch.android.app",
	"crunchyroll":  "com.crunchyroll.crunchyroid",
	"peacock":      "com.peacocktv.peacockandroid",
	"appletv":      "com.apple.atve.androidtv.appletv",
	"steamlink":    "com.valvesoftware.steamlink",
	"gamepass":     "com.gamepass",
	"xbox":         "com.gamepass",
	"retroarch":    "com.retroarch",
	"chrome":       "com.android.chrome",
	"discovery":    "com.wbd.stream",
	"roku":         "com.roku.web.trc",
}

// PackageToName maps package names to friendly display names
var PackageToName = map[string]string{
	"com.netflix.ninja":                    "Netflix",
	"com.google.android.youtube.tv":        "YouTube",
	"com.google.android.youtube.tvunplugged": "YouTube TV",
	"com.google.android.youtube.tvmusic":   "YouTube Music",
	"com.plexapp.android":                  "Plex",
	"com.amazon.amazonvideo.livingroom":    "Prime Video",
	"com.disney.disneyplus":                "Disney+",
	"com.hulu.livingroomplus":              "Hulu",
	"com.hbo.hbomax":                       "Max",
	"com.hbo.hbonow":                       "HBO Now",
	"com.spotify.tv.android":               "Spotify",
	"org.xbmc.kodi":                        "Kodi",
	"com.android.tv.settings":              "Settings",
	"com.nvidia.tegrazone3":                "GeForce NOW",
	"tv.twitch.android.app":                "Twitch",
	"com.crunchyroll.crunchyroid":          "Crunchyroll",
	"com.peacocktv.peacockandroid":         "Peacock",
	"com.apple.atve.androidtv.appletv":     "Apple TV",
	"com.valvesoftware.steamlink":          "Steam Link",
	"com.gamepass":                         "Xbox Game Pass",
	"com.retroarch":                        "RetroArch",
	"com.android.chrome":                   "Chrome",
	"com.wbd.stream":                       "Discovery+",
	"com.roku.web.trc":                     "Roku",
	"com.google.android.tvlauncher":        "Home",
	"com.nvidia.shield.remote":             "Shield Remote",
	"com.nvidia.shield.nvcustomize":        "Customize",
	"com.nvidia.shieldtech.accessory":      "Accessories",
	"com.nvidia.shield.systemapps":         "System Apps",
	"com.android.vending":                  "Play Store",
	"com.google.android.videos":            "Google Play Movies",
	"com.google.android.music":             "Google Play Music",
	"com.google.android.apps.tv.launcherx": "Android TV Home",
	"com.nvidia.bbciplayer":                "BBC iPlayer",
	"com.vudu.air":                         "Vudu",
	"com.tubi.tv":                          "Tubi",
	"com.cbs.ott":                          "Paramount+",
	"com.showtime.standalone":              "Showtime",
	"com.starz.starzplay.android":          "Starz",
	"com.funimation.funimationapp":         "Funimation",
	"com.gotv.nflgamecenter.us.lite":       "NFL",
	"com.espn.score_center":                "ESPN",
	"air.ITVMobilePlayer":                  "ITV Hub",
	"uk.co.channel4.fourdod":               "Channel 4",
}

// NewShieldManager creates a new Shield manager
func NewShieldManager() *ShieldManager {
	// Start ADB daemon early to avoid delay on first command
	go startADBDaemon()

	return &ShieldManager{
		devices: make(map[string]*ShieldDevice),
	}
}

// startADBDaemon starts the ADB daemon in the background
func startADBDaemon() {
	cmd := exec.Command("adb", "start-server")
	output, err := cmd.CombinedOutput()
	if err != nil {
		log.Printf("Failed to start ADB daemon: %v", err)
	} else {
		log.Printf("ADB daemon started: %s", strings.TrimSpace(string(output)))
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
	_, err := d.execADBWithOutput(args...)
	return err
}

// execADBWithOutput executes an ADB command and returns the output
func (d *ShieldDevice) execADBWithOutput(args ...string) (string, error) {
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
		return "", fmt.Errorf("adb command failed: %w", err)
	}

	result := strings.TrimSpace(string(output))
	if len(result) > 0 && len(result) < 500 {
		log.Printf("ADB output: %s", result)
	}
	return result, nil
}

// ShellWithOutput executes a shell command and returns the output
func (d *ShieldDevice) ShellWithOutput(cmd string) (string, error) {
	return d.execADBWithOutput("shell", cmd)
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
	output, err := d.ShellWithOutput("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
	if err != nil {
		return "", err
	}

	// Parse mCurrentFocus=Window{... com.package.name/...Activity}
	re := regexp.MustCompile(`mCurrentFocus=Window\{[^}]+ ([a-zA-Z0-9_.]+)/`)
	matches := re.FindStringSubmatch(output)
	if len(matches) > 1 {
		return matches[1], nil
	}

	// Try mFocusedApp as fallback
	re = regexp.MustCompile(`mFocusedApp=.*\s([a-zA-Z0-9_.]+)/`)
	matches = re.FindStringSubmatch(output)
	if len(matches) > 1 {
		return matches[1], nil
	}

	return "", nil
}

// GetPowerState returns the current power/wakefulness state
func (d *ShieldDevice) GetPowerState() (PowerState, error) {
	output, err := d.ShellWithOutput("dumpsys power | grep mWakefulness")
	if err != nil {
		return PowerStateUnknown, err
	}

	output = strings.ToLower(output)
	if strings.Contains(output, "awake") {
		return PowerStateAwake, nil
	} else if strings.Contains(output, "dreaming") {
		return PowerStateDreaming, nil
	} else if strings.Contains(output, "asleep") || strings.Contains(output, "dozing") {
		return PowerStateAsleep, nil
	}

	return PowerStateUnknown, nil
}

// GetVolume returns the current media volume level (0-100) and mute state
func (d *ShieldDevice) GetVolume() (volume int, muted bool, err error) {
	output, err := d.ShellWithOutput("dumpsys audio | grep -A5 'STREAM_MUSIC'")
	if err != nil {
		return 0, false, err
	}

	// Parse volume from output - this is device-specific
	// For Shield, try to get the current index
	re := regexp.MustCompile(`index:(\d+)`)
	matches := re.FindStringSubmatch(output)
	if len(matches) > 1 {
		vol, _ := strconv.Atoi(matches[1])
		// Shield typically has 0-15 volume range, convert to 0-100
		volume = vol * 100 / 15
	}

	// Check mute state
	muted = strings.Contains(strings.ToLower(output), "muted")

	return volume, muted, nil
}

// GetBrightness returns the current screen brightness (0-255)
func (d *ShieldDevice) GetBrightness() (int, error) {
	output, err := d.ShellWithOutput("settings get system screen_brightness")
	if err != nil {
		return 0, err
	}

	brightness, err := strconv.Atoi(strings.TrimSpace(output))
	if err != nil {
		return 0, fmt.Errorf("failed to parse brightness: %w", err)
	}

	return brightness, nil
}

// SetBrightness sets the screen brightness (0-255)
func (d *ShieldDevice) SetBrightness(level int) error {
	if level < 0 {
		level = 0
	} else if level > 255 {
		level = 255
	}

	cmd := fmt.Sprintf("settings put system screen_brightness %d", level)
	return d.Shell(cmd)
}

// SendText types text on the device (for search, etc.)
func (d *ShieldDevice) SendText(text string) error {
	// Escape special characters for shell
	escaped := strings.ReplaceAll(text, " ", "%s")
	escaped = strings.ReplaceAll(escaped, "'", "\\'")
	escaped = strings.ReplaceAll(escaped, "\"", "\\\"")

	cmd := fmt.Sprintf("input text '%s'", escaped)
	return d.Shell(cmd)
}

// ClearText clears text input by selecting all and deleting
func (d *ShieldDevice) ClearText() error {
	// Select all (Ctrl+A) and delete
	if err := d.Shell("input keyevent 29 --longpress"); err != nil { // KEYCODE_A with meta
		return err
	}
	return d.SendKeyEvent(67) // KEYCODE_DEL
}

// LongPressKeyEvent sends a long press key event
func (d *ShieldDevice) LongPressKeyEvent(keyCode int) error {
	cmd := fmt.Sprintf("input keyevent --longpress %d", keyCode)
	return d.Shell(cmd)
}

// GetDeviceInfo returns device hardware/software information
func (d *ShieldDevice) GetDeviceInfo() (*ShieldDeviceInfo, error) {
	info := &ShieldDeviceInfo{}

	// Get properties in parallel for speed
	props := map[string]*string{
		"ro.product.model":           &info.Model,
		"ro.product.manufacturer":    &info.Manufacturer,
		"ro.build.version.release":   &info.AndroidVer,
		"ro.build.version.sdk":       &info.SDKVersion,
		"ro.build.version.incremental": &info.BuildVersion,
		"ro.serialno":                &info.Serial,
	}

	for prop, dest := range props {
		output, err := d.ShellWithOutput(fmt.Sprintf("getprop %s", prop))
		if err == nil {
			*dest = strings.TrimSpace(output)
		}
	}

	return info, nil
}

// ListInstalledApps returns launchable apps (apps with Android TV launcher activity)
func (d *ShieldDevice) ListInstalledApps(includeSystem bool) ([]ShieldApp, error) {
	var apps []ShieldApp
	seen := make(map[string]bool)

	// Get launchable Android TV apps using LEANBACK_LAUNCHER category
	// This returns apps that appear in the Android TV launcher
	output, err := d.ShellWithOutput("pm query-activities -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER")
	if err != nil {
		// Fallback to old method if query-activities doesn't work
		return d.listInstalledAppsFallback(includeSystem)
	}

	// Parse output - look for lines with package/activity format
	// Example formats:
	//   com.netflix.ninja/.MainActivity
	//   Activity #0: com.netflix.ninja/.Activity
	// Skip metadata lines like SourceDir=, DataDir=, etc.
	packageActivityRegex := regexp.MustCompile(`^(?:Activity\s+#\d+:\s*)?([a-zA-Z][a-zA-Z0-9_.]*)/\.`)

	for _, line := range strings.Split(output, "\n") {
		line = strings.TrimSpace(line)

		// Skip empty lines and metadata lines (contain =)
		if line == "" || strings.Contains(line, "=") {
			continue
		}

		if strings.HasPrefix(line, "package:") {
			// Format: package:com.netflix.ninja
			pkg := strings.TrimPrefix(line, "package:")
			if !seen[pkg] {
				seen[pkg] = true
				apps = append(apps, ShieldApp{
					PackageName: pkg,
					Name:        getFriendlyName(pkg),
					IsSystem:    isSystemApp(pkg),
				})
			}
		} else if matches := packageActivityRegex.FindStringSubmatch(line); len(matches) > 1 {
			// Format: com.netflix.ninja/.MainActivity
			pkg := matches[1]
			if pkg != "" && !seen[pkg] {
				seen[pkg] = true
				apps = append(apps, ShieldApp{
					PackageName: pkg,
					Name:        getFriendlyName(pkg),
					IsSystem:    isSystemApp(pkg),
				})
			}
		}
	}

	// If we didn't get any apps, try fallback
	if len(apps) == 0 {
		return d.listInstalledAppsFallback(includeSystem)
	}

	// Filter out system apps if not requested
	if !includeSystem {
		filtered := make([]ShieldApp, 0, len(apps))
		for _, app := range apps {
			if !app.IsSystem {
				filtered = append(filtered, app)
			}
		}
		apps = filtered
	}

	return apps, nil
}

// listInstalledAppsFallback uses the old pm list packages method
func (d *ShieldDevice) listInstalledAppsFallback(includeSystem bool) ([]ShieldApp, error) {
	var apps []ShieldApp

	// Get third-party apps
	output, err := d.ShellWithOutput("pm list packages -3")
	if err != nil {
		return nil, err
	}

	for _, line := range strings.Split(output, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "package:") {
			pkg := strings.TrimPrefix(line, "package:")
			apps = append(apps, ShieldApp{
				PackageName: pkg,
				Name:        getFriendlyName(pkg),
				IsSystem:    false,
			})
		}
	}

	// Optionally include system apps
	if includeSystem {
		output, err := d.ShellWithOutput("pm list packages -s")
		if err == nil {
			for _, line := range strings.Split(output, "\n") {
				line = strings.TrimSpace(line)
				if strings.HasPrefix(line, "package:") {
					pkg := strings.TrimPrefix(line, "package:")
					apps = append(apps, ShieldApp{
						PackageName: pkg,
						Name:        getFriendlyName(pkg),
						IsSystem:    true,
					})
				}
			}
		}
	}

	return apps, nil
}

// getFriendlyName returns a friendly display name for a package
func getFriendlyName(pkg string) string {
	if name, ok := PackageToName[pkg]; ok {
		return name
	}
	// Fall back to extracting a readable name from the package
	// e.g., "com.example.myapp" -> "Myapp"
	parts := strings.Split(pkg, ".")
	if len(parts) > 0 {
		name := parts[len(parts)-1]
		// Capitalize first letter
		if len(name) > 0 {
			return strings.ToUpper(name[:1]) + name[1:]
		}
	}
	return pkg
}

// isSystemApp checks if a package is a known system app
func isSystemApp(pkg string) bool {
	systemPrefixes := []string{
		"com.android.",
		"com.google.android.gms",
		"com.google.android.gsf",
		"com.google.android.ext.",
		"com.nvidia.shield.",
		"com.nvidia.shieldtech.",
		"com.nvidia.fallback.",
		"com.nvidia.diagtools",
		"com.nvidia.benchmarks",
		"com.nvidia.hotwordsetup",
	}
	for _, prefix := range systemPrefixes {
		if strings.HasPrefix(pkg, prefix) {
			return true
		}
	}
	return false
}

// OpenSettings opens the Settings app
func (d *ShieldDevice) OpenSettings() error {
	return d.LaunchApp("com.android.tv.settings")
}

// TakeScreenshot captures a screenshot and returns the path on the device
func (d *ShieldDevice) TakeScreenshot() (string, error) {
	path := "/sdcard/screenshot.png"
	cmd := fmt.Sprintf("screencap -p %s", path)
	err := d.Shell(cmd)
	if err != nil {
		return "", err
	}
	return path, nil
}

// Reboot reboots the device
func (d *ShieldDevice) Reboot() error {
	return d.Shell("reboot")
}

// OpenURL opens a URL in the default browser
func (d *ShieldDevice) OpenURL(url string) error {
	cmd := fmt.Sprintf("am start -a android.intent.action.VIEW -d '%s'", url)
	return d.Shell(cmd)
}

// Search triggers voice/text search
func (d *ShieldDevice) Search() error {
	return d.SendKeyEvent(KeySearch)
}

// VoiceAssistant triggers the voice assistant
func (d *ShieldDevice) VoiceAssistant() error {
	return d.SendKeyEvent(KeyVoiceAssist)
}

// TVInput switches TV input
func (d *ShieldDevice) TVInput() error {
	return d.SendKeyEvent(KeyTVInput)
}

// Info shows info overlay
func (d *ShieldDevice) Info() error {
	return d.SendKeyEvent(KeyInfo)
}

// Captions toggles captions
func (d *ShieldDevice) Captions() error {
	return d.SendKeyEvent(KeyCaptions)
}

// ========== Device State ==========

// PowerState represents the power/wakefulness state
type PowerState string

const (
	PowerStateAwake    PowerState = "awake"
	PowerStateDreaming PowerState = "dreaming"
	PowerStateAsleep   PowerState = "asleep"
	PowerStateUnknown  PowerState = "unknown"
)

// ShieldState represents the state of a Shield device
type ShieldState struct {
	Name       string     `json:"name"`
	Host       string     `json:"host"`
	Online     bool       `json:"online"`
	PowerState PowerState `json:"power_state,omitempty"`
	CurrentApp string     `json:"current_app,omitempty"`
	Volume     int        `json:"volume,omitempty"`
	Muted      bool       `json:"muted,omitempty"`
	Brightness int        `json:"brightness,omitempty"`
	Error      string     `json:"error,omitempty"`
}

// ShieldDeviceInfo contains device hardware/software info
type ShieldDeviceInfo struct {
	Model        string `json:"model"`
	Manufacturer string `json:"manufacturer"`
	AndroidVer   string `json:"android_version"`
	SDKVersion   string `json:"sdk_version"`
	BuildVersion string `json:"build_version"`
	Serial       string `json:"serial"`
}

// ShieldApp represents an installed app
type ShieldApp struct {
	PackageName string `json:"package"`
	Name        string `json:"name,omitempty"`
	IsSystem    bool   `json:"is_system"`
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
		return state
	}
	conn.Close()
	state.Online = true

	// Get power state
	if powerState, err := d.GetPowerState(); err == nil {
		state.PowerState = powerState
	}

	// Get current app and convert to friendly name
	if currentApp, err := d.GetCurrentApp(); err == nil {
		state.CurrentApp = getFriendlyName(currentApp)
	}

	// Get volume (optional, may fail on some devices)
	if vol, muted, err := d.GetVolume(); err == nil {
		state.Volume = vol
		state.Muted = muted
	}

	// Get brightness
	if brightness, err := d.GetBrightness(); err == nil {
		state.Brightness = brightness
	}

	return state
}

// GetFullState returns a comprehensive state (slower, more API calls)
func (d *ShieldDevice) GetFullState() *ShieldState {
	return d.GetState()
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
