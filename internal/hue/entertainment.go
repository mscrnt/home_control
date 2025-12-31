package hue

import (
	"fmt"
	"log"
	"sync"
)

// EntertainmentStreamer manages entertainment area selection for Hue Sync Box
// Note: This doesn't stream colors itself - it just selects which area the Sync Box uses
type EntertainmentStreamer struct {
	client     *Client // REST API client for activation
	activeArea string
	mu         sync.Mutex
}

// NewEntertainmentStreamer creates a new entertainment streamer
func NewEntertainmentStreamer(client *Client) *EntertainmentStreamer {
	return &EntertainmentStreamer{
		client: client,
	}
}

// GetActiveArea returns the currently active entertainment area ID
func (e *EntertainmentStreamer) GetActiveArea() string {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.activeArea
}

// IsStreaming returns true if an entertainment area is active
func (e *EntertainmentStreamer) IsStreaming() bool {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.activeArea != ""
}

// SelectArea marks an area as selected in the kiosk UI (informational only)
// Actual streaming control should be done via the Sync Box API
func (e *EntertainmentStreamer) SelectArea(areaID string) error {
	e.mu.Lock()
	defer e.mu.Unlock()

	// Validate the area exists
	groups, err := e.client.GetGroups()
	if err != nil {
		return fmt.Errorf("failed to get groups: %w", err)
	}

	var area *Group
	for _, g := range groups {
		if g.ID == areaID && g.Type == "Entertainment" {
			area = g
			break
		}
	}
	if area == nil {
		return fmt.Errorf("entertainment area %s not found", areaID)
	}

	log.Printf("Selected entertainment area: %s (%s)", areaID, area.Name)
	e.activeArea = areaID
	return nil
}

// Deactivate deactivates the current entertainment area
func (e *EntertainmentStreamer) Deactivate() error {
	e.mu.Lock()
	defer e.mu.Unlock()

	if e.activeArea == "" {
		return nil
	}

	// Deactivate via V2 REST API
	err := e.client.doV2Put("entertainment_configuration", e.activeArea, map[string]interface{}{
		"action": "stop",
	})

	log.Printf("Entertainment area %s deactivated", e.activeArea)
	e.activeArea = ""
	return err
}
