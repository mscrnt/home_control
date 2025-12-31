package spotify

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	authURL  = "https://accounts.spotify.com/authorize"
	tokenURL = "https://accounts.spotify.com/api/token"
	apiURL   = "https://api.spotify.com/v1"
)

// Scopes required for playback control, search, and playlist browsing
var Scopes = []string{
	"user-read-playback-state",
	"user-modify-playback-state",
	"user-read-currently-playing",
	"playlist-read-private",
	"playlist-read-collaborative",
	"user-library-read",
	"user-library-modify",
	"user-read-recently-played",
	"user-top-read",
	"user-follow-read",
	"user-follow-modify",
}

// Token represents OAuth tokens
type Token struct {
	AccessToken  string    `json:"access_token"`
	TokenType    string    `json:"token_type"`
	RefreshToken string    `json:"refresh_token"`
	ExpiresIn    int       `json:"expires_in"`
	ExpiresAt    time.Time `json:"expires_at"`
	Scope        string    `json:"scope"`
}

// Client handles Spotify API interactions
type Client struct {
	clientID     string
	clientSecret string
	redirectURI  string
	token        *Token
	httpClient   *http.Client
	mu           sync.RWMutex
	onTokenSave  func(*Token) error

	// Rate limiting and caching
	playbackCache     *PlaybackState
	playbackCacheTime time.Time
	playbackCacheTTL  time.Duration
	rateLimitUntil    time.Time
	cacheMu           sync.RWMutex
}

// NewClient creates a new Spotify client
func NewClient(clientID, clientSecret, redirectURI string) *Client {
	return &Client{
		clientID:         clientID,
		clientSecret:     clientSecret,
		redirectURI:      redirectURI,
		httpClient:       &http.Client{Timeout: 10 * time.Second},
		playbackCacheTTL: 3 * time.Second, // Cache playback state for 3 seconds
	}
}

// SetTokenSaveCallback sets a callback for saving tokens
func (c *Client) SetTokenSaveCallback(fn func(*Token) error) {
	c.onTokenSave = fn
}

// SetToken sets the current token
func (c *Client) SetToken(token *Token) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.token = token
}

// GetToken returns the current token
func (c *Client) GetToken() *Token {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.token
}

// IsAuthenticated returns true if we have a valid token
func (c *Client) IsAuthenticated() bool {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.token != nil && c.token.AccessToken != ""
}

// GetAuthURL returns the Spotify authorization URL
func (c *Client) GetAuthURL(state string) string {
	params := url.Values{
		"client_id":     {c.clientID},
		"response_type": {"code"},
		"redirect_uri":  {c.redirectURI},
		"scope":         {strings.Join(Scopes, " ")},
		"state":         {state},
	}
	return authURL + "?" + params.Encode()
}

// Exchange exchanges an authorization code for tokens
func (c *Client) Exchange(ctx context.Context, code string) (*Token, error) {
	data := url.Values{
		"grant_type":   {"authorization_code"},
		"code":         {code},
		"redirect_uri": {c.redirectURI},
	}

	req, err := http.NewRequestWithContext(ctx, "POST", tokenURL, strings.NewReader(data.Encode()))
	if err != nil {
		return nil, err
	}

	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Authorization", "Basic "+c.basicAuth())

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("token exchange failed: %s - %s", resp.Status, string(body))
	}

	var token Token
	if err := json.NewDecoder(resp.Body).Decode(&token); err != nil {
		return nil, err
	}

	token.ExpiresAt = time.Now().Add(time.Duration(token.ExpiresIn) * time.Second)

	c.mu.Lock()
	c.token = &token
	c.mu.Unlock()

	if c.onTokenSave != nil {
		if err := c.onTokenSave(&token); err != nil {
			return nil, fmt.Errorf("failed to save token: %w", err)
		}
	}

	return &token, nil
}

// RefreshAccessToken refreshes the access token
func (c *Client) RefreshAccessToken(ctx context.Context) error {
	c.mu.RLock()
	refreshToken := ""
	if c.token != nil {
		refreshToken = c.token.RefreshToken
	}
	c.mu.RUnlock()

	if refreshToken == "" {
		return fmt.Errorf("no refresh token available")
	}

	data := url.Values{
		"grant_type":    {"refresh_token"},
		"refresh_token": {refreshToken},
	}

	req, err := http.NewRequestWithContext(ctx, "POST", tokenURL, strings.NewReader(data.Encode()))
	if err != nil {
		return err
	}

	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Authorization", "Basic "+c.basicAuth())

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("token refresh failed: %s - %s", resp.Status, string(body))
	}

	var newToken Token
	if err := json.NewDecoder(resp.Body).Decode(&newToken); err != nil {
		return err
	}

	newToken.ExpiresAt = time.Now().Add(time.Duration(newToken.ExpiresIn) * time.Second)

	// Preserve refresh token if not returned
	if newToken.RefreshToken == "" {
		newToken.RefreshToken = refreshToken
	}

	c.mu.Lock()
	c.token = &newToken
	c.mu.Unlock()

	if c.onTokenSave != nil {
		if err := c.onTokenSave(&newToken); err != nil {
			return fmt.Errorf("failed to save token: %w", err)
		}
	}

	return nil
}

func (c *Client) basicAuth() string {
	return base64.StdEncoding.EncodeToString([]byte(c.clientID + ":" + c.clientSecret))
}

// ensureValidToken refreshes the token if needed
func (c *Client) ensureValidToken(ctx context.Context) error {
	c.mu.RLock()
	token := c.token
	c.mu.RUnlock()

	if token == nil {
		return fmt.Errorf("not authenticated")
	}

	// Refresh if token expires within 5 minutes
	if time.Until(token.ExpiresAt) < 5*time.Minute {
		return c.RefreshAccessToken(ctx)
	}

	return nil
}

// doRequest makes an authenticated API request
func (c *Client) doRequest(ctx context.Context, method, endpoint string, body io.Reader) (*http.Response, error) {
	// Check if we're currently rate limited
	c.cacheMu.RLock()
	rateLimitUntil := c.rateLimitUntil
	c.cacheMu.RUnlock()

	if time.Now().Before(rateLimitUntil) {
		return nil, fmt.Errorf("rate limited, retry after %v", time.Until(rateLimitUntil).Round(time.Second))
	}

	if err := c.ensureValidToken(ctx); err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, method, apiURL+endpoint, body)
	if err != nil {
		return nil, err
	}

	c.mu.RLock()
	req.Header.Set("Authorization", "Bearer "+c.token.AccessToken)
	c.mu.RUnlock()

	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}

	// Handle 429 Too Many Requests - respect Retry-After header
	if resp.StatusCode == http.StatusTooManyRequests {
		retryAfter := resp.Header.Get("Retry-After")
		waitDuration := 30 * time.Second // Default to 30 seconds

		if retryAfter != "" {
			if seconds, err := strconv.Atoi(retryAfter); err == nil && seconds > 0 {
				waitDuration = time.Duration(seconds) * time.Second
			}
		}

		c.cacheMu.Lock()
		c.rateLimitUntil = time.Now().Add(waitDuration)
		c.cacheMu.Unlock()

		resp.Body.Close()
		return nil, fmt.Errorf("rate limited by Spotify, retry after %v", waitDuration)
	}

	return resp, nil
}

// Player types

// Device represents a Spotify playback device
type Device struct {
	ID               string `json:"id"`
	IsActive         bool   `json:"is_active"`
	IsPrivateSession bool   `json:"is_private_session"`
	IsRestricted     bool   `json:"is_restricted"`
	Name             string `json:"name"`
	Type             string `json:"type"`
	VolumePercent    int    `json:"volume_percent"`
}

// Image represents an image
type Image struct {
	URL    string `json:"url"`
	Height int    `json:"height"`
	Width  int    `json:"width"`
}

// Artist represents an artist
type Artist struct {
	ID     string  `json:"id"`
	Name   string  `json:"name"`
	URI    string  `json:"uri"`
	Images []Image `json:"images,omitempty"`
}

// Album represents an album
type Album struct {
	ID      string   `json:"id"`
	Name    string   `json:"name"`
	URI     string   `json:"uri"`
	Images  []Image  `json:"images"`
	Artists []Artist `json:"artists,omitempty"`
}

// Track represents a track
type Track struct {
	ID         string   `json:"id"`
	Name       string   `json:"name"`
	URI        string   `json:"uri"`
	DurationMS int      `json:"duration_ms"`
	Artists    []Artist `json:"artists"`
	Album      Album    `json:"album"`
}

// PlaybackState represents the current playback state
type PlaybackState struct {
	Device       *Device `json:"device"`
	ShuffleState bool    `json:"shuffle_state"`
	RepeatState  string  `json:"repeat_state"`
	Timestamp    int64   `json:"timestamp"`
	ProgressMS   int     `json:"progress_ms"`
	IsPlaying    bool    `json:"is_playing"`
	Item         *Track  `json:"item"`
}

// Playlist represents a playlist
type Playlist struct {
	ID          string  `json:"id"`
	Name        string  `json:"name"`
	Description string  `json:"description"`
	URI         string  `json:"uri"`
	Images      []Image `json:"images"`
	Owner       struct {
		DisplayName string `json:"display_name"`
		ID          string `json:"id"`
	} `json:"owner"`
	Tracks struct {
		Total int `json:"total"`
	} `json:"tracks"`
}

// PlaylistTrack represents a track in a playlist
type PlaylistTrack struct {
	AddedAt string `json:"added_at"`
	Track   Track  `json:"track"`
}

// RecentlyPlayedItem represents a recently played track
type RecentlyPlayedItem struct {
	PlayedAt string `json:"played_at"`
	Track    Track  `json:"track"`
}

// SearchResults represents search results
type SearchResults struct {
	Tracks struct {
		Items []Track `json:"items"`
		Total int     `json:"total"`
	} `json:"tracks"`
	Albums struct {
		Items []Album `json:"items"`
		Total int     `json:"total"`
	} `json:"albums"`
	Artists struct {
		Items []Artist `json:"items"`
		Total int      `json:"total"`
	} `json:"artists"`
	Playlists struct {
		Items []Playlist `json:"items"`
		Total int        `json:"total"`
	} `json:"playlists"`
}

// GetPlaybackState returns the current playback state with caching
// to reduce API calls and avoid rate limiting.
// Uses market=from_token to get content available in user's country.
// Supports both tracks and episodes via additional_types parameter.
func (c *Client) GetPlaybackState(ctx context.Context) (*PlaybackState, error) {
	// Check cache first - return cached data if still valid
	c.cacheMu.RLock()
	if c.playbackCache != nil && time.Since(c.playbackCacheTime) < c.playbackCacheTTL {
		cached := c.playbackCache
		c.cacheMu.RUnlock()
		return cached, nil
	}
	c.cacheMu.RUnlock()

	// Include market and additional_types for better content availability
	// market=from_token uses the user's account country
	resp, err := c.doRequest(ctx, "GET", "/me/player?market=from_token&additional_types=track,episode", nil)
	if err != nil {
		// On rate limit or network error, return cached data if available
		c.cacheMu.RLock()
		if c.playbackCache != nil {
			cached := c.playbackCache
			c.cacheMu.RUnlock()
			return cached, nil
		}
		c.cacheMu.RUnlock()
		return nil, err
	}
	defer resp.Body.Close()

	// 204 means no active playback/device
	if resp.StatusCode == http.StatusNoContent {
		c.cacheMu.Lock()
		c.playbackCache = nil
		c.playbackCacheTime = time.Now()
		c.cacheMu.Unlock()
		return nil, nil
	}

	// Handle other error codes
	if resp.StatusCode == http.StatusUnauthorized {
		return nil, fmt.Errorf("unauthorized: invalid or expired access token")
	}
	if resp.StatusCode == http.StatusForbidden {
		return nil, fmt.Errorf("forbidden: insufficient permissions (requires user-read-playback-state scope)")
	}
	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get playback state failed: %s - %s", resp.Status, string(body))
	}

	var state PlaybackState
	if err := json.NewDecoder(resp.Body).Decode(&state); err != nil {
		return nil, err
	}

	// Update cache
	c.cacheMu.Lock()
	c.playbackCache = &state
	c.playbackCacheTime = time.Now()
	c.cacheMu.Unlock()

	return &state, nil
}

// InvalidatePlaybackCache clears the playback cache, useful after control actions
func (c *Client) InvalidatePlaybackCache() {
	c.cacheMu.Lock()
	c.playbackCacheTime = time.Time{} // Set to zero time to force refresh
	c.cacheMu.Unlock()
}

// GetDevices returns available playback devices
func (c *Client) GetDevices(ctx context.Context) ([]Device, error) {
	resp, err := c.doRequest(ctx, "GET", "/me/player/devices", nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get devices failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Devices []Device `json:"devices"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	return result.Devices, nil
}

// Play starts or resumes playback
func (c *Client) Play(ctx context.Context, deviceID string) error {
	endpoint := "/me/player/play"
	if deviceID != "" {
		endpoint += "?device_id=" + deviceID
	}

	resp, err := c.doRequest(ctx, "PUT", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("play failed: %s - %s", resp.Status, string(body))
	}

	c.InvalidatePlaybackCache()
	return nil
}

// PlayURI plays a specific URI (track, album, playlist)
func (c *Client) PlayURI(ctx context.Context, deviceID string, uri string, position int) error {
	endpoint := "/me/player/play"
	if deviceID != "" {
		endpoint += "?device_id=" + deviceID
	}

	var body string
	if strings.Contains(uri, ":track:") {
		// For tracks, use uris array
		body = fmt.Sprintf(`{"uris":["%s"]}`, uri)
	} else {
		// For albums/playlists, use context_uri
		if position > 0 {
			body = fmt.Sprintf(`{"context_uri":"%s","offset":{"position":%d}}`, uri, position)
		} else {
			body = fmt.Sprintf(`{"context_uri":"%s"}`, uri)
		}
	}

	resp, err := c.doRequest(ctx, "PUT", endpoint, strings.NewReader(body))
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		respBody, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("play URI failed: %s - %s", resp.Status, string(respBody))
	}

	c.InvalidatePlaybackCache()
	return nil
}

// Pause pauses playback
func (c *Client) Pause(ctx context.Context, deviceID string) error {
	endpoint := "/me/player/pause"
	if deviceID != "" {
		endpoint += "?device_id=" + deviceID
	}

	resp, err := c.doRequest(ctx, "PUT", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("pause failed: %s - %s", resp.Status, string(body))
	}

	c.InvalidatePlaybackCache()
	return nil
}

// Next skips to next track
func (c *Client) Next(ctx context.Context, deviceID string) error {
	endpoint := "/me/player/next"
	if deviceID != "" {
		endpoint += "?device_id=" + deviceID
	}

	resp, err := c.doRequest(ctx, "POST", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("next failed: %s - %s", resp.Status, string(body))
	}

	c.InvalidatePlaybackCache()
	return nil
}

// Previous skips to previous track
func (c *Client) Previous(ctx context.Context, deviceID string) error {
	endpoint := "/me/player/previous"
	if deviceID != "" {
		endpoint += "?device_id=" + deviceID
	}

	resp, err := c.doRequest(ctx, "POST", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("previous failed: %s - %s", resp.Status, string(body))
	}

	c.InvalidatePlaybackCache()
	return nil
}

// SetVolume sets playback volume (0-100)
func (c *Client) SetVolume(ctx context.Context, deviceID string, volumePercent int) error {
	endpoint := fmt.Sprintf("/me/player/volume?volume_percent=%d", volumePercent)
	if deviceID != "" {
		endpoint += "&device_id=" + deviceID
	}

	resp, err := c.doRequest(ctx, "PUT", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("set volume failed: %s - %s", resp.Status, string(body))
	}

	c.InvalidatePlaybackCache()
	return nil
}

// TransferPlayback transfers playback to a different device
func (c *Client) TransferPlayback(ctx context.Context, deviceID string, play bool) error {
	body := fmt.Sprintf(`{"device_ids":["%s"],"play":%t}`, deviceID, play)

	resp, err := c.doRequest(ctx, "PUT", "/me/player", strings.NewReader(body))
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		respBody, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("transfer playback failed: %s - %s", resp.Status, string(respBody))
	}

	c.InvalidatePlaybackCache()
	return nil
}

// Seek seeks to position in track
func (c *Client) Seek(ctx context.Context, deviceID string, positionMS int) error {
	endpoint := fmt.Sprintf("/me/player/seek?position_ms=%d", positionMS)
	if deviceID != "" {
		endpoint += "&device_id=" + deviceID
	}

	resp, err := c.doRequest(ctx, "PUT", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("seek failed: %s - %s", resp.Status, string(body))
	}

	c.InvalidatePlaybackCache()
	return nil
}

// SetShuffle sets shuffle mode
func (c *Client) SetShuffle(ctx context.Context, deviceID string, state bool) error {
	endpoint := fmt.Sprintf("/me/player/shuffle?state=%t", state)
	if deviceID != "" {
		endpoint += "&device_id=" + deviceID
	}

	resp, err := c.doRequest(ctx, "PUT", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("set shuffle failed: %s - %s", resp.Status, string(body))
	}

	c.InvalidatePlaybackCache()
	return nil
}

// SetRepeat sets repeat mode (track, context, off)
func (c *Client) SetRepeat(ctx context.Context, deviceID string, state string) error {
	endpoint := fmt.Sprintf("/me/player/repeat?state=%s", state)
	if deviceID != "" {
		endpoint += "&device_id=" + deviceID
	}

	resp, err := c.doRequest(ctx, "PUT", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("set repeat failed: %s - %s", resp.Status, string(body))
	}

	c.InvalidatePlaybackCache()
	return nil
}

// GetPlaylists returns the user's playlists
func (c *Client) GetPlaylists(ctx context.Context, limit, offset int) ([]Playlist, int, error) {
	endpoint := fmt.Sprintf("/me/playlists?limit=%d&offset=%d", limit, offset)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, 0, fmt.Errorf("get playlists failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Items []Playlist `json:"items"`
		Total int        `json:"total"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, 0, err
	}

	return result.Items, result.Total, nil
}

// GetPlaylistTracks returns tracks in a playlist
func (c *Client) GetPlaylistTracks(ctx context.Context, playlistID string, limit, offset int) ([]PlaylistTrack, int, error) {
	endpoint := fmt.Sprintf("/playlists/%s/tracks?limit=%d&offset=%d", playlistID, limit, offset)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, 0, fmt.Errorf("get playlist tracks failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Items []PlaylistTrack `json:"items"`
		Total int             `json:"total"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, 0, err
	}

	return result.Items, result.Total, nil
}

// GetRecentlyPlayed returns recently played tracks
func (c *Client) GetRecentlyPlayed(ctx context.Context, limit int) ([]RecentlyPlayedItem, error) {
	endpoint := fmt.Sprintf("/me/player/recently-played?limit=%d", limit)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get recently played failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Items []RecentlyPlayedItem `json:"items"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	return result.Items, nil
}

// GetTopArtists returns user's top artists
func (c *Client) GetTopArtists(ctx context.Context, limit int, timeRange string) ([]Artist, error) {
	if timeRange == "" {
		timeRange = "medium_term"
	}
	endpoint := fmt.Sprintf("/me/top/artists?limit=%d&time_range=%s", limit, timeRange)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get top artists failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Items []Artist `json:"items"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	return result.Items, nil
}

// GetTopTracks returns user's top tracks
func (c *Client) GetTopTracks(ctx context.Context, limit int, timeRange string) ([]Track, error) {
	if timeRange == "" {
		timeRange = "medium_term"
	}
	endpoint := fmt.Sprintf("/me/top/tracks?limit=%d&time_range=%s", limit, timeRange)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get top tracks failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Items []Track `json:"items"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	return result.Items, nil
}

// Search searches for tracks, albums, artists, or playlists
func (c *Client) Search(ctx context.Context, query string, types []string, limit int) (*SearchResults, error) {
	params := url.Values{
		"q":     {query},
		"type":  {strings.Join(types, ",")},
		"limit": {fmt.Sprintf("%d", limit)},
	}

	resp, err := c.doRequest(ctx, "GET", "/search?"+params.Encode(), nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("search failed: %s - %s", resp.Status, string(body))
	}

	var results SearchResults
	if err := json.NewDecoder(resp.Body).Decode(&results); err != nil {
		return nil, err
	}

	return &results, nil
}

// AlbumFull represents a full album with tracks
type AlbumFull struct {
	ID         string   `json:"id"`
	Name       string   `json:"name"`
	URI        string   `json:"uri"`
	Images     []Image  `json:"images"`
	Artists    []Artist `json:"artists"`
	TotalTracks int     `json:"total_tracks"`
	ReleaseDate string  `json:"release_date"`
	Tracks     struct {
		Items []Track `json:"items"`
		Total int     `json:"total"`
	} `json:"tracks"`
}

// ArtistFull represents a full artist with details
type ArtistFull struct {
	ID         string   `json:"id"`
	Name       string   `json:"name"`
	URI        string   `json:"uri"`
	Images     []Image  `json:"images"`
	Genres     []string `json:"genres"`
	Followers  struct {
		Total int `json:"total"`
	} `json:"followers"`
}

// GetAlbum returns album details with tracks
func (c *Client) GetAlbum(ctx context.Context, albumID string) (*AlbumFull, error) {
	endpoint := fmt.Sprintf("/albums/%s", albumID)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get album failed: %s - %s", resp.Status, string(body))
	}

	var album AlbumFull
	if err := json.NewDecoder(resp.Body).Decode(&album); err != nil {
		return nil, err
	}

	return &album, nil
}

// GetArtist returns artist details
func (c *Client) GetArtist(ctx context.Context, artistID string) (*ArtistFull, error) {
	endpoint := fmt.Sprintf("/artists/%s", artistID)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get artist failed: %s - %s", resp.Status, string(body))
	}

	var artist ArtistFull
	if err := json.NewDecoder(resp.Body).Decode(&artist); err != nil {
		return nil, err
	}

	return &artist, nil
}

// GetArtistAlbums returns an artist's albums
func (c *Client) GetArtistAlbums(ctx context.Context, artistID string, limit int) ([]Album, error) {
	endpoint := fmt.Sprintf("/artists/%s/albums?include_groups=album,single&limit=%d", artistID, limit)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get artist albums failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Items []Album `json:"items"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	return result.Items, nil
}

// GetArtistTopTracks returns an artist's top tracks
func (c *Client) GetArtistTopTracks(ctx context.Context, artistID string, market string) ([]Track, error) {
	if market == "" {
		market = "US"
	}
	endpoint := fmt.Sprintf("/artists/%s/top-tracks?market=%s", artistID, market)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get artist top tracks failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Tracks []Track `json:"tracks"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	return result.Tracks, nil
}

// SaveAlbum saves an album to the user's library
func (c *Client) SaveAlbum(ctx context.Context, albumID string) error {
	endpoint := fmt.Sprintf("/me/albums?ids=%s", albumID)

	resp, err := c.doRequest(ctx, "PUT", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("save album failed: %s - %s", resp.Status, string(body))
	}

	return nil
}

// RemoveAlbum removes an album from the user's library
func (c *Client) RemoveAlbum(ctx context.Context, albumID string) error {
	endpoint := fmt.Sprintf("/me/albums?ids=%s", albumID)

	resp, err := c.doRequest(ctx, "DELETE", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("remove album failed: %s - %s", resp.Status, string(body))
	}

	return nil
}

// CheckAlbumSaved checks if an album is saved in the user's library
func (c *Client) CheckAlbumSaved(ctx context.Context, albumID string) (bool, error) {
	endpoint := fmt.Sprintf("/me/albums/contains?ids=%s", albumID)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return false, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return false, fmt.Errorf("check album saved failed: %s - %s", resp.Status, string(body))
	}

	var result []bool
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return false, err
	}

	if len(result) > 0 {
		return result[0], nil
	}
	return false, nil
}

// FollowArtist follows an artist
func (c *Client) FollowArtist(ctx context.Context, artistID string) error {
	endpoint := fmt.Sprintf("/me/following?type=artist&ids=%s", artistID)

	resp, err := c.doRequest(ctx, "PUT", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("follow artist failed: %s - %s", resp.Status, string(body))
	}

	return nil
}

// UnfollowArtist unfollows an artist
func (c *Client) UnfollowArtist(ctx context.Context, artistID string) error {
	endpoint := fmt.Sprintf("/me/following?type=artist&ids=%s", artistID)

	resp, err := c.doRequest(ctx, "DELETE", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("unfollow artist failed: %s - %s", resp.Status, string(body))
	}

	return nil
}

// CheckFollowingArtist checks if the user is following an artist
func (c *Client) CheckFollowingArtist(ctx context.Context, artistID string) (bool, error) {
	endpoint := fmt.Sprintf("/me/following/contains?type=artist&ids=%s", artistID)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return false, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return false, fmt.Errorf("check following artist failed: %s - %s", resp.Status, string(body))
	}

	var result []bool
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return false, err
	}

	if len(result) > 0 {
		return result[0], nil
	}
	return false, nil
}

// SavedAlbum represents an album saved to the user's library
type SavedAlbum struct {
	AddedAt string    `json:"added_at"`
	Album   AlbumFull `json:"album"`
}

// SavedTrack represents a track saved to the user's library
type SavedTrack struct {
	AddedAt string `json:"added_at"`
	Track   Track  `json:"track"`
}

// Show represents a podcast show
type Show struct {
	ID          string  `json:"id"`
	Name        string  `json:"name"`
	Publisher   string  `json:"publisher"`
	Description string  `json:"description"`
	URI         string  `json:"uri"`
	Images      []Image `json:"images"`
}

// SavedShow represents a show saved to the user's library
type SavedShow struct {
	AddedAt string `json:"added_at"`
	Show    Show   `json:"show"`
}

// GetSavedAlbums returns albums saved to the user's library
func (c *Client) GetSavedAlbums(ctx context.Context, limit, offset int) ([]SavedAlbum, int, error) {
	endpoint := fmt.Sprintf("/me/albums?limit=%d&offset=%d", limit, offset)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, 0, fmt.Errorf("get saved albums failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Items []SavedAlbum `json:"items"`
		Total int          `json:"total"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, 0, err
	}

	return result.Items, result.Total, nil
}

// GetFollowedArtists returns artists the user follows
func (c *Client) GetFollowedArtists(ctx context.Context, limit int, after string) ([]Artist, string, error) {
	endpoint := fmt.Sprintf("/me/following?type=artist&limit=%d", limit)
	if after != "" {
		endpoint += "&after=" + after
	}

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, "", fmt.Errorf("get followed artists failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Artists struct {
			Items   []Artist `json:"items"`
			Cursors struct {
				After string `json:"after"`
			} `json:"cursors"`
		} `json:"artists"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, "", err
	}

	return result.Artists.Items, result.Artists.Cursors.After, nil
}

// GetLikedSongs returns the user's liked songs
func (c *Client) GetLikedSongs(ctx context.Context, limit, offset int) ([]SavedTrack, int, error) {
	endpoint := fmt.Sprintf("/me/tracks?limit=%d&offset=%d", limit, offset)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, 0, fmt.Errorf("get liked songs failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Items []SavedTrack `json:"items"`
		Total int          `json:"total"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, 0, err
	}

	return result.Items, result.Total, nil
}

// GetSavedShows returns shows saved to the user's library
func (c *Client) GetSavedShows(ctx context.Context, limit, offset int) ([]SavedShow, int, error) {
	endpoint := fmt.Sprintf("/me/shows?limit=%d&offset=%d", limit, offset)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, 0, fmt.Errorf("get saved shows failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Items []SavedShow `json:"items"`
		Total int         `json:"total"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, 0, err
	}

	return result.Items, result.Total, nil
}

// ===== Queue Operations =====

// QueueItem represents an item in the playback queue
type QueueItem struct {
	Track
	Type string `json:"type"` // "track" or "episode"
}

// Queue represents the user's playback queue
type Queue struct {
	CurrentlyPlaying *Track      `json:"currently_playing"`
	Queue            []QueueItem `json:"queue"`
}

// GetQueue returns the user's current playback queue
func (c *Client) GetQueue(ctx context.Context) (*Queue, error) {
	resp, err := c.doRequest(ctx, "GET", "/me/player/queue", nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNoContent {
		return &Queue{}, nil
	}

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get queue failed: %s - %s", resp.Status, string(body))
	}

	var queue Queue
	if err := json.NewDecoder(resp.Body).Decode(&queue); err != nil {
		return nil, err
	}

	return &queue, nil
}

// AddToQueue adds a track or episode to the playback queue
func (c *Client) AddToQueue(ctx context.Context, uri string, deviceID string) error {
	endpoint := fmt.Sprintf("/me/player/queue?uri=%s", url.QueryEscape(uri))
	if deviceID != "" {
		endpoint += "&device_id=" + deviceID
	}

	resp, err := c.doRequest(ctx, "POST", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("add to queue failed: %s - %s", resp.Status, string(body))
	}

	c.InvalidatePlaybackCache()
	return nil
}

// ===== Recommendations =====

// RecommendationSeed represents seeds for getting recommendations
type RecommendationSeed struct {
	SeedArtists []string // Spotify artist IDs
	SeedGenres  []string // Genre names
	SeedTracks  []string // Spotify track IDs
}

// GetRecommendations returns track recommendations based on seeds
// Limit can be 1-100 (default 20)
func (c *Client) GetRecommendations(ctx context.Context, seeds RecommendationSeed, limit int) ([]Track, error) {
	if limit <= 0 {
		limit = 20
	}
	if limit > 100 {
		limit = 100
	}

	params := url.Values{}
	params.Set("limit", strconv.Itoa(limit))
	params.Set("market", "from_token")

	if len(seeds.SeedArtists) > 0 {
		params.Set("seed_artists", strings.Join(seeds.SeedArtists, ","))
	}
	if len(seeds.SeedGenres) > 0 {
		params.Set("seed_genres", strings.Join(seeds.SeedGenres, ","))
	}
	if len(seeds.SeedTracks) > 0 {
		params.Set("seed_tracks", strings.Join(seeds.SeedTracks, ","))
	}

	// Must have at least one seed
	if len(seeds.SeedArtists)+len(seeds.SeedGenres)+len(seeds.SeedTracks) == 0 {
		return nil, fmt.Errorf("at least one seed (artist, genre, or track) is required")
	}

	resp, err := c.doRequest(ctx, "GET", "/recommendations?"+params.Encode(), nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get recommendations failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Tracks []Track `json:"tracks"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	return result.Tracks, nil
}

// ===== Browse =====

// GetNewReleases returns new album releases
func (c *Client) GetNewReleases(ctx context.Context, limit, offset int) ([]Album, int, error) {
	if limit <= 0 {
		limit = 20
	}
	if limit > 50 {
		limit = 50
	}

	endpoint := fmt.Sprintf("/browse/new-releases?limit=%d&offset=%d", limit, offset)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, 0, fmt.Errorf("get new releases failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Albums struct {
			Items []Album `json:"items"`
			Total int     `json:"total"`
		} `json:"albums"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, 0, err
	}

	return result.Albums.Items, result.Albums.Total, nil
}

// Category represents a Spotify category
type Category struct {
	ID    string  `json:"id"`
	Name  string  `json:"name"`
	Icons []Image `json:"icons"`
}

// GetCategories returns browse categories
func (c *Client) GetCategories(ctx context.Context, limit, offset int) ([]Category, int, error) {
	if limit <= 0 {
		limit = 20
	}
	if limit > 50 {
		limit = 50
	}

	endpoint := fmt.Sprintf("/browse/categories?limit=%d&offset=%d", limit, offset)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, 0, fmt.Errorf("get categories failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Categories struct {
			Items []Category `json:"items"`
			Total int        `json:"total"`
		} `json:"categories"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, 0, err
	}

	return result.Categories.Items, result.Categories.Total, nil
}

// GetCategoryPlaylists returns playlists for a category
func (c *Client) GetCategoryPlaylists(ctx context.Context, categoryID string, limit, offset int) ([]Playlist, int, error) {
	if limit <= 0 {
		limit = 20
	}
	if limit > 50 {
		limit = 50
	}

	endpoint := fmt.Sprintf("/browse/categories/%s/playlists?limit=%d&offset=%d", categoryID, limit, offset)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, 0, fmt.Errorf("get category playlists failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Playlists struct {
			Items []Playlist `json:"items"`
			Total int        `json:"total"`
		} `json:"playlists"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, 0, err
	}

	return result.Playlists.Items, result.Playlists.Total, nil
}

// GetFeaturedPlaylists returns Spotify's featured playlists
func (c *Client) GetFeaturedPlaylists(ctx context.Context, limit, offset int) ([]Playlist, string, error) {
	if limit <= 0 {
		limit = 20
	}
	if limit > 50 {
		limit = 50
	}

	endpoint := fmt.Sprintf("/browse/featured-playlists?limit=%d&offset=%d", limit, offset)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, "", fmt.Errorf("get featured playlists failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Message   string `json:"message"`
		Playlists struct {
			Items []Playlist `json:"items"`
		} `json:"playlists"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, "", err
	}

	return result.Playlists.Items, result.Message, nil
}

// ===== Track Operations =====

// SaveTrack saves a track to the user's library (like a song)
func (c *Client) SaveTrack(ctx context.Context, trackID string) error {
	endpoint := fmt.Sprintf("/me/tracks?ids=%s", trackID)

	resp, err := c.doRequest(ctx, "PUT", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("save track failed: %s - %s", resp.Status, string(body))
	}

	return nil
}

// RemoveTrack removes a track from the user's library
func (c *Client) RemoveTrack(ctx context.Context, trackID string) error {
	endpoint := fmt.Sprintf("/me/tracks?ids=%s", trackID)

	resp, err := c.doRequest(ctx, "DELETE", endpoint, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("remove track failed: %s - %s", resp.Status, string(body))
	}

	return nil
}

// CheckTrackSaved checks if a track is saved in the user's library
func (c *Client) CheckTrackSaved(ctx context.Context, trackID string) (bool, error) {
	endpoint := fmt.Sprintf("/me/tracks/contains?ids=%s", trackID)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return false, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return false, fmt.Errorf("check track saved failed: %s - %s", resp.Status, string(body))
	}

	var result []bool
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return false, err
	}

	if len(result) > 0 {
		return result[0], nil
	}
	return false, nil
}

// CheckTracksSaved checks if multiple tracks are saved in the user's library (max 50 IDs)
func (c *Client) CheckTracksSaved(ctx context.Context, trackIDs []string) (map[string]bool, error) {
	if len(trackIDs) == 0 {
		return make(map[string]bool), nil
	}

	// Spotify API supports max 50 IDs per request
	if len(trackIDs) > 50 {
		trackIDs = trackIDs[:50]
	}

	endpoint := fmt.Sprintf("/me/tracks/contains?ids=%s", strings.Join(trackIDs, ","))

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("check tracks saved failed: %s - %s", resp.Status, string(body))
	}

	var result []bool
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	// Create a map of trackID -> saved status
	savedMap := make(map[string]bool)
	for i, id := range trackIDs {
		if i < len(result) {
			savedMap[id] = result[i]
		}
	}

	return savedMap, nil
}

// ===== Available Genre Seeds =====

// GetAvailableGenreSeeds returns all available genre seeds for recommendations
func (c *Client) GetAvailableGenreSeeds(ctx context.Context) ([]string, error) {
	resp, err := c.doRequest(ctx, "GET", "/recommendations/available-genre-seeds", nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get genre seeds failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Genres []string `json:"genres"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	return result.Genres, nil
}

// ===== Batch Operations (Reduces API calls) =====

// GetTracks returns multiple tracks in a single API call (max 50 IDs)
func (c *Client) GetTracks(ctx context.Context, trackIDs []string) ([]Track, error) {
	if len(trackIDs) == 0 {
		return nil, nil
	}
	if len(trackIDs) > 50 {
		trackIDs = trackIDs[:50]
	}

	endpoint := fmt.Sprintf("/tracks?ids=%s&market=from_token", strings.Join(trackIDs, ","))

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get tracks failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Tracks []Track `json:"tracks"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	return result.Tracks, nil
}

// GetTrack returns a single track by ID
func (c *Client) GetTrack(ctx context.Context, trackID string) (*Track, error) {
	endpoint := fmt.Sprintf("/tracks/%s?market=from_token", trackID)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get track failed: %s - %s", resp.Status, string(body))
	}

	var track Track
	if err := json.NewDecoder(resp.Body).Decode(&track); err != nil {
		return nil, err
	}

	return &track, nil
}

// GetAlbums returns multiple albums in a single API call (max 20 IDs)
func (c *Client) GetAlbums(ctx context.Context, albumIDs []string) ([]AlbumFull, error) {
	if len(albumIDs) == 0 {
		return nil, nil
	}
	if len(albumIDs) > 20 {
		albumIDs = albumIDs[:20]
	}

	endpoint := fmt.Sprintf("/albums?ids=%s&market=from_token", strings.Join(albumIDs, ","))

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get albums failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Albums []AlbumFull `json:"albums"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	return result.Albums, nil
}

// GetArtists returns multiple artists in a single API call (max 50 IDs)
func (c *Client) GetArtists(ctx context.Context, artistIDs []string) ([]ArtistFull, error) {
	if len(artistIDs) == 0 {
		return nil, nil
	}
	if len(artistIDs) > 50 {
		artistIDs = artistIDs[:50]
	}

	endpoint := fmt.Sprintf("/artists?ids=%s", strings.Join(artistIDs, ","))

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get artists failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Artists []ArtistFull `json:"artists"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	return result.Artists, nil
}

// ===== Player Operations =====

// CurrentlyPlaying represents what's currently playing
type CurrentlyPlaying struct {
	Device       *Device `json:"device"`
	ShuffleState bool    `json:"shuffle_state"`
	RepeatState  string  `json:"repeat_state"`
	Timestamp    int64   `json:"timestamp"`
	ProgressMS   int     `json:"progress_ms"`
	IsPlaying    bool    `json:"is_playing"`
	Item         *Track  `json:"item"`
	CurrentlyPlayingType string `json:"currently_playing_type"` // "track", "episode", "ad", "unknown"
}

// GetCurrentlyPlaying returns what's currently playing (lighter than GetPlaybackState)
func (c *Client) GetCurrentlyPlaying(ctx context.Context) (*CurrentlyPlaying, error) {
	resp, err := c.doRequest(ctx, "GET", "/me/player/currently-playing?market=from_token&additional_types=track,episode", nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNoContent {
		return nil, nil
	}

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get currently playing failed: %s - %s", resp.Status, string(body))
	}

	var current CurrentlyPlaying
	if err := json.NewDecoder(resp.Body).Decode(&current); err != nil {
		return nil, err
	}

	return &current, nil
}

// ===== Playlist Details =====

// PlaylistFull represents a full playlist with snapshot_id for efficient caching
type PlaylistFull struct {
	ID          string  `json:"id"`
	Name        string  `json:"name"`
	Description string  `json:"description"`
	URI         string  `json:"uri"`
	Images      []Image `json:"images"`
	SnapshotID  string  `json:"snapshot_id"` // Use to check if playlist changed
	Owner       struct {
		DisplayName string `json:"display_name"`
		ID          string `json:"id"`
	} `json:"owner"`
	Tracks struct {
		Total int             `json:"total"`
		Items []PlaylistTrack `json:"items"`
	} `json:"tracks"`
	Followers struct {
		Total int `json:"total"`
	} `json:"followers"`
	Public       bool `json:"public"`
	Collaborative bool `json:"collaborative"`
}

// GetPlaylist returns full playlist details including snapshot_id
func (c *Client) GetPlaylist(ctx context.Context, playlistID string) (*PlaylistFull, error) {
	endpoint := fmt.Sprintf("/playlists/%s?market=from_token", playlistID)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get playlist failed: %s - %s", resp.Status, string(body))
	}

	var playlist PlaylistFull
	if err := json.NewDecoder(resp.Body).Decode(&playlist); err != nil {
		return nil, err
	}

	return &playlist, nil
}

// ===== Related Artists =====

// GetRelatedArtists returns artists similar to a given artist
func (c *Client) GetRelatedArtists(ctx context.Context, artistID string) ([]Artist, error) {
	endpoint := fmt.Sprintf("/artists/%s/related-artists", artistID)

	resp, err := c.doRequest(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get related artists failed: %s - %s", resp.Status, string(body))
	}

	var result struct {
		Artists []Artist `json:"artists"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	return result.Artists, nil
}
