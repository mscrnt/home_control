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
