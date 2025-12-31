package hue

import (
	"bufio"
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"strings"
	"sync"
	"time"
)

// EventType represents the type of Hue event
type EventType string

const (
	EventTypeLight  EventType = "light"
	EventTypeRoom   EventType = "room"
	EventTypeScene  EventType = "scene"
	EventTypeGroup  EventType = "grouped_light"
	EventTypeUpdate EventType = "update"
)

// HueEvent represents a parsed event from the Hue bridge
type HueEvent struct {
	Type      EventType              `json:"type"`
	ID        string                 `json:"id"`
	IDV1      string                 `json:"id_v1,omitempty"`
	Owner     *ResourceRef           `json:"owner,omitempty"`
	Data      map[string]interface{} `json:"data"`
	Timestamp time.Time              `json:"timestamp"`
}

// EventCallback is called when a Hue event is received
type EventCallback func(event *HueEvent)

// EventStream connects to the Hue bridge SSE endpoint and forwards events
type EventStream struct {
	client       *Client
	callbacks    []EventCallback
	callbacksMu  sync.RWMutex
	cancel       context.CancelFunc
	running      bool
	mu           sync.Mutex
	retryDelay   time.Duration
	maxRetryDelay time.Duration
}

// NewEventStream creates a new event stream handler
func NewEventStream(client *Client) *EventStream {
	return &EventStream{
		client:        client,
		callbacks:     make([]EventCallback, 0),
		retryDelay:    5 * time.Second,
		maxRetryDelay: 60 * time.Second,
	}
}

// Subscribe adds a callback to receive events
func (e *EventStream) Subscribe(callback EventCallback) {
	e.callbacksMu.Lock()
	defer e.callbacksMu.Unlock()
	e.callbacks = append(e.callbacks, callback)
}

// Start begins listening for events from the Hue bridge
func (e *EventStream) Start() error {
	e.mu.Lock()
	if e.running {
		e.mu.Unlock()
		return nil
	}
	e.running = true
	e.mu.Unlock()

	ctx, cancel := context.WithCancel(context.Background())
	e.cancel = cancel

	go e.eventLoop(ctx)
	return nil
}

// Stop stops the event stream
func (e *EventStream) Stop() {
	e.mu.Lock()
	defer e.mu.Unlock()

	if e.cancel != nil {
		e.cancel()
	}
	e.running = false
}

// IsRunning returns whether the event stream is active
func (e *EventStream) IsRunning() bool {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.running
}

func (e *EventStream) eventLoop(ctx context.Context) {
	currentDelay := e.retryDelay
	for {
		select {
		case <-ctx.Done():
			return
		default:
			if err := e.connect(ctx); err != nil {
				log.Printf("Hue event stream error: %v, reconnecting in %v...", err, currentDelay)
				select {
				case <-ctx.Done():
					return
				case <-time.After(currentDelay):
					// Exponential backoff with cap
					currentDelay = currentDelay * 2
					if currentDelay > e.maxRetryDelay {
						currentDelay = e.maxRetryDelay
					}
					continue
				}
			} else {
				// Connection was successful and closed normally, reset delay
				currentDelay = e.retryDelay
			}
		}
	}
}

func (e *EventStream) connect(ctx context.Context) error {
	url := fmt.Sprintf("https://%s/eventstream/clip/v2", e.client.bridgeIP)

	// Create custom transport that skips TLS verification (self-signed cert on bridge)
	tr := &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}
	httpClient := &http.Client{
		Transport: tr,
		Timeout:   0, // No timeout for SSE
	}

	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("hue-application-key", e.client.appKey)
	req.Header.Set("Accept", "text/event-stream")

	resp, err := httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to connect: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("unexpected status: %d", resp.StatusCode)
	}

	log.Printf("Connected to Hue event stream")

	scanner := bufio.NewScanner(resp.Body)
	var dataBuffer strings.Builder

	for scanner.Scan() {
		select {
		case <-ctx.Done():
			return nil
		default:
		}

		line := scanner.Text()

		// SSE format: lines starting with "data:" contain JSON
		if strings.HasPrefix(line, "data:") {
			data := strings.TrimPrefix(line, "data:")
			data = strings.TrimSpace(data)
			dataBuffer.WriteString(data)
		} else if line == "" && dataBuffer.Len() > 0 {
			// Empty line means end of message
			e.processEvent(dataBuffer.String())
			dataBuffer.Reset()
		} else if strings.HasPrefix(line, ": ") {
			// Comment/keep-alive, ignore
			continue
		}
	}

	if err := scanner.Err(); err != nil {
		return fmt.Errorf("stream read error: %w", err)
	}

	return nil
}

func (e *EventStream) processEvent(data string) {
	// Parse the JSON array of events
	var rawEvents []json.RawMessage
	if err := json.Unmarshal([]byte(data), &rawEvents); err != nil {
		// Try as single object
		var singleEvent map[string]interface{}
		if err2 := json.Unmarshal([]byte(data), &singleEvent); err2 != nil {
			log.Printf("Failed to parse Hue event: %v", err)
			return
		}
		e.handleRawEvent(singleEvent)
		return
	}

	for _, rawEvent := range rawEvents {
		var event map[string]interface{}
		if err := json.Unmarshal(rawEvent, &event); err != nil {
			continue
		}
		e.handleRawEvent(event)
	}
}

func (e *EventStream) handleRawEvent(event map[string]interface{}) {
	// Extract event type and data
	eventType, _ := event["type"].(string)
	dataArray, _ := event["data"].([]interface{})

	if len(dataArray) == 0 {
		return
	}

	for _, item := range dataArray {
		itemMap, ok := item.(map[string]interface{})
		if !ok {
			continue
		}

		id, _ := itemMap["id"].(string)
		itemType, _ := itemMap["type"].(string)
		idV1, _ := itemMap["id_v1"].(string)

		hueEvent := &HueEvent{
			Type:      EventType(itemType),
			ID:        id,
			IDV1:      idV1,
			Data:      itemMap,
			Timestamp: time.Now(),
		}

		// Handle owner if present
		if owner, ok := itemMap["owner"].(map[string]interface{}); ok {
			hueEvent.Owner = &ResourceRef{
				RID:   owner["rid"].(string),
				RType: owner["rtype"].(string),
			}
		}

		// Check if this is an update event
		if eventType == "update" {
			hueEvent.Type = EventTypeUpdate
		}

		// Notify all subscribers
		e.callbacksMu.RLock()
		for _, cb := range e.callbacks {
			cb(hueEvent)
		}
		e.callbacksMu.RUnlock()
	}
}

// GetBridgeIP returns the bridge IP for external use
func (e *EventStream) GetBridgeIP() string {
	return e.client.bridgeIP
}

// GetAppKey returns the app key for external use
func (e *EventStream) GetAppKey() string {
	return e.client.appKey
}
