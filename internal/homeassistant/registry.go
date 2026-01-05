package homeassistant

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"sync"
	"time"
)

// RegistryManager handles entity registry caching and updates
type RegistryManager struct {
	client       *Client
	registry     *EntityRegistry
	filePath     string
	mu           sync.RWMutex
	refreshTimer *time.Timer
}

// NewRegistryManager creates a new registry manager
func NewRegistryManager(client *Client, dataDir string) *RegistryManager {
	filePath := dataDir + "/ha_entities.json"
	rm := &RegistryManager{
		client:   client,
		filePath: filePath,
	}

	// Try to load cached registry
	if err := rm.loadFromFile(); err != nil {
		log.Printf("HA Registry: No cached registry found, will fetch from HA")
	} else {
		log.Printf("HA Registry: Loaded %d domains from cache", len(rm.registry.Domains))
	}

	return rm
}

// Start begins periodic refresh of the registry
func (rm *RegistryManager) Start(refreshInterval time.Duration) {
	// Initial fetch if no cache
	if rm.registry == nil {
		go rm.Refresh()
	}

	// Schedule periodic refresh
	rm.refreshTimer = time.AfterFunc(refreshInterval, func() {
		rm.Refresh()
		rm.Start(refreshInterval) // Reschedule
	})
}

// Stop stops the periodic refresh
func (rm *RegistryManager) Stop() {
	if rm.refreshTimer != nil {
		rm.refreshTimer.Stop()
	}
}

// Refresh fetches fresh data from Home Assistant and updates the cache
func (rm *RegistryManager) Refresh() error {
	log.Printf("HA Registry: Refreshing entity registry...")

	registry, err := rm.client.BuildEntityRegistry()
	if err != nil {
		log.Printf("HA Registry: Failed to refresh: %v", err)
		return err
	}

	rm.mu.Lock()
	rm.registry = registry
	rm.mu.Unlock()

	// Save to file
	if err := rm.saveToFile(); err != nil {
		log.Printf("HA Registry: Failed to save cache: %v", err)
	} else {
		totalEntities := 0
		for _, entities := range registry.Entities {
			totalEntities += len(entities)
		}
		log.Printf("HA Registry: Cached %d entities across %d domains", totalEntities, len(registry.Domains))
	}

	return nil
}

// GetRegistry returns the current entity registry
func (rm *RegistryManager) GetRegistry() *EntityRegistry {
	rm.mu.RLock()
	defer rm.mu.RUnlock()
	return rm.registry
}

// GetEntitiesByDomain returns entities for a specific domain
func (rm *RegistryManager) GetEntitiesByDomain(domain string) []*Entity {
	rm.mu.RLock()
	defer rm.mu.RUnlock()

	if rm.registry == nil {
		return nil
	}
	return rm.registry.Entities[domain]
}

// GetDomains returns the sorted list of available domains
func (rm *RegistryManager) GetDomains() []string {
	rm.mu.RLock()
	defer rm.mu.RUnlock()

	if rm.registry == nil {
		return nil
	}
	return rm.registry.Domains
}

// GetDomainsWithInfo returns domains with their metadata
func (rm *RegistryManager) GetDomainsWithInfo() []DomainWithEntities {
	rm.mu.RLock()
	defer rm.mu.RUnlock()

	if rm.registry == nil {
		return nil
	}

	result := make([]DomainWithEntities, 0, len(rm.registry.Domains))
	for _, domain := range rm.registry.Domains {
		result = append(result, DomainWithEntities{
			Domain:   domain,
			Info:     GetDomainInfo(domain),
			Entities: rm.registry.Entities[domain],
			Count:    len(rm.registry.Entities[domain]),
		})
	}
	return result
}

// DomainWithEntities combines domain info with its entities
type DomainWithEntities struct {
	Domain   string      `json:"domain"`
	Info     DomainInfo  `json:"info"`
	Entities []*Entity   `json:"entities"`
	Count    int         `json:"count"`
}

// loadFromFile loads the registry from the cache file
func (rm *RegistryManager) loadFromFile() error {
	data, err := os.ReadFile(rm.filePath)
	if err != nil {
		return err
	}

	var registry EntityRegistry
	if err := json.Unmarshal(data, &registry); err != nil {
		return err
	}

	// Check if cache is too old (more than 24 hours)
	if time.Since(registry.LastUpdate) > 24*time.Hour {
		return fmt.Errorf("cache is stale")
	}

	rm.registry = &registry
	return nil
}

// saveToFile saves the registry to the cache file
func (rm *RegistryManager) saveToFile() error {
	rm.mu.RLock()
	defer rm.mu.RUnlock()

	if rm.registry == nil {
		return fmt.Errorf("no registry to save")
	}

	data, err := json.MarshalIndent(rm.registry, "", "  ")
	if err != nil {
		return err
	}

	return os.WriteFile(rm.filePath, data, 0644)
}

// SearchEntities searches for entities matching a query across all domains
func (rm *RegistryManager) SearchEntities(query string) []*Entity {
	rm.mu.RLock()
	defer rm.mu.RUnlock()

	if rm.registry == nil || query == "" {
		return nil
	}

	var results []*Entity
	for _, entities := range rm.registry.Entities {
		for _, entity := range entities {
			// Search in entity_id and friendly_name
			if containsIgnoreCase(entity.EntityID, query) {
				results = append(results, entity)
				continue
			}
			if name, ok := entity.Attributes["friendly_name"].(string); ok {
				if containsIgnoreCase(name, query) {
					results = append(results, entity)
				}
			}
		}
	}
	return results
}

func containsIgnoreCase(s, substr string) bool {
	return len(s) >= len(substr) &&
		(s == substr ||
		 len(substr) == 0 ||
		 findIgnoreCase(s, substr))
}

func findIgnoreCase(s, substr string) bool {
	if len(substr) == 0 {
		return true
	}
	if len(s) < len(substr) {
		return false
	}

	// Simple case-insensitive contains
	sLower := toLower(s)
	substrLower := toLower(substr)

	for i := 0; i <= len(sLower)-len(substrLower); i++ {
		if sLower[i:i+len(substrLower)] == substrLower {
			return true
		}
	}
	return false
}

func toLower(s string) string {
	b := make([]byte, len(s))
	for i := 0; i < len(s); i++ {
		c := s[i]
		if c >= 'A' && c <= 'Z' {
			c += 'a' - 'A'
		}
		b[i] = c
	}
	return string(b)
}
