package hue

import (
	"bytes"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sort"
	"time"
)

// Client represents a Philips Hue Bridge API v2 client
type Client struct {
	bridgeIP   string
	appKey     string // Renamed from username - same value, but V2 terminology
	httpClient *http.Client
}

// V2 API response wrapper
type v2Response struct {
	Errors []v2Error       `json:"errors"`
	Data   json.RawMessage `json:"data"`
}

type v2Error struct {
	Description string `json:"description"`
}

// ResourceRef is a reference to another resource (used throughout V2 API)
type ResourceRef struct {
	RID   string `json:"rid"`
	RType string `json:"rtype"`
}

// V2 Light resource
type v2Light struct {
	ID       string       `json:"id"`
	IDV1     string       `json:"id_v1"`
	Owner    *ResourceRef `json:"owner,omitempty"`
	Metadata struct {
		Name      string `json:"name"`
		Archetype string `json:"archetype"`
	} `json:"metadata"`
	On struct {
		On bool `json:"on"`
	} `json:"on"`
	Dimming *struct {
		Brightness  float64 `json:"brightness"`
		MinDimLevel float64 `json:"min_dim_level,omitempty"`
	} `json:"dimming,omitempty"`
	ColorTemperature *struct {
		Mirek      *int `json:"mirek"`
		MirekValid bool `json:"mirek_valid"`
		MirekSchema *struct {
			MirekMinimum int `json:"mirek_minimum"`
			MirekMaximum int `json:"mirek_maximum"`
		} `json:"mirek_schema,omitempty"`
	} `json:"color_temperature,omitempty"`
	Color *struct {
		XY struct {
			X float64 `json:"x"`
			Y float64 `json:"y"`
		} `json:"xy"`
		Gamut *struct {
			Red   struct{ X, Y float64 } `json:"red"`
			Green struct{ X, Y float64 } `json:"green"`
			Blue  struct{ X, Y float64 } `json:"blue"`
		} `json:"gamut,omitempty"`
		GamutType string `json:"gamut_type,omitempty"`
	} `json:"color,omitempty"`
	Mode string `json:"mode,omitempty"`
	Type string `json:"type"`
}

// V2 Room resource
type v2Room struct {
	ID       string        `json:"id"`
	IDV1     string        `json:"id_v1"`
	Children []ResourceRef `json:"children"`
	Services []ResourceRef `json:"services"`
	Metadata struct {
		Name      string `json:"name"`
		Archetype string `json:"archetype"`
	} `json:"metadata"`
	Type string `json:"type"`
}

// V2 Zone resource (similar to room but children are lights, not devices)
type v2Zone struct {
	ID       string        `json:"id"`
	IDV1     string        `json:"id_v1"`
	Children []ResourceRef `json:"children"` // References light services directly
	Services []ResourceRef `json:"services"`
	Metadata struct {
		Name      string `json:"name"`
		Archetype string `json:"archetype"`
	} `json:"metadata"`
	Type string `json:"type"`
}

// V2 GroupedLight resource (for room/zone-level control)
type v2GroupedLight struct {
	ID    string       `json:"id"`
	IDV1  string       `json:"id_v1"`
	Owner *ResourceRef `json:"owner,omitempty"`
	On    struct {
		On bool `json:"on"`
	} `json:"on"`
	Dimming *struct {
		Brightness float64 `json:"brightness"`
	} `json:"dimming,omitempty"`
	Type string `json:"type"`
}

// V2 Scene resource
type v2Scene struct {
	ID    string `json:"id"`
	IDV1  string `json:"id_v1"`
	Group struct {
		RID   string `json:"rid"`
		RType string `json:"rtype"`
	} `json:"group"`
	Actions []struct {
		Target ResourceRef `json:"target"`
		Action struct {
			On *struct {
				On bool `json:"on"`
			} `json:"on,omitempty"`
			Dimming *struct {
				Brightness float64 `json:"brightness"`
			} `json:"dimming,omitempty"`
		} `json:"action"`
	} `json:"actions"`
	Metadata struct {
		Name  string       `json:"name"`
		Image *ResourceRef `json:"image,omitempty"`
	} `json:"metadata"`
	Type   string `json:"type"`
	Status struct {
		Active string `json:"active,omitempty"`
	} `json:"status,omitempty"`
}

// V2 Entertainment Configuration
type v2EntertainmentConfig struct {
	ID       string `json:"id"`
	IDV1     string `json:"id_v1"`
	Name     string `json:"name"`
	Status   string `json:"status"` // "active" or "inactive"
	Metadata struct {
		Name string `json:"name"`
	} `json:"metadata"`
	ConfigurationType string `json:"configuration_type"`
	Type              string `json:"type"`
	Channels          []struct {
		ChannelID int `json:"channel_id"`
		Members   []struct {
			Service ResourceRef `json:"service"`
		} `json:"members"`
	} `json:"channels"`
	LightServices []ResourceRef `json:"light_services"` // Direct light references
}

// V2 Device resource (needed to map devices to lights)
type v2Device struct {
	ID       string        `json:"id"`
	IDV1     string        `json:"id_v1"`
	Services []ResourceRef `json:"services"`
	Metadata struct {
		Name      string `json:"name"`
		Archetype string `json:"archetype"`
	} `json:"metadata"`
	ProductData *struct {
		ModelID         string `json:"model_id"`
		ManufacturerName string `json:"manufacturer_name"`
		ProductName     string `json:"product_name"`
		SoftwareVersion string `json:"software_version"`
	} `json:"product_data,omitempty"`
	Type string `json:"type"`
}

// Light represents a Hue light (public API, compatible with existing callers)
type Light struct {
	ID           string     `json:"id"`
	Name         string     `json:"name"`
	Type         string     `json:"type"`
	ModelID      string     `json:"modelid"`
	ProductName  string     `json:"productname"`
	State        LightState `json:"state"`
	Capabilities struct {
		Control struct {
			ColorGamutType string `json:"colorgamuttype"`
			CT             struct {
				Min int `json:"min"`
				Max int `json:"max"`
			} `json:"ct"`
		} `json:"control"`
	} `json:"capabilities"`
}

// LightState represents the state of a light
type LightState struct {
	On         bool      `json:"on"`
	Brightness int       `json:"bri,omitempty"`  // 1-254 for API compat, internally 0-100%
	Hue        int       `json:"hue,omitempty"`  // 0-65535
	Saturation int       `json:"sat,omitempty"`  // 0-254
	CT         int       `json:"ct,omitempty"`   // 153-500 (mireds)
	XY         []float64 `json:"xy,omitempty"`   // CIE color space
	ColorMode  string    `json:"colormode,omitempty"`
	Effect     string    `json:"effect,omitempty"`
	Alert      string    `json:"alert,omitempty"`
	Reachable  bool      `json:"reachable"`
}

// EntertainmentStream represents the streaming state of an entertainment area
type EntertainmentStream struct {
	Active    bool   `json:"active"`
	Owner     string `json:"owner,omitempty"`
	ProxyMode string `json:"proxymode,omitempty"`
	ProxyNode string `json:"proxynode,omitempty"`
}

// Group represents a Hue group (room, zone, or entertainment area)
type Group struct {
	ID             string               `json:"id"`
	Name           string               `json:"name"`
	Lights         []string             `json:"lights"` // Light IDs (UUIDs in V2)
	Type           string               `json:"type"`   // Room, Zone, Entertainment
	Class          string               `json:"class,omitempty"`
	State          GroupState           `json:"state"`
	Action         LightState           `json:"action"`
	Stream         *EntertainmentStream `json:"stream,omitempty"`
	GroupedLightID string               `json:"groupedLightId,omitempty"` // V2: ID of grouped_light service
}

// GroupState represents the aggregate state of a group
type GroupState struct {
	AllOn bool `json:"all_on"`
	AnyOn bool `json:"any_on"`
}

// Scene represents a Hue scene
type Scene struct {
	ID          string   `json:"id"`
	Name        string   `json:"name"`
	Type        string   `json:"type"` // GroupScene, LightScene
	Group       string   `json:"group"`
	Lights      []string `json:"lights"`
	Owner       string   `json:"owner"`
	Recycle     bool     `json:"recycle"`
	Locked      bool     `json:"locked"`
	Image       string   `json:"image,omitempty"`
	LastUpdated string   `json:"lastupdated"`
	Active      bool     `json:"active"` // V2: whether this scene is currently active
}

// Room is a convenience type for rooms/zones that includes lights
type Room struct {
	ID              string   `json:"id"`
	Name            string   `json:"name"`
	Type            string   `json:"type"`
	Class           string   `json:"class"`
	IsOn            bool     `json:"isOn"`
	Brightness      int      `json:"brightness,omitempty"` // 0-100
	Lights          []*Light `json:"lights"`
	Scenes          []*Scene `json:"scenes"`
	StreamingActive bool     `json:"streamingActive,omitempty"`
	GroupedLightID  string   `json:"groupedLightId,omitempty"`
}

// NewClient creates a new Hue Bridge client (V2 API)
func NewClient(bridgeIP, username string) *Client {
	// Skip TLS verification for local Hue bridge (self-signed cert)
	tr := &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}
	return &Client{
		bridgeIP: bridgeIP,
		appKey:   username, // Same value, V2 uses it as application key in header
		httpClient: &http.Client{
			Timeout:   10 * time.Second,
			Transport: tr,
		},
	}
}

// V2 API base URL
func (c *Client) apiV2URL(resourceType string) string {
	if resourceType == "" {
		return fmt.Sprintf("https://%s/clip/v2/resource", c.bridgeIP)
	}
	return fmt.Sprintf("https://%s/clip/v2/resource/%s", c.bridgeIP, resourceType)
}

// doV2Get performs a GET request to the V2 API
func (c *Client) doV2Get(resourceType string) ([]byte, error) {
	req, err := http.NewRequest("GET", c.apiV2URL(resourceType), nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("hue-application-key", c.appKey)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("hue API request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	// V2 API returns proper HTTP status codes
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("hue API error (status %d): %s", resp.StatusCode, string(body))
	}

	return body, nil
}

// doV2Put performs a PUT request to the V2 API
func (c *Client) doV2Put(resourceType, id string, data interface{}) error {
	jsonData, err := json.Marshal(data)
	if err != nil {
		return fmt.Errorf("failed to marshal request: %w", err)
	}

	url := fmt.Sprintf("%s/%s", c.apiV2URL(resourceType), id)
	req, err := http.NewRequest("PUT", url, bytes.NewBuffer(jsonData))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("hue-application-key", c.appKey)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("hue API request failed: %w", err)
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)

	// Check for V2 API errors
	if resp.StatusCode >= 400 {
		return fmt.Errorf("hue API error (status %d): %s", resp.StatusCode, string(body))
	}

	// Also check for errors in response body
	var v2resp v2Response
	if err := json.Unmarshal(body, &v2resp); err == nil {
		if len(v2resp.Errors) > 0 {
			return fmt.Errorf("hue API error: %s", v2resp.Errors[0].Description)
		}
	}

	return nil
}

// parseV2Response parses a V2 API response and extracts the data array
func parseV2Response[T any](body []byte) ([]T, error) {
	var resp v2Response
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	if len(resp.Errors) > 0 {
		return nil, fmt.Errorf("hue API error: %s", resp.Errors[0].Description)
	}

	var data []T
	if err := json.Unmarshal(resp.Data, &data); err != nil {
		return nil, fmt.Errorf("failed to parse data: %w", err)
	}

	return data, nil
}

// GetLights returns all lights from the bridge
func (c *Client) GetLights() ([]*Light, error) {
	body, err := c.doV2Get("light")
	if err != nil {
		return nil, err
	}

	v2lights, err := parseV2Response[v2Light](body)
	if err != nil {
		return nil, err
	}

	lights := make([]*Light, 0, len(v2lights))
	for _, v2l := range v2lights {
		light := &Light{
			ID:   v2l.ID,
			Name: v2l.Metadata.Name,
			Type: v2l.Type,
			State: LightState{
				On:        v2l.On.On,
				Reachable: true, // V2 handles this via connectivity resource
			},
		}

		// Convert brightness from 0-100% to 1-254 for API compat
		if v2l.Dimming != nil {
			light.State.Brightness = int(v2l.Dimming.Brightness * 254 / 100)
			if light.State.Brightness < 1 && v2l.On.On {
				light.State.Brightness = 1
			}
		}

		// Color temperature
		if v2l.ColorTemperature != nil && v2l.ColorTemperature.Mirek != nil {
			light.State.CT = *v2l.ColorTemperature.Mirek
			light.State.ColorMode = "ct"
			if v2l.ColorTemperature.MirekSchema != nil {
				light.Capabilities.Control.CT.Min = v2l.ColorTemperature.MirekSchema.MirekMinimum
				light.Capabilities.Control.CT.Max = v2l.ColorTemperature.MirekSchema.MirekMaximum
			}
		}

		// Color XY
		if v2l.Color != nil {
			light.State.XY = []float64{v2l.Color.XY.X, v2l.Color.XY.Y}
			if light.State.ColorMode == "" {
				light.State.ColorMode = "xy"
			}
			if v2l.Color.GamutType != "" {
				light.Capabilities.Control.ColorGamutType = v2l.Color.GamutType
			}
		}

		lights = append(lights, light)
	}

	return lights, nil
}

// GetLight returns a single light by ID
func (c *Client) GetLight(id string) (*Light, error) {
	body, err := c.doV2Get("light/" + id)
	if err != nil {
		return nil, err
	}

	v2lights, err := parseV2Response[v2Light](body)
	if err != nil {
		return nil, err
	}

	if len(v2lights) == 0 {
		return nil, fmt.Errorf("light %s not found", id)
	}

	v2l := v2lights[0]
	light := &Light{
		ID:   v2l.ID,
		Name: v2l.Metadata.Name,
		Type: v2l.Type,
		State: LightState{
			On:        v2l.On.On,
			Reachable: true,
		},
	}

	if v2l.Dimming != nil {
		light.State.Brightness = int(v2l.Dimming.Brightness * 254 / 100)
	}

	if v2l.ColorTemperature != nil && v2l.ColorTemperature.Mirek != nil {
		light.State.CT = *v2l.ColorTemperature.Mirek
	}

	if v2l.Color != nil {
		light.State.XY = []float64{v2l.Color.XY.X, v2l.Color.XY.Y}
	}

	return light, nil
}

// SetLightState sets the state of a light using V2 API
func (c *Client) SetLightState(id string, state map[string]interface{}) error {
	v2state := make(map[string]interface{})

	// Convert V1-style state to V2 format
	if on, ok := state["on"].(bool); ok {
		v2state["on"] = map[string]bool{"on": on}
	}

	// Convert brightness from 1-254 to 0-100%
	if bri, ok := state["bri"].(int); ok {
		brightness := float64(bri) * 100 / 254
		v2state["dimming"] = map[string]float64{"brightness": brightness}
	}

	// Color temperature (mirek values are the same)
	if ct, ok := state["ct"].(int); ok {
		v2state["color_temperature"] = map[string]int{"mirek": ct}
	}

	// XY color
	if xy, ok := state["xy"].([]float64); ok && len(xy) == 2 {
		v2state["color"] = map[string]interface{}{
			"xy": map[string]float64{"x": xy[0], "y": xy[1]},
		}
	}

	return c.doV2Put("light", id, v2state)
}

// TurnOnLight turns on a light
func (c *Client) TurnOnLight(id string) error {
	return c.doV2Put("light", id, map[string]interface{}{
		"on": map[string]bool{"on": true},
	})
}

// TurnOffLight turns off a light
func (c *Client) TurnOffLight(id string) error {
	return c.doV2Put("light", id, map[string]interface{}{
		"on": map[string]bool{"on": false},
	})
}

// ToggleLight toggles a light on/off
func (c *Client) ToggleLight(id string) error {
	light, err := c.GetLight(id)
	if err != nil {
		return err
	}
	return c.doV2Put("light", id, map[string]interface{}{
		"on": map[string]bool{"on": !light.State.On},
	})
}

// SetLightBrightness sets the brightness of a light (1-254 for API compat)
func (c *Client) SetLightBrightness(id string, brightness int) error {
	// Clamp to valid range
	if brightness < 1 {
		brightness = 1
	}
	if brightness > 254 {
		brightness = 254
	}
	// Convert to percentage for V2 API
	pct := float64(brightness) * 100 / 254
	return c.doV2Put("light", id, map[string]interface{}{
		"on":      map[string]bool{"on": true},
		"dimming": map[string]float64{"brightness": pct},
	})
}

// SetLightColor sets the color of a light using hue and saturation
func (c *Client) SetLightColor(id string, hue, saturation int) error {
	// V2 API uses XY color space, not hue/sat
	// For now, we'll need to convert or use a different approach
	// This is a simplified implementation
	return c.SetLightState(id, map[string]interface{}{
		"on":  true,
		"hue": hue,
		"sat": saturation,
	})
}

// SetLightColorTemp sets the color temperature of a light (153-500 mireds)
func (c *Client) SetLightColorTemp(id string, ct int) error {
	if ct < 153 {
		ct = 153
	}
	if ct > 500 {
		ct = 500
	}
	return c.doV2Put("light", id, map[string]interface{}{
		"on":                map[string]bool{"on": true},
		"color_temperature": map[string]int{"mirek": ct},
	})
}

// getGroupedLights fetches all grouped_light resources and returns a map by owner ID
func (c *Client) getGroupedLights() (map[string]*v2GroupedLight, error) {
	body, err := c.doV2Get("grouped_light")
	if err != nil {
		return nil, err
	}

	v2gls, err := parseV2Response[v2GroupedLight](body)
	if err != nil {
		return nil, err
	}

	result := make(map[string]*v2GroupedLight)
	for i := range v2gls {
		gl := v2gls[i]
		if gl.Owner != nil {
			result[gl.Owner.RID] = &gl
		}
	}
	return result, nil
}

// GetGroups returns all groups (rooms, zones, entertainment areas) from the bridge
func (c *Client) GetGroups() ([]*Group, error) {
	// Fetch rooms, zones, and entertainment configurations
	var allGroups []*Group

	// Get grouped_light resources for state info
	groupedLights, err := c.getGroupedLights()
	if err != nil {
		return nil, err
	}

	// Get all lights for mapping
	lights, err := c.GetLights()
	if err != nil {
		return nil, err
	}
	lightMap := make(map[string]*Light)
	for _, l := range lights {
		lightMap[l.ID] = l
	}

	// Get devices to map device->light relationships
	devicesBody, err := c.doV2Get("device")
	if err != nil {
		return nil, err
	}
	v2devices, err := parseV2Response[v2Device](devicesBody)
	if err != nil {
		return nil, err
	}
	deviceToLights := make(map[string][]string)
	for _, d := range v2devices {
		for _, svc := range d.Services {
			if svc.RType == "light" {
				deviceToLights[d.ID] = append(deviceToLights[d.ID], svc.RID)
			}
		}
	}

	// Get rooms
	roomsBody, err := c.doV2Get("room")
	if err != nil {
		return nil, err
	}
	v2rooms, err := parseV2Response[v2Room](roomsBody)
	if err != nil {
		return nil, err
	}

	for _, r := range v2rooms {
		group := &Group{
			ID:    r.ID,
			Name:  r.Metadata.Name,
			Type:  "Room",
			Class: r.Metadata.Archetype,
		}

		// Collect light IDs from child devices
		for _, child := range r.Children {
			if child.RType == "device" {
				if lightIDs, ok := deviceToLights[child.RID]; ok {
					group.Lights = append(group.Lights, lightIDs...)
				}
			}
		}

		// Find grouped_light service for this room
		for _, svc := range r.Services {
			if svc.RType == "grouped_light" {
				group.GroupedLightID = svc.RID
				if gl, ok := groupedLights[r.ID]; ok {
					group.State.AnyOn = gl.On.On
					group.State.AllOn = gl.On.On // Simplified - V2 doesn't have all_on
					if gl.Dimming != nil {
						group.Action.Brightness = int(gl.Dimming.Brightness * 254 / 100)
					}
				}
				break
			}
		}

		allGroups = append(allGroups, group)
	}

	// Get zones
	zonesBody, err := c.doV2Get("zone")
	if err != nil {
		return nil, err
	}
	v2zones, err := parseV2Response[v2Zone](zonesBody)
	if err != nil {
		return nil, err
	}

	for _, z := range v2zones {
		group := &Group{
			ID:    z.ID,
			Name:  z.Metadata.Name,
			Type:  "Zone",
			Class: z.Metadata.Archetype,
		}

		// Zones reference lights directly
		for _, child := range z.Children {
			if child.RType == "light" {
				group.Lights = append(group.Lights, child.RID)
			}
		}

		// Find grouped_light service
		for _, svc := range z.Services {
			if svc.RType == "grouped_light" {
				group.GroupedLightID = svc.RID
				if gl, ok := groupedLights[z.ID]; ok {
					group.State.AnyOn = gl.On.On
					group.State.AllOn = gl.On.On
					if gl.Dimming != nil {
						group.Action.Brightness = int(gl.Dimming.Brightness * 254 / 100)
					}
				}
				break
			}
		}

		allGroups = append(allGroups, group)
	}

	// Get entertainment configurations
	entBody, err := c.doV2Get("entertainment_configuration")
	if err != nil {
		// Entertainment might not be available, continue without it
		return allGroups, nil
	}
	v2ents, err := parseV2Response[v2EntertainmentConfig](entBody)
	if err != nil {
		return allGroups, nil
	}

	for _, e := range v2ents {
		// Extract light IDs from light_services or channels
		var lightIDs []string

		// Try light_services first (direct light references)
		for _, ls := range e.LightServices {
			if ls.RType == "light" {
				lightIDs = append(lightIDs, ls.RID)
			}
		}

		// Also check channels for entertainment service references
		// (entertainment services are linked to lights, but we'd need another API call)
		// For now, light_services should be sufficient

		group := &Group{
			ID:     e.ID,
			Name:   e.Metadata.Name,
			Type:   "Entertainment",
			Class:  e.ConfigurationType,
			Lights: lightIDs,
			Stream: &EntertainmentStream{
				Active: e.Status == "active",
			},
		}
		allGroups = append(allGroups, group)
	}

	return allGroups, nil
}

// GetRooms returns only Room type groups
func (c *Client) GetRooms() ([]*Group, error) {
	groups, err := c.GetGroups()
	if err != nil {
		return nil, err
	}

	rooms := make([]*Group, 0)
	for _, g := range groups {
		if g.Type == "Room" {
			rooms = append(rooms, g)
		}
	}

	return rooms, nil
}

// GetZones returns only Zone type groups
func (c *Client) GetZones() ([]*Group, error) {
	groups, err := c.GetGroups()
	if err != nil {
		return nil, err
	}

	zones := make([]*Group, 0)
	for _, g := range groups {
		if g.Type == "Zone" {
			zones = append(zones, g)
		}
	}

	return zones, nil
}

// SetGroupState sets the state of all lights in a group via grouped_light
func (c *Client) SetGroupState(id string, state map[string]interface{}) error {
	// First, find the grouped_light ID for this group
	groups, err := c.GetGroups()
	if err != nil {
		return err
	}

	var groupedLightID string
	for _, g := range groups {
		if g.ID == id {
			groupedLightID = g.GroupedLightID
			break
		}
	}

	if groupedLightID == "" {
		return fmt.Errorf("group %s not found or has no grouped_light service", id)
	}

	// Convert state to V2 format
	v2state := make(map[string]interface{})

	if on, ok := state["on"].(bool); ok {
		v2state["on"] = map[string]bool{"on": on}
	}

	if bri, ok := state["bri"].(int); ok {
		brightness := float64(bri) * 100 / 254
		v2state["dimming"] = map[string]float64{"brightness": brightness}
	}

	// Handle scene activation - in V2, this is done via scene resource directly
	if sceneID, ok := state["scene"].(string); ok {
		return c.ActivateScene(sceneID)
	}

	return c.doV2Put("grouped_light", groupedLightID, v2state)
}

// TurnOnGroup turns on all lights in a group
func (c *Client) TurnOnGroup(id string) error {
	return c.SetGroupState(id, map[string]interface{}{"on": true})
}

// TurnOffGroup turns off all lights in a group
func (c *Client) TurnOffGroup(id string) error {
	return c.SetGroupState(id, map[string]interface{}{"on": false})
}

// ToggleGroup toggles all lights in a group on/off
func (c *Client) ToggleGroup(id string) error {
	groups, err := c.GetGroups()
	if err != nil {
		return err
	}

	for _, g := range groups {
		if g.ID == id {
			return c.SetGroupState(id, map[string]interface{}{"on": !g.State.AnyOn})
		}
	}

	return fmt.Errorf("group %s not found", id)
}

// SetGroupBrightness sets the brightness for all lights in a group
func (c *Client) SetGroupBrightness(id string, brightness int) error {
	if brightness < 1 {
		brightness = 1
	}
	if brightness > 254 {
		brightness = 254
	}
	return c.SetGroupState(id, map[string]interface{}{
		"on":  true,
		"bri": brightness,
	})
}

// GetScenes returns all scenes from the bridge
func (c *Client) GetScenes() ([]*Scene, error) {
	body, err := c.doV2Get("scene")
	if err != nil {
		return nil, err
	}

	v2scenes, err := parseV2Response[v2Scene](body)
	if err != nil {
		return nil, err
	}

	scenes := make([]*Scene, 0, len(v2scenes))
	for _, s := range v2scenes {
		scene := &Scene{
			ID:     s.ID,
			Name:   s.Metadata.Name,
			Type:   "GroupScene",
			Group:  s.Group.RID,
			Active: s.Status.Active == "static" || s.Status.Active == "dynamic_palette",
		}

		// Extract light IDs from actions
		for _, action := range s.Actions {
			if action.Target.RType == "light" {
				scene.Lights = append(scene.Lights, action.Target.RID)
			}
		}

		if s.Metadata.Image != nil {
			scene.Image = s.Metadata.Image.RID
		}

		scenes = append(scenes, scene)
	}

	return scenes, nil
}

// GetScenesForGroup returns scenes for a specific group
func (c *Client) GetScenesForGroup(groupID string) ([]*Scene, error) {
	scenes, err := c.GetScenes()
	if err != nil {
		return nil, err
	}

	groupScenes := make([]*Scene, 0)
	for _, s := range scenes {
		if s.Group == groupID {
			groupScenes = append(groupScenes, s)
		}
	}

	return groupScenes, nil
}

// ActivateScene activates a scene using V2 API (direct scene recall)
func (c *Client) ActivateScene(sceneID string) error {
	// V2: PUT to scene with recall action
	return c.doV2Put("scene", sceneID, map[string]interface{}{
		"recall": map[string]string{"action": "active"},
	})
}

// DeactivateAllEntertainment deactivates streaming on all entertainment areas
func (c *Client) DeactivateAllEntertainment() error {
	body, err := c.doV2Get("entertainment_configuration")
	if err != nil {
		return err
	}

	v2ents, err := parseV2Response[v2EntertainmentConfig](body)
	if err != nil {
		return err
	}

	for _, e := range v2ents {
		if e.Status == "active" {
			if err := c.doV2Put("entertainment_configuration", e.ID, map[string]interface{}{
				"action": "stop",
			}); err != nil {
				return fmt.Errorf("failed to deactivate entertainment area %s: %w", e.ID, err)
			}
		}
	}
	return nil
}

// ActivateEntertainmentArea sets an entertainment area as active for streaming
func (c *Client) ActivateEntertainmentArea(groupID string) error {
	// First deactivate any currently active entertainment areas
	if err := c.DeactivateAllEntertainment(); err != nil {
		// Log but continue
	}

	// Activate the requested area
	return c.doV2Put("entertainment_configuration", groupID, map[string]interface{}{
		"action": "start",
	})
}

// GetRoomsWithDetails returns rooms with their lights and scenes populated
func (c *Client) GetRoomsWithDetails() ([]*Room, error) {
	groups, err := c.GetGroups()
	if err != nil {
		return nil, err
	}

	lights, err := c.GetLights()
	if err != nil {
		return nil, err
	}

	scenes, err := c.GetScenes()
	if err != nil {
		return nil, err
	}

	// Get grouped_light states
	groupedLights, err := c.getGroupedLights()
	if err != nil {
		return nil, err
	}

	// Create light lookup by ID
	lightMap := make(map[string]*Light)
	for _, l := range lights {
		lightMap[l.ID] = l
	}

	// Filter for rooms, zones, and entertainment areas
	rooms := make([]*Room, 0)
	for _, g := range groups {
		if g.Type != "Room" && g.Type != "Zone" && g.Type != "Entertainment" {
			continue
		}

		room := &Room{
			ID:             g.ID,
			Name:           g.Name,
			Type:           g.Type,
			Class:          g.Class,
			IsOn:           g.State.AnyOn,
			Lights:         make([]*Light, 0),
			Scenes:         make([]*Scene, 0),
			GroupedLightID: g.GroupedLightID,
		}

		// Get brightness from grouped_light if available
		if gl, ok := groupedLights[g.ID]; ok {
			if gl.Dimming != nil {
				room.Brightness = int(gl.Dimming.Brightness)
			}
		}

		// For entertainment areas, check streaming status
		if g.Type == "Entertainment" && g.Stream != nil {
			room.StreamingActive = g.Stream.Active
		}

		// Add lights
		for _, lightID := range g.Lights {
			if light, ok := lightMap[lightID]; ok {
				room.Lights = append(room.Lights, light)
			}
		}

		// Add scenes for this room
		for _, s := range scenes {
			if s.Group == g.ID {
				room.Scenes = append(room.Scenes, s)
			}
		}

		rooms = append(rooms, room)
	}

	// Sort rooms: Rooms first, then Zones, then Entertainment, then by name
	sort.Slice(rooms, func(i, j int) bool {
		typeOrder := map[string]int{"Room": 0, "Zone": 1, "Entertainment": 2}
		if typeOrder[rooms[i].Type] != typeOrder[rooms[j].Type] {
			return typeOrder[rooms[i].Type] < typeOrder[rooms[j].Type]
		}
		return rooms[i].Name < rooms[j].Name
	})

	return rooms, nil
}
