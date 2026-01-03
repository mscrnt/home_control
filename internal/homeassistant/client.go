package homeassistant

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
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
