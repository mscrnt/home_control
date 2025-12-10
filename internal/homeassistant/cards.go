package homeassistant

import (
	"sort"
	"strings"
)

// CardType determines how to render an entity
type CardType string

const (
	CardTypeSensor   CardType = "sensor"
	CardTypeSwitch   CardType = "switch"
	CardTypeLight    CardType = "light"
	CardTypeClimate  CardType = "climate"
	CardTypeBinary   CardType = "binary_sensor"
	CardTypeCamera   CardType = "camera"
	CardTypeWeather  CardType = "weather"
	CardTypePerson   CardType = "person"
	CardTypeLock     CardType = "lock"
	CardTypeFan      CardType = "fan"
	CardTypeVacuum   CardType = "vacuum"
	CardTypeUnknown  CardType = "unknown"
)

// Card represents a rendered card for the UI
type Card struct {
	EntityID     string                 `json:"entityId"`
	Name         string                 `json:"name"`
	State        string                 `json:"state"`
	Icon         string                 `json:"icon"`
	Type         CardType               `json:"type"`
	Unit         string                 `json:"unit"`
	IsOn         bool                   `json:"isOn"`
	Group        string                 `json:"group"`
	Attributes   map[string]interface{} `json:"attributes"`
	IsLightGroup bool                   `json:"isLightGroup"` // True if this is a light group with member lights
	Members      []*Card                `json:"members"`      // Member lights for light groups
}

// CardGroup holds cards organized by group
type CardGroup struct {
	Name  string  `json:"name"`
	Icon  string  `json:"icon"`
	Cards []*Card `json:"cards"`
}

// ToCard converts an Entity to a UI Card
func (e *Entity) ToCard() *Card {
	card := &Card{
		EntityID:   e.EntityID,
		State:      e.State,
		Type:       detectCardType(e.EntityID),
		Attributes: e.Attributes,
	}

	// Get friendly name
	if name, ok := e.Attributes["friendly_name"].(string); ok {
		card.Name = name
	} else {
		card.Name = e.EntityID
	}

	// Get unit of measurement
	if unit, ok := e.Attributes["unit_of_measurement"].(string); ok {
		card.Unit = unit
	}

	// Determine icon based on type and state
	card.Icon = getIcon(card.Type, e.State, e.Attributes)

	// Check if entity is "on"
	card.IsOn = e.State == "on" || e.State == "home" || e.State == "open"

	// Determine group
	card.Group = detectGroup(e.EntityID, card.Type)

	return card
}

// GroupCards organizes cards into groups
func GroupCards(cards []*Card) []*CardGroup {
	// Define group order and icons
	groupOrder := []string{"Lights", "Climate", "Security", "Tesla", "Home", "Other"}
	groupIcons := map[string]string{
		"Lights":   "💡",
		"Climate":  "🌡️",
		"Security": "🔒",
		"Tesla":    "🚗",
		"Home":     "🏠",
		"Other":    "📦",
	}

	// Group cards
	grouped := make(map[string][]*Card)
	for _, card := range cards {
		grouped[card.Group] = append(grouped[card.Group], card)
	}

	// Build ordered result
	var result []*CardGroup
	for _, name := range groupOrder {
		if cards, ok := grouped[name]; ok && len(cards) > 0 {
			// Sort cards within Climate group: climate first, then sensors, then fans
			if name == "Climate" {
				sort.Slice(cards, func(i, j int) bool {
					return cardTypePriority(cards[i].Type) < cardTypePriority(cards[j].Type)
				})
			}
			result = append(result, &CardGroup{
				Name:  name,
				Icon:  groupIcons[name],
				Cards: cards,
			})
		}
	}

	return result
}

// cardTypePriority returns sort priority for cards within Climate group
func cardTypePriority(t CardType) int {
	switch t {
	case CardTypeClimate:
		return 0
	case CardTypeFan:
		return 1
	case CardTypeSensor:
		return 2
	default:
		return 3
	}
}

func detectGroup(entityID string, cardType CardType) string {
	id := strings.ToLower(entityID)

	// Tesla entities
	if strings.Contains(id, "tessa") || strings.Contains(id, "tesla") {
		return "Tesla"
	}

	// Weather entities
	if strings.Contains(id, "weather") || strings.Contains(id, "openweathermap") {
		return "Weather"
	}

	// Home entities (vacuum, litter robot, people)
	if strings.Contains(id, "litter") || strings.Contains(id, "s8") || strings.Contains(id, "vacuum") {
		return "Home"
	}
	if cardType == CardTypePerson {
		return "Home"
	}

	// By type
	switch cardType {
	case CardTypeLight:
		return "Lights"
	case CardTypeClimate:
		return "Climate"
	case CardTypeSensor:
		if strings.Contains(id, "temperature") || strings.Contains(id, "humidity") {
			return "Climate"
		}
		return "Other"
	case CardTypeLock:
		return "Security"
	case CardTypeFan:
		return "Climate"
	default:
		return "Other"
	}
}

func detectCardType(entityID string) CardType {
	parts := strings.Split(entityID, ".")
	if len(parts) == 0 {
		return CardTypeUnknown
	}

	switch parts[0] {
	case "sensor":
		return CardTypeSensor
	case "switch":
		return CardTypeSwitch
	case "light":
		return CardTypeLight
	case "climate":
		return CardTypeClimate
	case "binary_sensor":
		return CardTypeBinary
	case "camera":
		return CardTypeCamera
	case "weather":
		return CardTypeWeather
	case "person":
		return CardTypePerson
	case "lock":
		return CardTypeLock
	case "fan":
		return CardTypeFan
	case "vacuum":
		return CardTypeVacuum
	default:
		return CardTypeUnknown
	}
}

func getIcon(cardType CardType, state string, attrs map[string]interface{}) string {
	// Check for custom icon first
	if icon, ok := attrs["icon"].(string); ok {
		return convertMdiIcon(icon)
	}

	// Default icons by type
	switch cardType {
	case CardTypeLight:
		if state == "on" {
			return "💡"
		}
		return "🔅"
	case CardTypeSwitch:
		if state == "on" {
			return "🔌"
		}
		return "⭕"
	case CardTypeSensor:
		// Check device class for better icons
		if dc, ok := attrs["device_class"].(string); ok {
			switch dc {
			case "temperature":
				return "🌡️"
			case "humidity":
				return "💧"
			case "battery":
				return "🔋"
			case "power", "energy":
				return "⚡"
			case "motion":
				return "🚶"
			case "door", "window":
				if state == "on" {
					return "🚪"
				}
				return "🚪"
			}
		}
		return "📊"
	case CardTypeBinary:
		if dc, ok := attrs["device_class"].(string); ok {
			switch dc {
			case "motion":
				if state == "on" {
					return "🏃"
				}
				return "🚶"
			case "door":
				if state == "on" {
					return "🚪"
				}
				return "🚪"
			case "window":
				if state == "on" {
					return "🪟"
				}
				return "🪟"
			case "lock":
				if state == "on" {
					return "🔓"
				}
				return "🔒"
			}
		}
		return "⚫"
	case CardTypeClimate:
		return "🌡️"
	case CardTypeWeather:
		return getWeatherIcon(state)
	case CardTypeCamera:
		return "📷"
	case CardTypePerson:
		if state == "home" {
			return "🏠"
		}
		return "👤"
	case CardTypeLock:
		if state == "locked" {
			return "🔒"
		}
		return "🔓"
	case CardTypeFan:
		if state == "on" {
			return "🌀"
		}
		return "💨"
	case CardTypeVacuum:
		if state == "cleaning" {
			return "🧹"
		}
		return "🤖"
	default:
		return "❓"
	}
}

func getWeatherIcon(state string) string {
	switch state {
	case "sunny", "clear-night":
		return "☀️"
	case "cloudy":
		return "☁️"
	case "partlycloudy":
		return "⛅"
	case "rainy":
		return "🌧️"
	case "snowy":
		return "❄️"
	case "lightning":
		return "⚡"
	case "fog":
		return "🌫️"
	default:
		return "🌤️"
	}
}

func convertMdiIcon(icon string) string {
	// Convert mdi:icon-name to emoji (basic mapping)
	icon = strings.TrimPrefix(icon, "mdi:")
	switch icon {
	case "lightbulb", "lightbulb-on":
		return "💡"
	case "lightbulb-off":
		return "🔅"
	case "thermometer":
		return "🌡️"
	case "water-percent":
		return "💧"
	case "power-plug":
		return "🔌"
	case "television":
		return "📺"
	case "fan":
		return "🌀"
	case "garage":
		return "🚗"
	case "car", "car-electric":
		return "🚗"
	case "battery", "battery-charging":
		return "🔋"
	case "lock":
		return "🔒"
	case "lock-open":
		return "🔓"
	case "robot-vacuum":
		return "🤖"
	case "cat":
		return "🐱"
	case "weather-sunny":
		return "☀️"
	case "weather-cloudy":
		return "☁️"
	case "weather-rainy":
		return "🌧️"
	case "account":
		return "👤"
	case "home":
		return "🏠"
	case "map-marker":
		return "📍"
	default:
		return "•"
	}
}
