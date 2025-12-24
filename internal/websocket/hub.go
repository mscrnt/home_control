package websocket

import (
	"encoding/json"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

const (
	// Time allowed to write a message to the peer
	writeWait = 10 * time.Second
	// Time allowed to read the next pong message from the peer
	pongWait = 60 * time.Second
	// Send pings to peer with this period (must be less than pongWait)
	pingPeriod = 30 * time.Second
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin: func(r *http.Request) bool {
		return true // Allow all origins for local network use
	},
}

// Event represents a WebSocket event
type Event struct {
	Type    string      `json:"type"`
	Payload interface{} `json:"payload,omitempty"`
}

// Client represents a WebSocket client
type Client struct {
	hub        *Hub
	conn       *websocket.Conn
	send       chan []byte
	remoteAddr string
}

// Hub manages WebSocket connections
type Hub struct {
	clients    map[*Client]bool
	broadcast  chan []byte
	register   chan *Client
	unregister chan *Client
	mu         sync.RWMutex
}

// NewHub creates a new WebSocket hub
func NewHub() *Hub {
	return &Hub{
		clients:    make(map[*Client]bool),
		broadcast:  make(chan []byte, 256),
		register:   make(chan *Client),
		unregister: make(chan *Client),
	}
}

// Run starts the hub's main loop
func (h *Hub) Run() {
	for {
		select {
		case client := <-h.register:
			h.mu.Lock()
			h.clients[client] = true
			count := len(h.clients)
			h.mu.Unlock()
			log.Printf("WebSocket: Client connected from %s (total: %d)", client.remoteAddr, count)

		case client := <-h.unregister:
			h.mu.Lock()
			if _, ok := h.clients[client]; ok {
				delete(h.clients, client)
				close(client.send)
			}
			count := len(h.clients)
			h.mu.Unlock()
			log.Printf("WebSocket: Client disconnected from %s (total: %d)", client.remoteAddr, count)

		case message := <-h.broadcast:
			h.mu.RLock()
			for client := range h.clients {
				select {
				case client.send <- message:
				default:
					close(client.send)
					delete(h.clients, client)
				}
			}
			h.mu.RUnlock()
		}
	}
}

// Broadcast sends a message to all connected clients
func (h *Hub) Broadcast(event Event) {
	data, err := json.Marshal(event)
	if err != nil {
		log.Printf("WebSocket: Failed to marshal event: %v", err)
		return
	}
	h.mu.RLock()
	count := len(h.clients)
	h.mu.RUnlock()
	log.Printf("WebSocket: Broadcasting '%s' to %d client(s)", event.Type, count)
	h.broadcast <- data
}

// BroadcastDoorbell sends a doorbell event to all clients
func (h *Hub) BroadcastDoorbell(cameraName string) {
	h.Broadcast(Event{
		Type: "doorbell",
		Payload: map[string]string{
			"camera": cameraName,
		},
	})
	log.Printf("Broadcasted doorbell event for camera: %s", cameraName)
}

// ClientCount returns the number of connected clients
func (h *Hub) ClientCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients)
}

// ServeWS handles WebSocket requests
func (h *Hub) ServeWS(w http.ResponseWriter, r *http.Request) {
	remoteAddr := r.RemoteAddr
	log.Printf("WebSocket: Upgrade request from %s (User-Agent: %s)", remoteAddr, r.UserAgent())

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("WebSocket: Upgrade failed from %s: %v", remoteAddr, err)
		return
	}

	client := &Client{
		hub:        h,
		conn:       conn,
		send:       make(chan []byte, 256),
		remoteAddr: remoteAddr,
	}
	h.register <- client

	// Send initial connection success
	event := Event{Type: "connected"}
	if data, err := json.Marshal(event); err == nil {
		client.send <- data
	}

	go client.writePump()
	go client.readPump()
}

// writePump sends messages to the WebSocket connection
func (c *Client) writePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		c.conn.Close()
	}()

	for {
		select {
		case message, ok := <-c.send:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}
			if err := c.conn.WriteMessage(websocket.TextMessage, message); err != nil {
				return
			}
		case <-ticker.C:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

// readPump reads messages from the WebSocket connection
func (c *Client) readPump() {
	defer func() {
		c.hub.unregister <- c
		c.conn.Close()
	}()

	c.conn.SetReadLimit(512)
	c.conn.SetReadDeadline(time.Now().Add(pongWait))
	c.conn.SetPongHandler(func(string) error {
		c.conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})

	for {
		_, _, err := c.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				log.Printf("WebSocket error: %v", err)
			}
			break
		}
		// Reset read deadline on any message (keepalive from client)
		c.conn.SetReadDeadline(time.Now().Add(pongWait))
	}
}
