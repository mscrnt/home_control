package weather

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"time"
)

// Client handles Google Weather API requests with tiered caching
// Uses API key authentication (same key as Google Places API)
// Caching strategy to stay within 1000 calls/month:
// - Current conditions: refresh hourly (720/month)
// - Daily forecast: refresh every 12 hours (60/month)
// - Hourly forecast: refresh every 6 hours (120/month)
// Total: ~900 calls/month
type Client struct {
	apiKey    string
	lat       float64
	lon       float64
	timezone  *time.Location
	cacheFile string

	// Cached data with separate timestamps
	cache       *WeatherData
	cacheMu     sync.RWMutex
	lastCurrent time.Time
	lastDaily   time.Time
	lastHourly  time.Time
}

// persistedCache is the structure saved to disk
type persistedCache struct {
	Data        *WeatherData `json:"data"`
	LastCurrent time.Time    `json:"lastCurrent"`
	LastDaily   time.Time    `json:"lastDaily"`
	LastHourly  time.Time    `json:"lastHourly"`
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
	IconUri   string  `json:"iconUri"` // Google Weather API icon URL
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
	IconUri   string  `json:"iconUri"` // Google Weather API icon URL
	Pop       float64 `json:"pop"`     // Probability of precipitation
}

// DailyWeather represents daily forecast
type DailyWeather struct {
	Time      int64   `json:"time"`
	TempMin   float64 `json:"tempMin"`
	TempMax   float64 `json:"tempMax"`
	Humidity  int     `json:"humidity"`
	Condition string  `json:"condition"`
	Icon      string  `json:"icon"`
	IconUri   string  `json:"iconUri"` // Google Weather API icon URL
	Pop       float64 `json:"pop"`
	Sunrise   int64   `json:"sunrise"`
	Sunset    int64   `json:"sunset"`
	Summary   string  `json:"summary"`
}

// Google Weather API response structures
type googleTimeZone struct {
	ID string `json:"id"`
}

type googleCurrentResponse struct {
	CurrentTime          string                 `json:"currentTime"`
	TimeZone             googleTimeZone         `json:"timeZone"`
	IsDaytime            bool                   `json:"isDaytime"`
	WeatherCondition     googleWeatherCondition `json:"weatherCondition"`
	Temperature          googleTemperature      `json:"temperature"`
	FeelsLikeTemperature googleTemperature      `json:"feelsLikeTemperature"`
	Humidity             googlePercentage       `json:"humidity"`
	Wind                 googleWind             `json:"wind"`
	UvIndex              int                    `json:"uvIndex"`
	CloudCover           int                    `json:"cloudCover"`
}

type googleForecastDaysResponse struct {
	ForecastDays []googleDayForecast `json:"forecastDays"`
	TimeZone     googleTimeZone      `json:"timeZone"`
}

type googleDayForecast struct {
	Interval          googleInterval        `json:"interval"`
	DaytimeForecast   googleDayPartForecast `json:"daytimeForecast"`
	NighttimeForecast googleDayPartForecast `json:"nighttimeForecast"`
	MaxTemperature    googleTemperature     `json:"maxTemperature"`
	MinTemperature    googleTemperature     `json:"minTemperature"`
	SunEvents         googleSunEvents       `json:"sunEvents"`
	MoonEvents        googleMoonEvents      `json:"moonEvents"`
}

type googleDayPartForecast struct {
	WeatherCondition   googleWeatherCondition `json:"weatherCondition"`
	RelativeHumidity   int                    `json:"relativeHumidity"`
	Wind               googleWind             `json:"wind"`
	Precipitation      googlePrecipitation    `json:"precipitation"`
	CloudCover         int                    `json:"cloudCover"`
	UVIndex            int                    `json:"uvIndex"`
}

type googlePrecipitation struct {
	Probability struct {
		Percent int    `json:"percent"`
		Type    string `json:"type"`
	} `json:"probability"`
}

type googleMoonEvents struct {
	MoonPhase string `json:"moonPhase"`
}

type googleForecastHoursResponse struct {
	ForecastHours []googleHourForecast `json:"forecastHours"`
	TimeZone      googleTimeZone       `json:"timeZone"`
}

type googleHourForecast struct {
	Interval             googleInterval         `json:"interval"`
	WeatherCondition     googleWeatherCondition `json:"weatherCondition"`
	Temperature          googleTemperature      `json:"temperature"`
	FeelsLikeTemperature googleTemperature      `json:"feelsLikeTemperature"`
	RelativeHumidity     int                    `json:"relativeHumidity"`
	Precipitation        googlePrecipitation    `json:"precipitation"`
	IsDaytime            bool                   `json:"isDaytime"`
}

type googleWeatherCondition struct {
	IconBaseUri string `json:"iconBaseUri"`
	Description struct {
		Text string `json:"text"`
	} `json:"description"`
	Type string `json:"type"`
}

type googleTemperature struct {
	Degrees float64 `json:"degrees"`
	Unit    string  `json:"unit"`
}

type googlePercentage struct {
	Percent int `json:"percent"`
}

type googleWind struct {
	Speed struct {
		Value float64 `json:"value"`
		Unit  string  `json:"unit"`
	} `json:"speed"`
	Direction struct {
		Degrees int `json:"degrees"`
	} `json:"direction"`
}

type googleInterval struct {
	StartTime string `json:"startTime"`
	EndTime   string `json:"endTime"`
}

type googleSunEvents struct {
	Sunrise string `json:"sunriseTime"`
	Sunset  string `json:"sunsetTime"`
}

// Refresh intervals (10K free calls/month = ~4,320 calls with these settings)
const (
	currentRefreshInterval = 15 * time.Minute // Refresh current conditions every 15 min (~2,880/month)
	dailyRefreshInterval   = 1 * time.Hour    // Refresh daily forecast hourly (~720/month)
	hourlyRefreshInterval  = 1 * time.Hour    // Refresh hourly forecast hourly (~720/month)
)

// NewClient creates a new weather client using API key authentication
// cacheFile is optional - pass empty string to disable disk caching
func NewClient(apiKey string, lat, lon float64, timezone *time.Location, cacheFile string) *Client {
	return &Client{
		apiKey:    apiKey,
		lat:       lat,
		lon:       lon,
		timezone:  timezone,
		cacheFile: cacheFile,
		cache:     &WeatherData{},
	}
}

// Start begins the background refresh scheduler
func (c *Client) Start() {
	// Try to load cached data from disk first
	if err := c.loadCache(); err != nil {
		log.Printf("Weather cache load failed (will fetch fresh): %v", err)
	}

	// Only refresh data that has expired based on TTL or is stale
	now := time.Now()
	needsCurrent := now.Sub(c.lastCurrent) >= currentRefreshInterval
	needsDaily := now.Sub(c.lastDaily) >= dailyRefreshInterval || c.isDailyStale()
	needsHourly := now.Sub(c.lastHourly) >= hourlyRefreshInterval

	if needsCurrent || needsDaily || needsHourly {
		log.Printf("Weather cache: current=%v daily=%v hourly=%v (refreshing expired data)",
			!needsCurrent, !needsDaily, !needsHourly)

		if needsCurrent {
			if err := c.refreshCurrent(); err != nil {
				log.Printf("Current weather refresh failed: %v", err)
			}
		}
		if needsDaily {
			if err := c.refreshDaily(); err != nil {
				log.Printf("Daily forecast refresh failed: %v", err)
			}
		}
		if needsHourly {
			if err := c.refreshHourly(); err != nil {
				log.Printf("Hourly forecast refresh failed: %v", err)
			}
		}
	} else {
		log.Printf("Weather cache: all data still valid (current: %s ago, daily: %s ago, hourly: %s ago)",
			now.Sub(c.lastCurrent).Round(time.Minute),
			now.Sub(c.lastDaily).Round(time.Minute),
			now.Sub(c.lastHourly).Round(time.Minute))
	}

	// Start separate schedulers for tiered caching
	go c.runCurrentScheduler()
	go c.runDailyScheduler()
	go c.runHourlyScheduler()
}

// runCurrentScheduler refreshes current conditions every hour
func (c *Client) runCurrentScheduler() {
	ticker := time.NewTicker(currentRefreshInterval)
	defer ticker.Stop()

	for range ticker.C {
		if err := c.refreshCurrent(); err != nil {
			log.Printf("Current weather refresh failed: %v", err)
		}
	}
}

// runDailyScheduler refreshes daily forecast every 12 hours or when data is stale
func (c *Client) runDailyScheduler() {
	ticker := time.NewTicker(dailyRefreshInterval)
	defer ticker.Stop()

	// Also check every hour if daily data starts with a past day
	staleTicker := time.NewTicker(1 * time.Hour)
	defer staleTicker.Stop()

	for {
		select {
		case <-ticker.C:
			if err := c.refreshDaily(); err != nil {
				log.Printf("Daily forecast refresh failed: %v", err)
			}
		case <-staleTicker.C:
			// Check if the first day in cache is before today
			if c.isDailyStale() {
				log.Printf("Daily forecast stale (first day is in the past), refreshing...")
				if err := c.refreshDaily(); err != nil {
					log.Printf("Daily forecast refresh failed: %v", err)
				}
			}
		}
	}
}

// isDailyStale returns true if the first day in the daily forecast is before today
func (c *Client) isDailyStale() bool {
	c.cacheMu.RLock()
	defer c.cacheMu.RUnlock()

	if len(c.cache.Daily) == 0 {
		return true
	}

	now := time.Now()
	todayStart := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, now.Location()).Unix()
	return c.cache.Daily[0].Time < todayStart
}

// runHourlyScheduler refreshes hourly forecast every 6 hours
func (c *Client) runHourlyScheduler() {
	ticker := time.NewTicker(hourlyRefreshInterval)
	defer ticker.Stop()

	for range ticker.C {
		if err := c.refreshHourly(); err != nil {
			log.Printf("Hourly forecast refresh failed: %v", err)
		}
	}
}

// RefreshAll fetches all weather data (used for initial load)
func (c *Client) RefreshAll() error {
	log.Printf("Weather API: fetching all data for lat=%.4f, lon=%.4f", c.lat, c.lon)

	var wg sync.WaitGroup
	var currentErr, dailyErr, hourlyErr error

	wg.Add(3)
	go func() {
		defer wg.Done()
		currentErr = c.refreshCurrent()
	}()
	go func() {
		defer wg.Done()
		dailyErr = c.refreshDaily()
	}()
	go func() {
		defer wg.Done()
		hourlyErr = c.refreshHourly()
	}()
	wg.Wait()

	// Return first error encountered
	if currentErr != nil {
		return currentErr
	}
	if dailyErr != nil {
		return dailyErr
	}
	return hourlyErr
}

// Refresh is the legacy method name - now calls RefreshAll
func (c *Client) Refresh() error {
	return c.RefreshAll()
}

// refreshCurrent fetches current conditions from Google Weather API
func (c *Client) refreshCurrent() error {
	url := fmt.Sprintf(
		"https://weather.googleapis.com/v1/currentConditions:lookup?key=%s&location.latitude=%f&location.longitude=%f&unitsSystem=IMPERIAL",
		c.apiKey, c.lat, c.lon,
	)

	body, err := c.doRequest(url)
	if err != nil {
		return fmt.Errorf("failed to fetch current conditions: %w", err)
	}

	var resp googleCurrentResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return fmt.Errorf("failed to decode current conditions: %w", err)
	}

	c.cacheMu.Lock()
	c.cache.Current = CurrentWeather{
		Temp:      resp.Temperature.Degrees,
		FeelsLike: resp.FeelsLikeTemperature.Degrees,
		Humidity:  resp.Humidity.Percent,
		WindSpeed: resp.Wind.Speed.Value,
		WindDeg:   resp.Wind.Direction.Degrees,
		Clouds:    resp.CloudCover,
		UVI:       float64(resp.UvIndex),
		Condition: resp.WeatherCondition.Description.Text,
		Icon:      c.mapConditionToIcon(resp.WeatherCondition.Type, resp.IsDaytime),
		IconUri:   resp.WeatherCondition.IconBaseUri,
	}
	c.cache.Timezone = resp.TimeZone.ID
	c.cache.FetchedAt = time.Now()
	c.lastCurrent = time.Now()
	c.cacheMu.Unlock()

	log.Printf("Weather updated: %.0f°F, %s", c.cache.Current.Temp, c.cache.Current.Condition)
	c.saveCache()
	return nil
}

// refreshDaily fetches daily forecast from Google Weather API
func (c *Client) refreshDaily() error {
	// Request 10 days of forecast with pageSize=10 to get all days in one request
	// (pageSize defaults to 5, so without it we'd only get 5 days per page)
	url := fmt.Sprintf(
		"https://weather.googleapis.com/v1/forecast/days:lookup?key=%s&location.latitude=%f&location.longitude=%f&unitsSystem=IMPERIAL&days=10&pageSize=10",
		c.apiKey, c.lat, c.lon,
	)

	body, err := c.doRequest(url)
	if err != nil {
		return fmt.Errorf("failed to fetch daily forecast: %w", err)
	}

	var resp googleForecastDaysResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return fmt.Errorf("failed to decode daily forecast: %w", err)
	}

	c.cacheMu.Lock()
	c.cache.Daily = make([]DailyWeather, 0, len(resp.ForecastDays))
	for _, day := range resp.ForecastDays {
		startTime, _ := time.Parse(time.RFC3339, day.Interval.StartTime)
		sunrise, _ := time.Parse(time.RFC3339, day.SunEvents.Sunrise)
		sunset, _ := time.Parse(time.RFC3339, day.SunEvents.Sunset)

		// Update current weather sunrise/sunset from first day
		if len(c.cache.Daily) == 0 {
			c.cache.Current.Sunrise = sunrise.Unix()
			c.cache.Current.Sunset = sunset.Unix()
		}

		c.cache.Daily = append(c.cache.Daily, DailyWeather{
			Time:      startTime.Unix(),
			TempMin:   day.MinTemperature.Degrees,
			TempMax:   day.MaxTemperature.Degrees,
			Humidity:  day.DaytimeForecast.RelativeHumidity,
			Condition: day.DaytimeForecast.WeatherCondition.Description.Text,
			Icon:      c.mapConditionToIcon(day.DaytimeForecast.WeatherCondition.Type, true),
			IconUri:   day.DaytimeForecast.WeatherCondition.IconBaseUri,
			Pop:       float64(day.DaytimeForecast.Precipitation.Probability.Percent) / 100.0,
			Sunrise:   sunrise.Unix(),
			Sunset:    sunset.Unix(),
			Summary:   day.DaytimeForecast.WeatherCondition.Description.Text,
		})
	}
	c.lastDaily = time.Now()
	c.cacheMu.Unlock()

	log.Printf("Daily forecast updated: %d days", len(c.cache.Daily))
	c.saveCache()
	return nil
}

// refreshHourly fetches hourly forecast from Google Weather API
func (c *Client) refreshHourly() error {
	url := fmt.Sprintf(
		"https://weather.googleapis.com/v1/forecast/hours:lookup?key=%s&location.latitude=%f&location.longitude=%f&unitsSystem=IMPERIAL",
		c.apiKey, c.lat, c.lon,
	)

	body, err := c.doRequest(url)
	if err != nil {
		return fmt.Errorf("failed to fetch hourly forecast: %w", err)
	}

	var resp googleForecastHoursResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return fmt.Errorf("failed to decode hourly forecast: %w", err)
	}

	c.cacheMu.Lock()
	c.cache.Hourly = make([]HourlyWeather, 0, len(resp.ForecastHours))
	for _, hour := range resp.ForecastHours {
		startTime, _ := time.Parse(time.RFC3339, hour.Interval.StartTime)
		c.cache.Hourly = append(c.cache.Hourly, HourlyWeather{
			Time:      startTime.Unix(),
			Temp:      hour.Temperature.Degrees,
			FeelsLike: hour.FeelsLikeTemperature.Degrees,
			Humidity:  hour.RelativeHumidity,
			Condition: hour.WeatherCondition.Description.Text,
			Icon:      c.mapConditionToIcon(hour.WeatherCondition.Type, hour.IsDaytime),
			IconUri:   hour.WeatherCondition.IconBaseUri,
			Pop:       float64(hour.Precipitation.Probability.Percent) / 100.0,
		})
	}
	c.lastHourly = time.Now()
	c.cacheMu.Unlock()

	log.Printf("Hourly forecast updated: %d hours", len(c.cache.Hourly))
	c.saveCache()
	return nil
}

// doRequest makes a GET request to the Google Weather API
func (c *Client) doRequest(url string) ([]byte, error) {
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

// mapConditionToIcon converts Google Weather condition types to our icon names
func (c *Client) mapConditionToIcon(conditionType string, isDaytime bool) string {
	switch conditionType {
	case "CLEAR":
		if isDaytime {
			return "sun"
		}
		return "moon"
	case "MOSTLY_CLEAR", "PARTLY_CLOUDY":
		if isDaytime {
			return "cloud-sun"
		}
		return "cloud-moon"
	case "MOSTLY_CLOUDY":
		return "cloud"
	case "CLOUDY", "OVERCAST":
		return "clouds"
	case "LIGHT_RAIN", "RAIN", "HEAVY_RAIN", "RAIN_SHOWERS":
		return "cloud-showers"
	case "DRIZZLE", "LIGHT_DRIZZLE":
		return "cloud-rain"
	case "THUNDERSTORM", "THUNDERSTORM_WITH_RAIN":
		return "bolt"
	case "SNOW", "LIGHT_SNOW", "HEAVY_SNOW", "SNOW_SHOWERS", "BLOWING_SNOW":
		return "snowflake"
	case "SLEET", "FREEZING_RAIN", "ICE_PELLETS":
		return "snowflake"
	case "FOG", "HAZE", "MIST":
		return "smog"
	case "WINDY", "BREEZY":
		return "wind"
	default:
		return "cloud"
	}
}

// GetWeather returns the cached weather data with stale entries filtered out
func (c *Client) GetWeather() *WeatherData {
	c.cacheMu.RLock()
	defer c.cacheMu.RUnlock()

	// Return nil if no data has been fetched yet
	if c.cache.FetchedAt.IsZero() {
		return nil
	}

	// Calculate the start of today (midnight local time)
	now := time.Now()
	todayStart := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, now.Location()).Unix()

	// Filter daily forecast to only include today and future days
	var filteredDaily []DailyWeather
	for _, day := range c.cache.Daily {
		if day.Time >= todayStart {
			filteredDaily = append(filteredDaily, day)
		}
	}

	// Filter hourly forecast to only include current hour and future
	currentHour := time.Date(now.Year(), now.Month(), now.Day(), now.Hour(), 0, 0, 0, now.Location()).Unix()
	var filteredHourly []HourlyWeather
	for _, hour := range c.cache.Hourly {
		if hour.Time >= currentHour {
			filteredHourly = append(filteredHourly, hour)
		}
	}

	// Return a copy with filtered data
	return &WeatherData{
		Current:   c.cache.Current,
		Hourly:    filteredHourly,
		Daily:     filteredDaily,
		Timezone:  c.cache.Timezone,
		FetchedAt: c.cache.FetchedAt,
	}
}

// IsConfigured returns true if the weather client is properly configured
func (c *Client) IsConfigured() bool {
	return c.apiKey != ""
}

// GetLastFetch returns when weather was last fetched
func (c *Client) GetLastFetch() time.Time {
	c.cacheMu.RLock()
	defer c.cacheMu.RUnlock()
	return c.cache.FetchedAt
}

// loadCache loads weather data from disk cache
func (c *Client) loadCache() error {
	if c.cacheFile == "" {
		return fmt.Errorf("no cache file configured")
	}

	data, err := os.ReadFile(c.cacheFile)
	if err != nil {
		return fmt.Errorf("failed to read cache file: %w", err)
	}

	var cached persistedCache
	if err := json.Unmarshal(data, &cached); err != nil {
		return fmt.Errorf("failed to parse cache file: %w", err)
	}

	if cached.Data == nil {
		return fmt.Errorf("cache file contains no data")
	}

	c.cacheMu.Lock()
	defer c.cacheMu.Unlock()

	c.cache = cached.Data
	c.lastCurrent = cached.LastCurrent
	c.lastDaily = cached.LastDaily
	c.lastHourly = cached.LastHourly

	log.Printf("Weather cache loaded: current=%s ago, daily=%s ago, hourly=%s ago",
		time.Since(c.lastCurrent).Round(time.Minute),
		time.Since(c.lastDaily).Round(time.Minute),
		time.Since(c.lastHourly).Round(time.Minute))

	return nil
}

// saveCache persists weather data to disk
func (c *Client) saveCache() {
	if c.cacheFile == "" {
		return
	}

	c.cacheMu.RLock()
	cached := persistedCache{
		Data:        c.cache,
		LastCurrent: c.lastCurrent,
		LastDaily:   c.lastDaily,
		LastHourly:  c.lastHourly,
	}
	c.cacheMu.RUnlock()

	data, err := json.MarshalIndent(cached, "", "  ")
	if err != nil {
		log.Printf("Failed to marshal weather cache: %v", err)
		return
	}

	// Ensure directory exists
	dir := filepath.Dir(c.cacheFile)
	if err := os.MkdirAll(dir, 0755); err != nil {
		log.Printf("Failed to create cache directory: %v", err)
		return
	}

	if err := os.WriteFile(c.cacheFile, data, 0644); err != nil {
		log.Printf("Failed to write weather cache: %v", err)
		return
	}
}
