package mqtt

import (
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"sync"
	"time"

	paho "github.com/eclipse/paho.mqtt.golang"
)

// DoorbellHandler is called when doorbell is pressed
type DoorbellHandler func()

// Client handles MQTT connections for doorbell events
type Client struct {
	client          paho.Client
	doorbellHandler DoorbellHandler
	mu              sync.RWMutex
	connected       bool
	customTopics    []string
}

// Config holds MQTT connection settings
type Config struct {
	Host           string
	Port           int
	Username       string
	Password       string
	ClientID       string
	DoorbellTopics []string // Custom doorbell topics (optional)
}

// NewClient creates a new MQTT client
func NewClient(cfg Config) *Client {
	c := &Client{
		customTopics: cfg.DoorbellTopics,
	}

	opts := paho.NewClientOptions()
	opts.AddBroker(fmt.Sprintf("tcp://%s:%d", cfg.Host, cfg.Port))
	opts.SetClientID(cfg.ClientID)
	opts.SetUsername(cfg.Username)
	opts.SetPassword(cfg.Password)
	opts.SetAutoReconnect(true)
	opts.SetConnectRetry(true)
	opts.SetConnectRetryInterval(5 * time.Second)
	opts.SetKeepAlive(30 * time.Second)

	opts.SetOnConnectHandler(func(client paho.Client) {
		log.Println("MQTT connected")
		c.mu.Lock()
		c.connected = true
		c.mu.Unlock()
		c.subscribeToDoorbellTopics()
	})

	opts.SetConnectionLostHandler(func(client paho.Client, err error) {
		log.Printf("MQTT connection lost: %v", err)
		c.mu.Lock()
		c.connected = false
		c.mu.Unlock()
	})

	c.client = paho.NewClient(opts)
	return c
}

// Connect starts the MQTT connection
func (c *Client) Connect() error {
	token := c.client.Connect()
	token.Wait()
	if err := token.Error(); err != nil {
		return fmt.Errorf("MQTT connect failed: %w", err)
	}
	return nil
}

// SetDoorbellHandler sets the callback for doorbell events
func (c *Client) SetDoorbellHandler(handler DoorbellHandler) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.doorbellHandler = handler
}

// subscribeToDoorbellTopics subscribes to known doorbell MQTT topics
func (c *Client) subscribeToDoorbellTopics() {
	var topics []string

	// Use custom topics if provided, otherwise use defaults
	if len(c.customTopics) > 0 {
		topics = c.customTopics
	} else {
		// Common Amcrest doorbell MQTT topics
		topics = []string{
			"amcrest2mqtt/+/doorbell",                    // amcrest2mqtt integration
			"amcrest2mqtt/+/event",                       // general events
			"amcrest2mqtt/+/button",                      // button press events
			"homeassistant/binary_sensor/+/doorbell/state", // HA MQTT discovery
			"doorbell/+/pressed",                         // generic doorbell topic
			"amcrest/+/button",                           // direct amcrest
		}
	}

	for _, topic := range topics {
		token := c.client.Subscribe(topic, 1, c.handleDoorbellMessage)
		token.Wait()
		if err := token.Error(); err != nil {
			log.Printf("Failed to subscribe to %s: %v", topic, err)
		} else {
			log.Printf("Subscribed to MQTT topic: %s", topic)
		}
	}

	// Subscribe to wildcard topics to help debug (logs ALL MQTT messages)
	debugTopics := []string{
		"amcrest2mqtt/#",     // Amcrest MQTT bridge
		"amcrest/#",          // Direct Amcrest
		"homeassistant/#",    // Home Assistant discovery
		"zigbee2mqtt/#",      // Zigbee devices
	}
	for _, topic := range debugTopics {
		debugToken := c.client.Subscribe(topic, 1, c.handleDebugMessage)
		debugToken.Wait()
		if err := debugToken.Error(); err != nil {
			log.Printf("Failed to subscribe to debug topic %s: %v", topic, err)
		} else {
			log.Printf("Subscribed to debug topic: %s", topic)
		}
	}
}

// handleDebugMessage logs all MQTT messages for debugging
func (c *Client) handleDebugMessage(client paho.Client, msg paho.Message) {
	log.Printf("MQTT DEBUG: topic=%s payload=%s", msg.Topic(), string(msg.Payload()))
}

// handleDoorbellMessage processes incoming doorbell MQTT messages
func (c *Client) handleDoorbellMessage(client paho.Client, msg paho.Message) {
	log.Printf("MQTT doorbell topic message: topic=%s payload=%s", msg.Topic(), string(msg.Payload()))

	// Check if this is a doorbell press event
	payload := string(msg.Payload())

	// Match various doorbell press payloads
	isDoorbellPress := false
	switch payload {
	case "on", "ON", "pressed", "1", "true", "True", "TRUE":
		isDoorbellPress = true
	}

	// Also check if the topic itself indicates doorbell press (for topics that don't use payload)
	topic := msg.Topic()
	if strings.Contains(topic, "doorbell") || strings.Contains(topic, "button") {
		// For these topics, any message might indicate a press
		if payload != "off" && payload != "OFF" && payload != "0" && payload != "false" && payload != "False" {
			isDoorbellPress = true
		}
	}

	// Check for Amcrest event format (CallNoAnswered = doorbell pressed but not answered)
	if strings.Contains(topic, "event") {
		// Try to parse JSON payload for Amcrest events
		var eventData map[string]interface{}
		if err := json.Unmarshal(msg.Payload(), &eventData); err == nil {
			// Check for CallNoAnswered event (doorbell press)
			if event, ok := eventData["event"].(string); ok {
				if event == "CallNoAnswered" {
					if payloadData, ok := eventData["payload"].(map[string]interface{}); ok {
						if action, ok := payloadData["action"].(string); ok && action == "Start" {
							log.Println("Amcrest CallNoAnswered event detected")
							isDoorbellPress = true
						}
					}
				}
			}
		}
		// Also check for plain text "CallNoAnswered"
		if strings.Contains(payload, "CallNoAnswered") {
			isDoorbellPress = true
		}
	}

	if isDoorbellPress {
		c.mu.RLock()
		handler := c.doorbellHandler
		c.mu.RUnlock()

		if handler != nil {
			log.Println("Doorbell pressed! Triggering handler...")
			handler()
		}
	}
}

// IsConnected returns the connection status
func (c *Client) IsConnected() bool {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.connected
}

// Disconnect closes the MQTT connection
func (c *Client) Disconnect() {
	c.client.Disconnect(250)
}
