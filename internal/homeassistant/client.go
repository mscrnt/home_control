package homeassistant

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sort"
	"strings"
	"time"
)

type Client struct {
	baseURL    string
	token      string
	httpClient *http.Client
}

type Entity struct {
	EntityID    string                 `json:"entity_id"`
	State       string                 `json:"state"`
	Attributes  map[string]interface{} `json:"attributes"`
	LastChanged time.Time              `json:"last_changed"`
}

func NewClient(baseURL, token string) *Client {
	return &Client{
		baseURL: baseURL,
		token:   token,
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// GetBaseURL returns the Home Assistant base URL
func (c *Client) GetBaseURL() string {
	return c.baseURL
}

// GetState fetches a single entity state
func (c *Client) GetState(entityID string) (*Entity, error) {
	url := fmt.Sprintf("%s/api/states/%s", c.baseURL, entityID)

	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("HA API error %d: %s", resp.StatusCode, string(body))
	}

	var entity Entity
	if err := json.NewDecoder(resp.Body).Decode(&entity); err != nil {
		return nil, err
	}

	return &entity, nil
}

// GetStates fetches multiple entity states
func (c *Client) GetStates(entityIDs []string) ([]*Entity, error) {
	entities := make([]*Entity, 0, len(entityIDs))
	for _, id := range entityIDs {
		entity, err := c.GetState(id)
		if err != nil {
			// Log error but continue with other entities
			entities = append(entities, &Entity{
				EntityID: id,
				State:    "unavailable",
				Attributes: map[string]interface{}{
					"friendly_name": id,
					"error":         err.Error(),
				},
			})
			continue
		}
		entities = append(entities, entity)
	}
	return entities, nil
}

// CallService calls a Home Assistant service (e.g., turn on light)
func (c *Client) CallService(domain, service, entityID string) error {
	url := fmt.Sprintf("%s/api/services/%s/%s", c.baseURL, domain, service)

	body := fmt.Sprintf(`{"entity_id": "%s"}`, entityID)

	req, err := http.NewRequest("POST", url, io.NopCloser(
		io.Reader(stringReader(body)),
	))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		respBody, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("HA service call failed %d: %s", resp.StatusCode, string(respBody))
	}

	return nil
}

type stringReader string

func (s stringReader) Read(p []byte) (n int, err error) {
	n = copy(p, s)
	return n, io.EOF
}

// SetClimateTemperature sets the target temperature for a climate entity
func (c *Client) SetClimateTemperature(entityID string, temperature float64) error {
	url := fmt.Sprintf("%s/api/services/climate/set_temperature", c.baseURL)

	body := fmt.Sprintf(`{"entity_id": "%s", "temperature": %.1f}`, entityID, temperature)

	req, err := http.NewRequest("POST", url, io.NopCloser(
		io.Reader(stringReader(body)),
	))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		respBody, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("HA service call failed %d: %s", resp.StatusCode, string(respBody))
	}

	return nil
}

// SetClimateDualTemperature sets both low and high target temperatures for heat_cool/auto mode
func (c *Client) SetClimateDualTemperature(entityID string, targetTempLow, targetTempHigh float64) error {
	url := fmt.Sprintf("%s/api/services/climate/set_temperature", c.baseURL)

	body := fmt.Sprintf(`{"entity_id": "%s", "target_temp_low": %.1f, "target_temp_high": %.1f}`, entityID, targetTempLow, targetTempHigh)

	req, err := http.NewRequest("POST", url, io.NopCloser(
		io.Reader(stringReader(body)),
	))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		respBody, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("HA service call failed %d: %s", resp.StatusCode, string(respBody))
	}

	return nil
}

// SetClimateHVACMode sets the HVAC mode for a climate entity (heat, cool, auto, off, etc.)
func (c *Client) SetClimateHVACMode(entityID string, hvacMode string) error {
	url := fmt.Sprintf("%s/api/services/climate/set_hvac_mode", c.baseURL)

	body := fmt.Sprintf(`{"entity_id": "%s", "hvac_mode": "%s"}`, entityID, hvacMode)

	req, err := http.NewRequest("POST", url, io.NopCloser(
		io.Reader(stringReader(body)),
	))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		respBody, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("HA service call failed %d: %s", resp.StatusCode, string(respBody))
	}

	return nil
}

// SetClimateFanMode sets the fan mode for a climate entity (on, off, auto, low, medium, high, etc.)
func (c *Client) SetClimateFanMode(entityID string, fanMode string) error {
	url := fmt.Sprintf("%s/api/services/climate/set_fan_mode", c.baseURL)

	body := fmt.Sprintf(`{"entity_id": "%s", "fan_mode": "%s"}`, entityID, fanMode)

	req, err := http.NewRequest("POST", url, io.NopCloser(
		io.Reader(stringReader(body)),
	))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		respBody, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("HA service call failed %d: %s", resp.StatusCode, string(respBody))
	}

	return nil
}

// CallServiceWithData calls a Home Assistant service with custom JSON data
func (c *Client) CallServiceWithData(domain, service string, data map[string]interface{}) error {
	url := fmt.Sprintf("%s/api/services/%s/%s", c.baseURL, domain, service)

	jsonData, err := json.Marshal(data)
	if err != nil {
		return err
	}

	req, err := http.NewRequest("POST", url, io.NopCloser(
		io.Reader(stringReader(string(jsonData))),
	))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		respBody, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("HA service call failed %d: %s", resp.StatusCode, string(respBody))
	}

	return nil
}

// DomainInfo contains metadata about an entity domain
type DomainInfo struct {
	Name     string `json:"name"`
	Icon     string `json:"icon"`
	Priority int    `json:"priority"` // Lower = higher priority in UI
}

// EntityRegistry holds categorized entities from Home Assistant
type EntityRegistry struct {
	Entities   map[string][]*Entity `json:"entities"`   // Domain -> entities
	Domains    []string             `json:"domains"`    // Sorted domain list
	LastUpdate time.Time            `json:"lastUpdate"`
	Config     *HAConfig            `json:"config,omitempty"`
}

// HAConfig represents Home Assistant configuration
type HAConfig struct {
	LocationName string   `json:"location_name"`
	Latitude     float64  `json:"latitude"`
	Longitude    float64  `json:"longitude"`
	Elevation    int      `json:"elevation"`
	UnitSystem   struct {
		Length      string `json:"length"`
		Mass        string `json:"mass"`
		Temperature string `json:"temperature"`
		Volume      string `json:"volume"`
	} `json:"unit_system"`
	TimeZone string `json:"time_zone"`
	Version  string `json:"version"`
}

// DomainMetadata provides display info for each domain
var DomainMetadata = map[string]DomainInfo{
	"automation":    {Name: "Automations", Icon: "robot", Priority: 10},
	"binary_sensor": {Name: "Binary Sensors", Icon: "checkbox-blank-circle", Priority: 40},
	"button":        {Name: "Buttons", Icon: "gesture-tap-button", Priority: 50},
	"camera":        {Name: "Cameras", Icon: "video", Priority: 15},
	"climate":       {Name: "Climate", Icon: "thermostat", Priority: 5},
	"cover":         {Name: "Covers", Icon: "window-shutter", Priority: 20},
	"device_tracker": {Name: "Device Trackers", Icon: "crosshairs-gps", Priority: 35},
	"fan":           {Name: "Fans", Icon: "fan", Priority: 25},
	"input_boolean": {Name: "Input Booleans", Icon: "toggle-switch", Priority: 55},
	"input_number":  {Name: "Input Numbers", Icon: "ray-vertex", Priority: 56},
	"input_select":  {Name: "Input Selects", Icon: "form-dropdown", Priority: 57},
	"light":         {Name: "Lights", Icon: "lightbulb", Priority: 1},
	"lock":          {Name: "Locks", Icon: "lock", Priority: 8},
	"media_player":  {Name: "Media Players", Icon: "cast", Priority: 12},
	"number":        {Name: "Numbers", Icon: "ray-vertex", Priority: 58},
	"person":        {Name: "People", Icon: "account", Priority: 30},
	"remote":        {Name: "Remotes", Icon: "remote", Priority: 45},
	"scene":         {Name: "Scenes", Icon: "palette", Priority: 11},
	"script":        {Name: "Scripts", Icon: "script-text", Priority: 13},
	"select":        {Name: "Selects", Icon: "form-dropdown", Priority: 59},
	"sensor":        {Name: "Sensors", Icon: "eye", Priority: 41},
	"switch":        {Name: "Switches", Icon: "toggle-switch", Priority: 3},
	"update":        {Name: "Updates", Icon: "package-up", Priority: 60},
	"vacuum":        {Name: "Vacuums", Icon: "robot-vacuum", Priority: 22},
	"weather":       {Name: "Weather", Icon: "weather-partly-cloudy", Priority: 42},
	"zone":          {Name: "Zones", Icon: "map-marker-radius", Priority: 70},
}

// GetAllStates fetches all entity states from Home Assistant
func (c *Client) GetAllStates() ([]*Entity, error) {
	url := fmt.Sprintf("%s/api/states", c.baseURL)

	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("HA API error %d: %s", resp.StatusCode, string(body))
	}

	var entities []*Entity
	if err := json.NewDecoder(resp.Body).Decode(&entities); err != nil {
		return nil, err
	}

	return entities, nil
}

// GetConfig fetches Home Assistant configuration
func (c *Client) GetConfig() (*HAConfig, error) {
	url := fmt.Sprintf("%s/api/config", c.baseURL)

	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("HA API error %d: %s", resp.StatusCode, string(body))
	}

	var config HAConfig
	if err := json.NewDecoder(resp.Body).Decode(&config); err != nil {
		return nil, err
	}

	return &config, nil
}

// BuildEntityRegistry fetches all entities and organizes them by domain
func (c *Client) BuildEntityRegistry() (*EntityRegistry, error) {
	entities, err := c.GetAllStates()
	if err != nil {
		return nil, fmt.Errorf("failed to fetch states: %w", err)
	}

	config, err := c.GetConfig()
	if err != nil {
		// Config is optional, continue without it
		config = nil
	}

	// Organize entities by domain
	registry := &EntityRegistry{
		Entities:   make(map[string][]*Entity),
		LastUpdate: time.Now(),
		Config:     config,
	}

	for _, entity := range entities {
		// Extract domain from entity_id (e.g., "light.living_room" -> "light")
		parts := strings.SplitN(entity.EntityID, ".", 2)
		if len(parts) < 2 {
			continue
		}
		domain := parts[0]

		if registry.Entities[domain] == nil {
			registry.Entities[domain] = make([]*Entity, 0)
		}
		registry.Entities[domain] = append(registry.Entities[domain], entity)
	}

	// Sort entities within each domain by friendly_name
	for domain := range registry.Entities {
		sort.Slice(registry.Entities[domain], func(i, j int) bool {
			nameI := getFriendlyName(registry.Entities[domain][i])
			nameJ := getFriendlyName(registry.Entities[domain][j])
			return nameI < nameJ
		})
	}

	// Build sorted domain list by priority
	for domain := range registry.Entities {
		registry.Domains = append(registry.Domains, domain)
	}
	sort.Slice(registry.Domains, func(i, j int) bool {
		priI := getDomainPriority(registry.Domains[i])
		priJ := getDomainPriority(registry.Domains[j])
		return priI < priJ
	})

	return registry, nil
}

// getFriendlyName extracts the friendly_name attribute from an entity
func getFriendlyName(e *Entity) string {
	if name, ok := e.Attributes["friendly_name"].(string); ok {
		return name
	}
	return e.EntityID
}

// getDomainPriority returns the UI priority for a domain
func getDomainPriority(domain string) int {
	if info, ok := DomainMetadata[domain]; ok {
		return info.Priority
	}
	return 100 // Unknown domains go to the end
}

// GetDomainInfo returns metadata for a domain
func GetDomainInfo(domain string) DomainInfo {
	if info, ok := DomainMetadata[domain]; ok {
		return info
	}
	// Return generic info for unknown domains
	return DomainInfo{
		Name:     strings.Title(strings.ReplaceAll(domain, "_", " ")),
		Icon:     "help-circle",
		Priority: 100,
	}
}

// AutomationConfig represents the configuration of a Home Assistant automation
type AutomationConfig struct {
	ID          string                   `json:"id"`
	Alias       string                   `json:"alias"`
	Description string                   `json:"description,omitempty"`
	Triggers    []map[string]interface{} `json:"triggers"`
	Conditions  []map[string]interface{} `json:"conditions,omitempty"`
	Actions     []map[string]interface{} `json:"actions"`
	Mode        string                   `json:"mode,omitempty"`
}

// FilteredAutomation represents a button/switch trigger entity that triggers an automation
type FilteredAutomation struct {
	EntityID       string `json:"entity_id"`       // The trigger entity (button/switch)
	FriendlyName   string `json:"friendly_name"`   // Friendly name of the trigger
	State          string `json:"state"`           // Current state of the trigger
	TriggerType    string `json:"trigger_type"`    // "button" or "switch"
	AutomationID   string `json:"automation_id"`   // The automation this triggers
	AutomationName string `json:"automation_name"` // Friendly name of the automation
}

// GetAutomationConfig fetches the configuration for a specific automation
func (c *Client) GetAutomationConfig(automationID string) (*AutomationConfig, error) {
	url := fmt.Sprintf("%s/api/config/automation/config/%s", c.baseURL, automationID)

	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("HA API error %d: %s", resp.StatusCode, string(body))
	}

	var config AutomationConfig
	if err := json.NewDecoder(resp.Body).Decode(&config); err != nil {
		return nil, err
	}
	config.ID = automationID

	return &config, nil
}

// GetButtonSwitchTriggeredAutomations returns button/switch entities that trigger automations
func (c *Client) GetButtonSwitchTriggeredAutomations() ([]FilteredAutomation, error) {
	// First, get all states to look up trigger entity info
	entities, err := c.GetAllStates()
	if err != nil {
		return nil, fmt.Errorf("failed to get states: %w", err)
	}

	// Build a map of entity states for quick lookup
	entityMap := make(map[string]*Entity)
	for _, entity := range entities {
		entityMap[entity.EntityID] = entity
	}

	// Track unique triggers (a button might trigger multiple automations)
	triggerMap := make(map[string]*FilteredAutomation)

	for _, entity := range entities {
		if !strings.HasPrefix(entity.EntityID, "automation.") {
			continue
		}

		// Skip disabled/unavailable automations
		if entity.State != "on" {
			continue
		}

		// Get the automation ID from attributes (HA uses numeric ID, not entity name)
		automationID, ok := entity.Attributes["id"].(string)
		if !ok || automationID == "" {
			continue
		}

		// Try to get the automation config
		config, err := c.GetAutomationConfig(automationID)
		if err != nil {
			// Skip automations we can't fetch config for
			continue
		}

		automationName := automationID
		if name, ok := entity.Attributes["friendly_name"].(string); ok {
			automationName = name
		}

		// Check if any trigger is for a button or switch
		triggerType, triggerEntityIDs := checkButtonSwitchTriggers(config.Triggers)
		if triggerType != "" {
			for _, triggerEntityID := range triggerEntityIDs {
				// Skip device IDs (not entity IDs)
				if !strings.Contains(triggerEntityID, ".") {
					continue
				}

				if _, exists := triggerMap[triggerEntityID]; !exists {
					// Look up the trigger entity state
					triggerEntity, found := entityMap[triggerEntityID]
					friendlyName := triggerEntityID
					state := "unknown"
					if found {
						state = triggerEntity.State
						if name, ok := triggerEntity.Attributes["friendly_name"].(string); ok {
							friendlyName = name
						}
					}

					// Determine trigger type from entity ID
					entityTriggerType := "button"
					if strings.HasPrefix(triggerEntityID, "switch.") || strings.HasPrefix(triggerEntityID, "input_boolean.") {
						entityTriggerType = "switch"
					}

					triggerMap[triggerEntityID] = &FilteredAutomation{
						EntityID:       triggerEntityID,
						FriendlyName:   friendlyName,
						State:          state,
						TriggerType:    entityTriggerType,
						AutomationID:   entity.EntityID,
						AutomationName: automationName,
					}
				}
			}
		}
	}

	// Convert map to slice
	var triggers []FilteredAutomation
	for _, trigger := range triggerMap {
		triggers = append(triggers, *trigger)
	}

	return triggers, nil
}

// checkButtonSwitchTriggers checks if triggers include button or switch entities
func checkButtonSwitchTriggers(triggers []map[string]interface{}) (triggerType string, triggerNames []string) {
	for _, trigger := range triggers {
		// Get platform/trigger type (newer HA uses "trigger" key, older uses "platform")
		platform := ""
		if p, ok := trigger["platform"].(string); ok {
			platform = p
		} else if p, ok := trigger["trigger"].(string); ok {
			platform = p
		}

		// Check for state triggers
		if platform == "state" {
			// Get entity_id (can be string or array)
			var entityIDs []string
			if eid, ok := trigger["entity_id"].(string); ok {
				entityIDs = append(entityIDs, eid)
			} else if eids, ok := trigger["entity_id"].([]interface{}); ok {
				for _, e := range eids {
					if eid, ok := e.(string); ok {
						entityIDs = append(entityIDs, eid)
					}
				}
			}

			for _, entityID := range entityIDs {
				// Check for button entities (button.* or input_button.*)
				if strings.HasPrefix(entityID, "button.") || strings.HasPrefix(entityID, "input_button.") {
					triggerType = "button"
					triggerNames = append(triggerNames, entityID)
				} else if strings.HasPrefix(entityID, "switch.") || strings.HasPrefix(entityID, "input_boolean.") {
					if triggerType == "" {
						triggerType = "switch"
					}
					triggerNames = append(triggerNames, entityID)
				}
			}
		}

		// Check for device triggers (button presses, switch toggles)
		if platform == "device" {
			domain := ""
			if d, ok := trigger["domain"].(string); ok {
				domain = d
			}
			triggerTypeVal := ""
			if t, ok := trigger["type"].(string); ok {
				triggerTypeVal = t
			}

			// Check for button press events
			if domain == "button" || triggerTypeVal == "pressed" || triggerTypeVal == "button_press" {
				triggerType = "button"
				if name, ok := trigger["device_id"].(string); ok {
					triggerNames = append(triggerNames, name)
				}
			}

			// Check for switch toggle events
			if domain == "switch" || triggerTypeVal == "turned_on" || triggerTypeVal == "turned_off" {
				if triggerType == "" {
					triggerType = "switch"
				}
				if name, ok := trigger["device_id"].(string); ok {
					triggerNames = append(triggerNames, name)
				}
			}
		}

		// Check for event triggers (for Zigbee/Z-Wave button events)
		if platform == "event" {
			eventType := ""
			if et, ok := trigger["event_type"].(string); ok {
				eventType = et
			}

			// Common button event types
			if strings.Contains(eventType, "button") ||
				strings.Contains(eventType, "click") ||
				strings.Contains(eventType, "press") ||
				eventType == "zha_event" ||
				eventType == "deconz_event" {
				triggerType = "button"
				triggerNames = append(triggerNames, eventType)
			}
		}
	}

	return triggerType, triggerNames
}

// FanEntity represents a fan entity with simplified fields for the API
type FanEntity struct {
	EntityID     string `json:"entity_id"`
	FriendlyName string `json:"friendly_name"`
	State        string `json:"state"` // "on" or "off"
}

// GetFanEntities fetches all fan entities from Home Assistant
func (c *Client) GetFanEntities() ([]FanEntity, error) {
	entities, err := c.GetAllStates()
	if err != nil {
		return nil, fmt.Errorf("failed to get states: %w", err)
	}

	var fans []FanEntity
	for _, entity := range entities {
		if !strings.HasPrefix(entity.EntityID, "fan.") {
			continue
		}

		// Skip unavailable entities
		if entity.State == "unavailable" {
			continue
		}

		friendlyName := entity.EntityID
		if name, ok := entity.Attributes["friendly_name"].(string); ok {
			friendlyName = name
		}

		fans = append(fans, FanEntity{
			EntityID:     entity.EntityID,
			FriendlyName: friendlyName,
			State:        entity.State,
		})
	}

	// Sort by friendly name
	sort.Slice(fans, func(i, j int) bool {
		return fans[i].FriendlyName < fans[j].FriendlyName
	})

	return fans, nil
}

// ClimateEntity represents a climate entity with fan mode control
type ClimateEntity struct {
	EntityID     string   `json:"entity_id"`
	FriendlyName string   `json:"friendly_name"`
	State        string   `json:"state"`    // Current HVAC state (off, heat, cool, etc.)
	FanMode      string   `json:"fan_mode"` // Current fan mode
	FanModes     []string `json:"fan_modes"`
}

// GetClimateEntities fetches all climate entities from Home Assistant
func (c *Client) GetClimateEntities() ([]ClimateEntity, error) {
	entities, err := c.GetAllStates()
	if err != nil {
		return nil, fmt.Errorf("failed to get states: %w", err)
	}

	var climates []ClimateEntity
	for _, entity := range entities {
		if !strings.HasPrefix(entity.EntityID, "climate.") {
			continue
		}

		// Skip unavailable entities
		if entity.State == "unavailable" {
			continue
		}

		// Skip Tesla climate entities
		if strings.Contains(entity.EntityID, "tessa") {
			continue
		}

		friendlyName := entity.EntityID
		if name, ok := entity.Attributes["friendly_name"].(string); ok {
			friendlyName = name
		}

		fanMode := ""
		if fm, ok := entity.Attributes["fan_mode"].(string); ok {
			fanMode = fm
		}

		var fanModes []string
		if fms, ok := entity.Attributes["fan_modes"].([]interface{}); ok {
			for _, fm := range fms {
				if s, ok := fm.(string); ok {
					fanModes = append(fanModes, s)
				}
			}
		}

		// Only include if it has fan modes
		if len(fanModes) > 0 {
			climates = append(climates, ClimateEntity{
				EntityID:     entity.EntityID,
				FriendlyName: friendlyName,
				State:        entity.State,
				FanMode:      fanMode,
				FanModes:     fanModes,
			})
		}
	}

	// Sort by friendly name
	sort.Slice(climates, func(i, j int) bool {
		return climates[i].FriendlyName < climates[j].FriendlyName
	})

	return climates, nil
}
