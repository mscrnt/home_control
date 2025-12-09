package weather

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"sync"
	"time"
)

// Client handles OpenWeatherMap API requests with caching
type Client struct {
	apiKey    string
	lat       float64
	lon       float64
	units     string
	cache     *WeatherData
	cacheMu   sync.RWMutex
	lastFetch time.Time
	timezone  *time.Location
}

// WeatherData represents the cached weather information
type WeatherData struct {
	Current   CurrentWeather  `json:"current"`
	Hourly    []HourlyWeather `json:"hourly"`
	Daily     []DailyWeather  `json:"daily"`
	Timezone  string          `json:"timezone"`
	FetchedAt time.Time       `json:"fetchedAt"`
}

// CurrentWeather represents current weather conditions
type CurrentWeather struct {
	Temp      float64 `json:"temp"`
	FeelsLike float64 `json:"feelsLike"`
	Humidity  int     `json:"humidity"`
	WindSpeed float64 `json:"windSpeed"`
	WindDeg   int     `json:"windDeg"`
	Clouds    int     `json:"clouds"`
	UVI       float64 `json:"uvi"`
	Condition string  `json:"condition"`
	Icon      string  `json:"icon"`
	Sunrise   int64   `json:"sunrise"`
	Sunset    int64   `json:"sunset"`
}

// HourlyWeather represents hourly forecast
type HourlyWeather struct {
	Time      int64   `json:"time"`
	Temp      float64 `json:"temp"`
	FeelsLike float64 `json:"feelsLike"`
	Humidity  int     `json:"humidity"`
	Condition string  `json:"condition"`
	Icon      string  `json:"icon"`
	Pop       float64 `json:"pop"` // Probability of precipitation
}

// DailyWeather represents daily forecast
type DailyWeather struct {
	Time      int64   `json:"time"`
	TempMin   float64 `json:"tempMin"`
	TempMax   float64 `json:"tempMax"`
	Humidity  int     `json:"humidity"`
	Condition string  `json:"condition"`
	Icon      string  `json:"icon"`
	Pop       float64 `json:"pop"`
	Sunrise   int64   `json:"sunrise"`
	Sunset    int64   `json:"sunset"`
	Summary   string  `json:"summary"`
}

// OpenWeatherMap 2.5 API response structures (FREE tier)
type owmCurrentResponse struct {
	Main struct {
		Temp      float64 `json:"temp"`
		FeelsLike float64 `json:"feels_like"`
		Humidity  int     `json:"humidity"`
	} `json:"main"`
	Wind struct {
		Speed float64 `json:"speed"`
		Deg   int     `json:"deg"`
	} `json:"wind"`
	Clouds struct {
		All int `json:"all"`
	} `json:"clouds"`
	Weather []struct {
		Main        string `json:"main"`
		Description string `json:"description"`
		Icon        string `json:"icon"`
	} `json:"weather"`
	Sys struct {
		Sunrise int64 `json:"sunrise"`
		Sunset  int64 `json:"sunset"`
	} `json:"sys"`
	Timezone int    `json:"timezone"`
	Name     string `json:"name"`
}

type owmForecastResponse struct {
	List []struct {
		Dt   int64 `json:"dt"`
		Main struct {
			Temp      float64 `json:"temp"`
			FeelsLike float64 `json:"feels_like"`
			TempMin   float64 `json:"temp_min"`
			TempMax   float64 `json:"temp_max"`
			Humidity  int     `json:"humidity"`
		} `json:"main"`
		Weather []struct {
			Main        string `json:"main"`
			Description string `json:"description"`
			Icon        string `json:"icon"`
		} `json:"weather"`
		Pop float64 `json:"pop"`
	} `json:"list"`
	City struct {
		Sunrise  int64 `json:"sunrise"`
		Sunset   int64 `json:"sunset"`
		Timezone int   `json:"timezone"`
	} `json:"city"`
}

// NewClient creates a new weather client
func NewClient(apiKey string, lat, lon float64, timezone *time.Location) *Client {
	return &Client{
		apiKey:   apiKey,
		lat:      lat,
		lon:      lon,
		units:    "imperial", // Fahrenheit
		timezone: timezone,
	}
}

// Start begins the background refresh scheduler
func (c *Client) Start() {
	// Initial fetch
	if err := c.Refresh(); err != nil {
		log.Printf("Initial weather fetch failed: %v", err)
	}

	// Start scheduler
	go c.runScheduler()
}

// runScheduler runs the refresh at scheduled times (1am, 6am, 3pm)
func (c *Client) runScheduler() {
	for {
		now := time.Now().In(c.timezone)
		nextRefresh := c.getNextRefreshTime(now)
		duration := nextRefresh.Sub(now)

		log.Printf("Weather: next refresh at %s (in %v)", nextRefresh.Format("3:04 PM"), duration.Round(time.Minute))

		timer := time.NewTimer(duration)
		<-timer.C

		if err := c.Refresh(); err != nil {
			log.Printf("Scheduled weather refresh failed: %v", err)
		}
	}
}

// getNextRefreshTime calculates the next refresh time (1am, 6am, or 3pm)
func (c *Client) getNextRefreshTime(now time.Time) time.Time {
	refreshHours := []int{1, 6, 15} // 1am, 6am, 3pm

	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, c.timezone)

	for _, hour := range refreshHours {
		candidate := today.Add(time.Duration(hour) * time.Hour)
		if candidate.After(now) {
			return candidate
		}
	}

	// All refresh times today have passed, return 1am tomorrow
	return today.Add(25 * time.Hour) // 1am next day
}

// Refresh fetches fresh weather data from OpenWeatherMap (FREE 2.5 API)
func (c *Client) Refresh() error {
	log.Printf("Weather API request: lat=%.4f, lon=%.4f", c.lat, c.lon)

	// Fetch current weather
	currentURL := fmt.Sprintf(
		"https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&units=%s&appid=%s",
		c.lat, c.lon, c.units, c.apiKey,
	)

	currentResp, err := c.fetchURL(currentURL)
	if err != nil {
		return fmt.Errorf("failed to fetch current weather: %w", err)
	}

	var current owmCurrentResponse
	if err := json.Unmarshal(currentResp, &current); err != nil {
		return fmt.Errorf("failed to decode current weather: %w", err)
	}

	// Fetch 5-day forecast
	forecastURL := fmt.Sprintf(
		"https://api.openweathermap.org/data/2.5/forecast?lat=%f&lon=%f&units=%s&appid=%s",
		c.lat, c.lon, c.units, c.apiKey,
	)

	forecastResp, err := c.fetchURL(forecastURL)
	if err != nil {
		return fmt.Errorf("failed to fetch forecast: %w", err)
	}

	var forecast owmForecastResponse
	if err := json.Unmarshal(forecastResp, &forecast); err != nil {
		return fmt.Errorf("failed to decode forecast: %w", err)
	}

	// Convert to our format
	data := c.convertResponse(&current, &forecast)

	c.cacheMu.Lock()
	c.cache = data
	c.lastFetch = time.Now()
	c.cacheMu.Unlock()

	log.Printf("Weather updated: %.0f°F, %s", data.Current.Temp, data.Current.Condition)
	return nil
}

func (c *Client) fetchURL(url string) ([]byte, error) {
	resp, err := http.Get(url)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("API returned status %d: %s", resp.StatusCode, string(body))
	}

	return body, nil
}

// convertResponse converts OpenWeatherMap 2.5 API responses to our format
func (c *Client) convertResponse(current *owmCurrentResponse, forecast *owmForecastResponse) *WeatherData {
	data := &WeatherData{
		Timezone:  current.Name,
		FetchedAt: time.Now(),
	}

	// Current weather
	if len(current.Weather) > 0 {
		data.Current = CurrentWeather{
			Temp:      current.Main.Temp,
			FeelsLike: current.Main.FeelsLike,
			Humidity:  current.Main.Humidity,
			WindSpeed: current.Wind.Speed,
			WindDeg:   current.Wind.Deg,
			Clouds:    current.Clouds.All,
			UVI:       0, // Not available in free API
			Condition: current.Weather[0].Main,
			Icon:      c.mapIcon(current.Weather[0].Icon),
			Sunrise:   current.Sys.Sunrise,
			Sunset:    current.Sys.Sunset,
		}
	}

	// Hourly forecast (3-hour intervals from 5-day forecast)
	for i, item := range forecast.List {
		if i >= 8 { // ~24 hours (8 x 3-hour intervals)
			break
		}
		if len(item.Weather) > 0 {
			data.Hourly = append(data.Hourly, HourlyWeather{
				Time:      item.Dt,
				Temp:      item.Main.Temp,
				FeelsLike: item.Main.FeelsLike,
				Humidity:  item.Main.Humidity,
				Condition: item.Weather[0].Main,
				Icon:      c.mapIcon(item.Weather[0].Icon),
				Pop:       item.Pop,
			})
		}
	}

	// Daily forecast - aggregate from 3-hour data
	dailyMap := make(map[string]*DailyWeather)
	for _, item := range forecast.List {
		if len(item.Weather) == 0 {
			continue
		}

		// Get date string for grouping
		t := time.Unix(item.Dt, 0).In(c.timezone)
		dateKey := t.Format("2006-01-02")

		if daily, ok := dailyMap[dateKey]; ok {
			// Update min/max temps
			if item.Main.TempMin < daily.TempMin {
				daily.TempMin = item.Main.TempMin
			}
			if item.Main.TempMax > daily.TempMax {
				daily.TempMax = item.Main.TempMax
			}
			if item.Pop > daily.Pop {
				daily.Pop = item.Pop
			}
		} else {
			// Create new daily entry
			dailyMap[dateKey] = &DailyWeather{
				Time:      time.Date(t.Year(), t.Month(), t.Day(), 12, 0, 0, 0, c.timezone).Unix(),
				TempMin:   item.Main.TempMin,
				TempMax:   item.Main.TempMax,
				Humidity:  item.Main.Humidity,
				Condition: item.Weather[0].Main,
				Icon:      c.mapIcon(item.Weather[0].Icon),
				Pop:       item.Pop,
				Sunrise:   forecast.City.Sunrise,
				Sunset:    forecast.City.Sunset,
				Summary:   item.Weather[0].Description,
			}
		}
	}

	// Convert map to sorted slice (up to 5 days)
	for i := 0; i < 5; i++ {
		t := time.Now().In(c.timezone).AddDate(0, 0, i)
		dateKey := t.Format("2006-01-02")
		if daily, ok := dailyMap[dateKey]; ok {
			data.Daily = append(data.Daily, *daily)
		}
	}

	return data
}

// mapIcon converts OpenWeatherMap icon codes to our icon names
func (c *Client) mapIcon(code string) string {
	switch code {
	case "01d": // clear sky day
		return "sun"
	case "01n": // clear sky night
		return "moon"
	case "02d", "02n": // few clouds
		return "cloud-sun"
	case "03d", "03n": // scattered clouds
		return "cloud"
	case "04d", "04n": // broken clouds
		return "clouds"
	case "09d", "09n": // shower rain
		return "cloud-rain"
	case "10d", "10n": // rain
		return "cloud-showers"
	case "11d", "11n": // thunderstorm
		return "bolt"
	case "13d", "13n": // snow
		return "snowflake"
	case "50d", "50n": // mist
		return "smog"
	default:
		return "cloud"
	}
}

// GetWeather returns the cached weather data
func (c *Client) GetWeather() *WeatherData {
	c.cacheMu.RLock()
	defer c.cacheMu.RUnlock()
	return c.cache
}

// IsConfigured returns true if the weather client is properly configured
func (c *Client) IsConfigured() bool {
	return c.apiKey != ""
}

// GetLastFetch returns when weather was last fetched
func (c *Client) GetLastFetch() time.Time {
	c.cacheMu.RLock()
	defer c.cacheMu.RUnlock()
	return c.lastFetch
}
