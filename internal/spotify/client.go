package spotify

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
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
}

// NewClient creates a new Spotify client
func NewClient(clientID, clientSecret, redirectURI string) *Client {
	return &Client{
		clientID:     clientID,
		clientSecret: clientSecret,
		redirectURI:  redirectURI,
		httpClient:   &http.Client{Timeout: 10 * time.Second},
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

	return c.httpClient.Do(req)
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
	ID   string `json:"id"`
	Name string `json:"name"`
	URI  string `json:"uri"`
}

// Album represents an album
type Album struct {
	ID     string   `json:"id"`
	Name   string   `json:"name"`
	URI    string   `json:"uri"`
	Images []Image  `json:"images"`
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

// GetPlaybackState returns the current playback state
func (c *Client) GetPlaybackState(ctx context.Context) (*PlaybackState, error) {
	resp, err := c.doRequest(ctx, "GET", "/me/player", nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	// 204 means no active device
	if resp.StatusCode == http.StatusNoContent {
		return nil, nil
	}

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get playback state failed: %s - %s", resp.Status, string(body))
	}

	var state PlaybackState
	if err := json.NewDecoder(resp.Body).Decode(&state); err != nil {
		return nil, err
	}

	return &state, nil
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
