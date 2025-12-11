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

// Client represents a Philips Hue Bridge API client
type Client struct {
	bridgeIP   string
	username   string
	httpClient *http.Client
}

// Light represents a Hue light
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
	Brightness int       `json:"bri,omitempty"`  // 1-254
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
	ID      string               `json:"id"`
	Name    string               `json:"name"`
	Lights  []string             `json:"lights"`
	Type    string               `json:"type"` // Room, Zone, Entertainment, LightGroup
	Class   string               `json:"class,omitempty"`
	State   GroupState           `json:"state"`
	Action  LightState           `json:"action"`
	Stream  *EntertainmentStream `json:"stream,omitempty"`
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
}

// Room is a convenience type for rooms/zones that includes lights
type Room struct {
	ID              string   `json:"id"`
	Name            string   `json:"name"`
	Type            string   `json:"type"`
	Class           string   `json:"class"`
	IsOn            bool     `json:"isOn"`
	Lights          []*Light `json:"lights"`
	Scenes          []*Scene `json:"scenes"`
	StreamingActive bool     `json:"streamingActive,omitempty"`
}

// NewClient creates a new Hue Bridge client
func NewClient(bridgeIP, username string) *Client {
	// Skip TLS verification for local Hue bridge
	tr := &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}
	return &Client{
		bridgeIP: bridgeIP,
		username: username,
		httpClient: &http.Client{
			Timeout:   10 * time.Second,
			Transport: tr,
		},
	}
}

func (c *Client) apiURL(path string) string {
	return fmt.Sprintf("https://%s/api/%s%s", c.bridgeIP, c.username, path)
}

func (c *Client) get(path string) ([]byte, error) {
	resp, err := c.httpClient.Get(c.apiURL(path))
	if err != nil {
		return nil, fmt.Errorf("hue API request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	return body, nil
}

func (c *Client) put(path string, data interface{}) error {
	jsonData, err := json.Marshal(data)
	if err != nil {
		return fmt.Errorf("failed to marshal request: %w", err)
	}

	req, err := http.NewRequest("PUT", c.apiURL(path), bytes.NewBuffer(jsonData))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("hue API request failed: %w", err)
	}
	defer resp.Body.Close()

	// Check for errors in response
	body, _ := io.ReadAll(resp.Body)
	var result []map[string]interface{}
	if err := json.Unmarshal(body, &result); err == nil {
		for _, item := range result {
			if errMsg, ok := item["error"]; ok {
				return fmt.Errorf("hue API error: %v", errMsg)
			}
		}
	}

	return nil
}

// GetLights returns all lights from the bridge
func (c *Client) GetLights() ([]*Light, error) {
	body, err := c.get("/lights")
	if err != nil {
		return nil, err
	}

	var lightsMap map[string]json.RawMessage
	if err := json.Unmarshal(body, &lightsMap); err != nil {
		return nil, fmt.Errorf("failed to parse lights: %w", err)
	}

	lights := make([]*Light, 0, len(lightsMap))
	for id, data := range lightsMap {
		var light Light
		if err := json.Unmarshal(data, &light); err != nil {
			continue
		}
		light.ID = id
		lights = append(lights, &light)
	}

	return lights, nil
}

// GetLight returns a single light by ID
func (c *Client) GetLight(id string) (*Light, error) {
	body, err := c.get("/lights/" + id)
	if err != nil {
		return nil, err
	}

	var light Light
	if err := json.Unmarshal(body, &light); err != nil {
		return nil, fmt.Errorf("failed to parse light: %w", err)
	}
	light.ID = id

	return &light, nil
}

// SetLightState sets the state of a light
func (c *Client) SetLightState(id string, state map[string]interface{}) error {
	return c.put("/lights/"+id+"/state", state)
}

// TurnOnLight turns on a light
func (c *Client) TurnOnLight(id string) error {
	return c.SetLightState(id, map[string]interface{}{"on": true})
}

// TurnOffLight turns off a light
func (c *Client) TurnOffLight(id string) error {
	return c.SetLightState(id, map[string]interface{}{"on": false})
}

// ToggleLight toggles a light on/off
func (c *Client) ToggleLight(id string) error {
	light, err := c.GetLight(id)
	if err != nil {
		return err
	}
	return c.SetLightState(id, map[string]interface{}{"on": !light.State.On})
}

// SetLightBrightness sets the brightness of a light (1-254)
func (c *Client) SetLightBrightness(id string, brightness int) error {
	if brightness < 1 {
		brightness = 1
	}
	if brightness > 254 {
		brightness = 254
	}
	return c.SetLightState(id, map[string]interface{}{
		"on":  true,
		"bri": brightness,
	})
}

// SetLightColor sets the color of a light using hue and saturation
func (c *Client) SetLightColor(id string, hue, saturation int) error {
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
	return c.SetLightState(id, map[string]interface{}{
		"on": true,
		"ct": ct,
	})
}

// GetGroups returns all groups from the bridge
func (c *Client) GetGroups() ([]*Group, error) {
	body, err := c.get("/groups")
	if err != nil {
		return nil, err
	}

	var groupsMap map[string]json.RawMessage
	if err := json.Unmarshal(body, &groupsMap); err != nil {
		return nil, fmt.Errorf("failed to parse groups: %w", err)
	}

	groups := make([]*Group, 0, len(groupsMap))
	for id, data := range groupsMap {
		var group Group
		if err := json.Unmarshal(data, &group); err != nil {
			continue
		}
		group.ID = id
		groups = append(groups, &group)
	}

	return groups, nil
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

// SetGroupState sets the state of all lights in a group
func (c *Client) SetGroupState(id string, state map[string]interface{}) error {
	return c.put("/groups/"+id+"/action", state)
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
			// If any light is on, turn all off; otherwise turn all on
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
	body, err := c.get("/scenes")
	if err != nil {
		return nil, err
	}

	var scenesMap map[string]json.RawMessage
	if err := json.Unmarshal(body, &scenesMap); err != nil {
		return nil, fmt.Errorf("failed to parse scenes: %w", err)
	}

	scenes := make([]*Scene, 0, len(scenesMap))
	for id, data := range scenesMap {
		var scene Scene
		if err := json.Unmarshal(data, &scene); err != nil {
			continue
		}
		scene.ID = id
		scenes = append(scenes, &scene)
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

// ActivateScene activates a scene
func (c *Client) ActivateScene(sceneID string) error {
	// First get the scene to find its group
	scenes, err := c.GetScenes()
	if err != nil {
		return err
	}

	for _, s := range scenes {
		if s.ID == sceneID {
			return c.SetGroupState(s.Group, map[string]interface{}{"scene": sceneID})
		}
	}

	return fmt.Errorf("scene %s not found", sceneID)
}

// DeactivateAllEntertainment deactivates streaming on all entertainment areas
func (c *Client) DeactivateAllEntertainment() error {
	groups, err := c.GetGroups()
	if err != nil {
		return err
	}

	for _, g := range groups {
		if g.Type == "Entertainment" && g.Stream != nil && g.Stream.Active {
			// Deactivate this entertainment area
			if err := c.put("/groups/"+g.ID, map[string]interface{}{
				"stream": map[string]interface{}{
					"active": false,
				},
			}); err != nil {
				return fmt.Errorf("failed to deactivate entertainment area %s: %w", g.ID, err)
			}
		}
	}
	return nil
}

// ActivateEntertainmentArea sets an entertainment area as active for streaming
func (c *Client) ActivateEntertainmentArea(groupID string) error {
	// First deactivate any currently active entertainment areas
	if err := c.DeactivateAllEntertainment(); err != nil {
		// Log but continue - the area we want might still activate
	}

	// Activate streaming on the requested area
	// This uses PUT to /groups/{id} with stream.active = true
	return c.put("/groups/"+groupID, map[string]interface{}{
		"stream": map[string]interface{}{
			"active": true,
		},
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

	// Create light lookup
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
			ID:     g.ID,
			Name:   g.Name,
			Type:   g.Type,
			Class:  g.Class,
			IsOn:   g.State.AnyOn,
			Lights: make([]*Light, 0),
			Scenes: make([]*Scene, 0),
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
