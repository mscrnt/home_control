package calendar

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"sort"
	"sync"
	"time"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
	gcal "google.golang.org/api/calendar/v3"
	"google.golang.org/api/option"
)

type Client struct {
	config      *oauth2.Config
	tokenFile   string
	service     *gcal.Service
	calendarIDs []string
	timezone    *time.Location

	// Event cache
	cacheMu     sync.RWMutex
	eventCache  map[string]*eventCacheEntry
	cacheTTL    time.Duration
}

type eventCacheEntry struct {
	events    []*Event
	fetchedAt time.Time
}

type Event struct {
	ID          string    `json:"id"`
	CalendarID  string    `json:"calendarId,omitempty"`
	Title       string    `json:"title"`
	Start       time.Time `json:"start"`
	End         time.Time `json:"end"`
	AllDay      bool      `json:"allDay"`
	Location    string    `json:"location,omitempty"`
	Description string    `json:"description,omitempty"`
	Color       string    `json:"color,omitempty"`
	ColorID     string    `json:"colorId,omitempty"`
	Recurring   bool      `json:"recurring,omitempty"`
	HTMLLink    string    `json:"htmlLink,omitempty"`
}

// CalendarColors holds the color definitions from Google Calendar
type CalendarColors struct {
	Calendar map[string]ColorDefinition `json:"calendar"`
	Event    map[string]ColorDefinition `json:"event"`
}

// ColorDefinition represents a single color option
type ColorDefinition struct {
	Background string `json:"background"`
	Foreground string `json:"foreground"`
}

// CalendarInfo contains basic info about a calendar
type CalendarInfo struct {
	ID    string `json:"id"`
	Name  string `json:"name"`
	Color string `json:"color,omitempty"`
}

func NewClient(clientID, clientSecret, redirectURL, tokenFile string, calendarIDs []string, timezone *time.Location) *Client {
	config := &oauth2.Config{
		ClientID:     clientID,
		ClientSecret: clientSecret,
		RedirectURL:  redirectURL,
		Scopes: []string{
			gcal.CalendarScope,
			"https://www.googleapis.com/auth/tasks",
			"https://www.googleapis.com/auth/drive.readonly",
		},
		Endpoint: google.Endpoint,
	}

	return &Client{
		config:      config,
		tokenFile:   tokenFile,
		calendarIDs: calendarIDs,
		timezone:    timezone,
		eventCache:  make(map[string]*eventCacheEntry),
		cacheTTL:    60 * time.Second, // Cache for 60 seconds
	}
}

// GetAuthURL returns the URL for user to authorize the app
func (c *Client) GetAuthURL(state string) string {
	return c.config.AuthCodeURL(state, oauth2.AccessTypeOffline, oauth2.ApprovalForce)
}

// ExchangeCode exchanges the authorization code for a token
func (c *Client) ExchangeCode(ctx context.Context, code string) error {
	token, err := c.config.Exchange(ctx, code)
	if err != nil {
		return fmt.Errorf("failed to exchange code: %w", err)
	}

	if err := c.saveToken(token); err != nil {
		return fmt.Errorf("failed to save token: %w", err)
	}

	return c.initService(ctx, token)
}

// IsAuthorized checks if we have a valid token
func (c *Client) IsAuthorized() bool {
	token, err := c.loadToken()
	if err != nil {
		return false
	}
	return token.Valid() || token.RefreshToken != ""
}

// Init initializes the calendar service with stored token
func (c *Client) Init(ctx context.Context) error {
	token, err := c.loadToken()
	if err != nil {
		return fmt.Errorf("no stored token: %w", err)
	}

	log.Printf("Token loaded: AccessToken=%d chars, RefreshToken=%d chars, Expiry=%v, TokenType=%s",
		len(token.AccessToken), len(token.RefreshToken), token.Expiry, token.TokenType)

	if len(c.calendarIDs) > 0 {
		log.Printf("Configured calendars: %v", c.calendarIDs)
		log.Printf("Events will be created on: %s", c.calendarIDs[0])
	} else {
		log.Printf("No calendars configured, using 'primary' for all operations")
	}

	return c.initService(ctx, token)
}

func (c *Client) initService(ctx context.Context, token *oauth2.Token) error {
	client := c.config.Client(ctx, token)
	service, err := gcal.NewService(ctx, option.WithHTTPClient(client))
	if err != nil {
		return fmt.Errorf("failed to create calendar service: %w", err)
	}
	c.service = service
	return nil
}

// GetHTTPClient returns an OAuth2 HTTP client for use with other Google APIs
func (c *Client) GetHTTPClient(ctx context.Context) (*http.Client, error) {
	token, err := c.loadToken()
	if err != nil {
		return nil, fmt.Errorf("no stored token: %w", err)
	}
	return c.config.Client(ctx, token), nil
}

// GetUpcomingEvents fetches events from configured calendars (or all if none specified)
func (c *Client) GetUpcomingEvents(ctx context.Context, days int) ([]*Event, error) {
	if c.service == nil {
		return nil, fmt.Errorf("calendar service not initialized")
	}

	now := time.Now()
	end := now.AddDate(0, 0, days)

	// Build list of calendar IDs to fetch
	type calendarInfo struct {
		ID    string
		Color string
	}
	var calendars []calendarInfo

	if len(c.calendarIDs) > 0 {
		// Use explicitly configured calendars
		for _, id := range c.calendarIDs {
			calendars = append(calendars, calendarInfo{ID: id, Color: "#4285f4"})
		}
		log.Printf("Fetching events from %d configured calendars", len(calendars))
	} else {
		// Get all calendars from the user's list
		calList, err := c.service.CalendarList.List().Do()
		if err != nil {
			return nil, fmt.Errorf("failed to list calendars: %w", err)
		}
		for _, cal := range calList.Items {
			color := cal.BackgroundColor
			if color == "" {
				color = "#4285f4"
			}
			calendars = append(calendars, calendarInfo{ID: cal.Id, Color: color})
		}
		log.Printf("Fetching events from %d calendars in user's list", len(calendars))
	}

	var result []*Event

	// Fetch events from each calendar
	for _, cal := range calendars {
		events, err := c.service.Events.List(cal.ID).
			ShowDeleted(false).
			SingleEvents(true).
			TimeMin(now.Format(time.RFC3339)).
			TimeMax(end.Format(time.RFC3339)).
			MaxResults(50).
			OrderBy("startTime").
			Do()
		if err != nil {
			log.Printf("Failed to fetch events from calendar %s: %v", cal.ID, err)
			continue
		}

		for _, item := range events.Items {
			result = append(result, c.convertGoogleEvent(item, cal.ID, cal.Color))
		}
	}

	// Sort all events by start time
	sort.Slice(result, func(i, j int) bool {
		return result[i].Start.Before(result[j].Start)
	})

	return result, nil
}

// GetEventsInRange fetches events within a specific date range
func (c *Client) GetEventsInRange(ctx context.Context, start, end time.Time) ([]*Event, error) {
	if c.service == nil {
		return nil, fmt.Errorf("calendar service not initialized")
	}

	// Check cache first
	cacheKey := fmt.Sprintf("%s_%s", start.Format("2006-01-02"), end.Format("2006-01-02"))
	c.cacheMu.RLock()
	if entry, ok := c.eventCache[cacheKey]; ok {
		if time.Since(entry.fetchedAt) < c.cacheTTL {
			c.cacheMu.RUnlock()
			return entry.events, nil
		}
	}
	c.cacheMu.RUnlock()

	// Build list of calendar IDs to fetch
	type calendarInfo struct {
		ID    string
		Color string
	}
	var calendars []calendarInfo

	if len(c.calendarIDs) > 0 {
		for _, id := range c.calendarIDs {
			calendars = append(calendars, calendarInfo{ID: id, Color: "#4285f4"})
		}
	} else {
		calList, err := c.service.CalendarList.List().Do()
		if err != nil {
			return nil, fmt.Errorf("failed to list calendars: %w", err)
		}
		for _, cal := range calList.Items {
			color := cal.BackgroundColor
			if color == "" {
				color = "#4285f4"
			}
			calendars = append(calendars, calendarInfo{ID: cal.Id, Color: color})
		}
	}

	var result []*Event

	for _, cal := range calendars {
		events, err := c.service.Events.List(cal.ID).
			ShowDeleted(false).
			SingleEvents(true).
			TimeMin(start.Format(time.RFC3339)).
			TimeMax(end.Format(time.RFC3339)).
			MaxResults(100).
			OrderBy("startTime").
			Do()
		if err != nil {
			log.Printf("Failed to fetch events from calendar %s: %v", cal.ID, err)
			continue
		}

		for _, item := range events.Items {
			result = append(result, c.convertGoogleEvent(item, cal.ID, cal.Color))
		}
	}

	sort.Slice(result, func(i, j int) bool {
		return result[i].Start.Before(result[j].Start)
	})

	// Store in cache
	c.cacheMu.Lock()
	c.eventCache[cacheKey] = &eventCacheEntry{
		events:    result,
		fetchedAt: time.Now(),
	}
	c.cacheMu.Unlock()

	return result, nil
}

// InvalidateCache clears the event cache
func (c *Client) InvalidateCache() {
	c.cacheMu.Lock()
	c.eventCache = make(map[string]*eventCacheEntry)
	c.cacheMu.Unlock()
}

// CreateEventOptions holds optional fields for event creation
type CreateEventOptions struct {
	Location    string
	Description string
	Recurrence  []string // RRULE strings
	ColorID     string   // Event color ID (1-11)
}

// UpdateEventOptions holds fields that can be updated
type UpdateEventOptions struct {
	Title       *string
	Start       *time.Time
	End         *time.Time
	AllDay      *bool
	Location    *string
	Description *string
	ColorID     *string
	Recurrence  []string
}

// convertGoogleEvent converts a Google Calendar event to our Event struct
func (c *Client) convertGoogleEvent(item *gcal.Event, calendarID, defaultColor string) *Event {
	event := &Event{
		ID:          item.Id,
		CalendarID:  calendarID,
		Title:       item.Summary,
		Location:    item.Location,
		Description: item.Description,
		Color:       defaultColor,
		ColorID:     item.ColorId,
		Recurring:   item.RecurringEventId != "" || len(item.Recurrence) > 0,
		HTMLLink:    item.HtmlLink,
	}

	// Get timezone for all-day event parsing
	loc := c.timezone
	if loc == nil {
		loc = time.Local
	}

	// Parse start time
	if item.Start != nil {
		if item.Start.DateTime != "" {
			t, _ := time.Parse(time.RFC3339, item.Start.DateTime)
			event.Start = t
			event.AllDay = false
		} else if item.Start.Date != "" {
			// All-day events should be parsed in local timezone
			t, _ := time.ParseInLocation("2006-01-02", item.Start.Date, loc)
			event.Start = t
			event.AllDay = true
		}
	}

	// Parse end time
	if item.End != nil {
		if item.End.DateTime != "" {
			t, _ := time.Parse(time.RFC3339, item.End.DateTime)
			event.End = t
		} else if item.End.Date != "" {
			// All-day events should be parsed in local timezone
			t, _ := time.ParseInLocation("2006-01-02", item.End.Date, loc)
			event.End = t
		}
	}

	// Override with event-specific color if set
	if item.ColorId != "" {
		event.Color = getEventColor(item.ColorId)
	}

	return event
}

// CreateEvent creates a new event on the first configured calendar or primary
func (c *Client) CreateEvent(ctx context.Context, title string, start, end time.Time, allDay bool, opts *CreateEventOptions) (*Event, error) {
	if c.service == nil {
		return nil, fmt.Errorf("calendar service not initialized")
	}

	event := &gcal.Event{
		Summary: title,
	}

	if opts != nil {
		if opts.Location != "" {
			event.Location = opts.Location
		}
		if opts.Description != "" {
			event.Description = opts.Description
		}
		if len(opts.Recurrence) > 0 {
			event.Recurrence = opts.Recurrence
		}
		if opts.ColorID != "" {
			event.ColorId = opts.ColorID
		}
	}

	// Get timezone name - ensure it's a valid IANA timezone
	tzName := start.Location().String()
	if tzName == "Local" {
		tzName = "UTC"
	}

	if allDay {
		event.Start = &gcal.EventDateTime{Date: start.Format("2006-01-02")}
		event.End = &gcal.EventDateTime{Date: end.Format("2006-01-02")}
	} else {
		event.Start = &gcal.EventDateTime{
			DateTime: start.Format(time.RFC3339),
			TimeZone: tzName,
		}
		event.End = &gcal.EventDateTime{
			DateTime: end.Format(time.RFC3339),
			TimeZone: tzName,
		}
	}

	// Determine which calendar to create the event on
	calendarID := "primary"
	if len(c.calendarIDs) > 0 {
		calendarID = c.calendarIDs[0]
	}

	log.Printf("Creating event on calendar %q: %s on %s (allDay=%v, tz=%s)", calendarID, title, start, allDay, tzName)
	log.Printf("Event data: Start=%+v, End=%+v, Recurrence=%v", event.Start, event.End, event.Recurrence)

	created, err := c.service.Events.Insert(calendarID, event).Do()
	if err != nil {
		return nil, fmt.Errorf("failed to create event: %w", err)
	}

	log.Printf("Event created successfully: ID=%s, Status=%s, CalendarID=%s, HTMLLink=%s", created.Id, created.Status, calendarID, created.HtmlLink)

	// Verify the event was actually created by fetching it back
	verified, err := c.service.Events.Get(calendarID, created.Id).Do()
	if err != nil {
		log.Printf("WARNING: Could not verify event creation: %v", err)
	} else {
		log.Printf("Event verified: ID=%s, Summary=%s, Status=%s, Start=%+v", verified.Id, verified.Summary, verified.Status, verified.Start)
	}

	c.InvalidateCache()
	return c.convertGoogleEvent(created, calendarID, "#4285f4"), nil
}

// GetEvent fetches a single event by ID
func (c *Client) GetEvent(ctx context.Context, calendarID, eventID string) (*Event, error) {
	if c.service == nil {
		return nil, fmt.Errorf("calendar service not initialized")
	}

	if calendarID == "" {
		calendarID = c.getDefaultCalendarID()
	}

	log.Printf("GetEvent: calendarID=%q, eventID=%q", calendarID, eventID)

	event, err := c.service.Events.Get(calendarID, eventID).Do()
	if err != nil {
		log.Printf("GetEvent: Event not found on calendar %q, trying primary", calendarID)
		// Try primary calendar as fallback
		event, err = c.service.Events.Get("primary", eventID).Do()
		if err != nil {
			return nil, fmt.Errorf("event not found on calendar %q or primary: %w", calendarID, err)
		}
		calendarID = "primary"
		log.Printf("GetEvent: Found event on primary calendar")
	}

	return c.convertGoogleEvent(event, calendarID, "#4285f4"), nil
}

// UpdateEvent performs a full update of an event (replaces all fields)
func (c *Client) UpdateEvent(ctx context.Context, calendarID, eventID string, title string, start, end time.Time, allDay bool, opts *CreateEventOptions) (*Event, error) {
	if c.service == nil {
		return nil, fmt.Errorf("calendar service not initialized")
	}

	if calendarID == "" {
		calendarID = c.getDefaultCalendarID()
	}

	log.Printf("UpdateEvent: calendarID=%q, eventID=%q", calendarID, eventID)

	// First verify the event exists
	existing, err := c.service.Events.Get(calendarID, eventID).Do()
	if err != nil {
		log.Printf("UpdateEvent: Event not found on calendar %q, trying primary", calendarID)
		// Try primary calendar as fallback
		existing, err = c.service.Events.Get("primary", eventID).Do()
		if err != nil {
			return nil, fmt.Errorf("event not found on calendar %q or primary: %w", calendarID, err)
		}
		// Event exists on primary, use that instead
		calendarID = "primary"
		log.Printf("UpdateEvent: Found event on primary calendar")
	}
	log.Printf("UpdateEvent: Found existing event: ID=%s, Summary=%s", existing.Id, existing.Summary)

	// Modify the existing event rather than creating a new one
	// This preserves all fields that we're not explicitly changing
	existing.Summary = title

	if opts != nil {
		existing.Location = opts.Location
		existing.Description = opts.Description
		if len(opts.Recurrence) > 0 {
			existing.Recurrence = opts.Recurrence
		}
		if opts.ColorID != "" {
			existing.ColorId = opts.ColorID
		}
	}

	tzName := start.Location().String()
	if tzName == "Local" {
		tzName = "UTC"
	}

	if allDay {
		existing.Start = &gcal.EventDateTime{Date: start.Format("2006-01-02")}
		existing.End = &gcal.EventDateTime{Date: end.Format("2006-01-02")}
	} else {
		existing.Start = &gcal.EventDateTime{
			DateTime: start.Format(time.RFC3339),
			TimeZone: tzName,
		}
		existing.End = &gcal.EventDateTime{
			DateTime: end.Format(time.RFC3339),
			TimeZone: tzName,
		}
	}

	updated, err := c.service.Events.Update(calendarID, eventID, existing).Do()
	if err != nil {
		return nil, fmt.Errorf("failed to update event: %w", err)
	}

	log.Printf("Event updated: ID=%s, Status=%s", updated.Id, updated.Status)
	c.InvalidateCache()
	return c.convertGoogleEvent(updated, calendarID, "#4285f4"), nil
}

// PatchEvent performs a partial update of an event (only specified fields)
func (c *Client) PatchEvent(ctx context.Context, calendarID, eventID string, opts *UpdateEventOptions) (*Event, error) {
	if c.service == nil {
		return nil, fmt.Errorf("calendar service not initialized")
	}

	if calendarID == "" {
		calendarID = c.getDefaultCalendarID()
	}

	log.Printf("PatchEvent: calendarID=%q, eventID=%q", calendarID, eventID)

	// First verify the event exists on specified calendar
	_, err := c.service.Events.Get(calendarID, eventID).Do()
	if err != nil {
		log.Printf("PatchEvent: Event not found on calendar %q, trying primary", calendarID)
		// Try primary calendar as fallback
		_, err = c.service.Events.Get("primary", eventID).Do()
		if err != nil {
			return nil, fmt.Errorf("event not found on calendar %q or primary: %w", calendarID, err)
		}
		calendarID = "primary"
		log.Printf("PatchEvent: Found event on primary calendar")
	}

	// Build the patch event with only specified fields
	event := &gcal.Event{}

	if opts.Title != nil {
		event.Summary = *opts.Title
	}
	if opts.Location != nil {
		event.Location = *opts.Location
	}
	if opts.Description != nil {
		event.Description = *opts.Description
	}
	if opts.ColorID != nil {
		event.ColorId = *opts.ColorID
	}
	if len(opts.Recurrence) > 0 {
		event.Recurrence = opts.Recurrence
	}

	// Handle time updates
	if opts.Start != nil || opts.End != nil || opts.AllDay != nil {
		// We need the existing event to handle partial time updates
		existing, err := c.service.Events.Get(calendarID, eventID).Do()
		if err != nil {
			return nil, fmt.Errorf("failed to get existing event: %w", err)
		}

		start := existing.Start
		end := existing.End

		if opts.Start != nil {
			tzName := opts.Start.Location().String()
			if tzName == "Local" {
				tzName = "UTC"
			}
			if opts.AllDay != nil && *opts.AllDay {
				start = &gcal.EventDateTime{Date: opts.Start.Format("2006-01-02")}
			} else {
				start = &gcal.EventDateTime{
					DateTime: opts.Start.Format(time.RFC3339),
					TimeZone: tzName,
				}
			}
		}

		if opts.End != nil {
			tzName := opts.End.Location().String()
			if tzName == "Local" {
				tzName = "UTC"
			}
			if opts.AllDay != nil && *opts.AllDay {
				end = &gcal.EventDateTime{Date: opts.End.Format("2006-01-02")}
			} else {
				end = &gcal.EventDateTime{
					DateTime: opts.End.Format(time.RFC3339),
					TimeZone: tzName,
				}
			}
		}

		event.Start = start
		event.End = end
	}

	patched, err := c.service.Events.Patch(calendarID, eventID, event).Do()
	if err != nil {
		return nil, fmt.Errorf("failed to patch event: %w", err)
	}

	log.Printf("Event patched: ID=%s, Status=%s", patched.Id, patched.Status)
	c.InvalidateCache()
	return c.convertGoogleEvent(patched, calendarID, "#4285f4"), nil
}

// MoveEvent moves an event to a different calendar
func (c *Client) MoveEvent(ctx context.Context, sourceCalendarID, eventID, destinationCalendarID string) (*Event, error) {
	if c.service == nil {
		return nil, fmt.Errorf("calendar service not initialized")
	}

	if sourceCalendarID == "" {
		sourceCalendarID = c.getDefaultCalendarID()
	}

	moved, err := c.service.Events.Move(sourceCalendarID, eventID, destinationCalendarID).Do()
	if err != nil {
		return nil, fmt.Errorf("failed to move event: %w", err)
	}

	log.Printf("Event moved: ID=%s from %s to %s", moved.Id, sourceCalendarID, destinationCalendarID)
	c.InvalidateCache()
	return c.convertGoogleEvent(moved, destinationCalendarID, "#4285f4"), nil
}

// DeleteEvent removes an event from a calendar
func (c *Client) DeleteEvent(ctx context.Context, calendarID, eventID string) error {
	if c.service == nil {
		return fmt.Errorf("calendar service not initialized")
	}

	if calendarID == "" {
		calendarID = c.getDefaultCalendarID()
	}

	log.Printf("DeleteEvent: calendarID=%q, eventID=%q", calendarID, eventID)

	// First verify the event exists on specified calendar
	_, err := c.service.Events.Get(calendarID, eventID).Do()
	if err != nil {
		log.Printf("DeleteEvent: Event not found on calendar %q, trying primary", calendarID)
		// Try primary calendar as fallback
		_, err = c.service.Events.Get("primary", eventID).Do()
		if err != nil {
			return fmt.Errorf("event not found on calendar %q or primary: %w", calendarID, err)
		}
		// Event exists on primary, use that instead
		calendarID = "primary"
		log.Printf("DeleteEvent: Found event on primary calendar")
	}

	if err := c.service.Events.Delete(calendarID, eventID).Do(); err != nil {
		return fmt.Errorf("failed to delete event: %w", err)
	}

	log.Printf("Event deleted: ID=%s from calendar %s", eventID, calendarID)
	c.InvalidateCache()
	return nil
}

// GetEventInstances returns instances of a recurring event
func (c *Client) GetEventInstances(ctx context.Context, calendarID, eventID string, timeMin, timeMax time.Time) ([]*Event, error) {
	if c.service == nil {
		return nil, fmt.Errorf("calendar service not initialized")
	}

	if calendarID == "" {
		calendarID = c.getDefaultCalendarID()
	}

	call := c.service.Events.Instances(calendarID, eventID)
	if !timeMin.IsZero() {
		call = call.TimeMin(timeMin.Format(time.RFC3339))
	}
	if !timeMax.IsZero() {
		call = call.TimeMax(timeMax.Format(time.RFC3339))
	}

	instances, err := call.Do()
	if err != nil {
		return nil, fmt.Errorf("failed to get event instances: %w", err)
	}

	var result []*Event
	for _, item := range instances.Items {
		result = append(result, c.convertGoogleEvent(item, calendarID, "#4285f4"))
	}

	return result, nil
}

// GetColors returns the available calendar and event colors
func (c *Client) GetColors(ctx context.Context) (*CalendarColors, error) {
	if c.service == nil {
		return nil, fmt.Errorf("calendar service not initialized")
	}

	colors, err := c.service.Colors.Get().Do()
	if err != nil {
		return nil, fmt.Errorf("failed to get colors: %w", err)
	}

	result := &CalendarColors{
		Calendar: make(map[string]ColorDefinition),
		Event:    make(map[string]ColorDefinition),
	}

	for id, color := range colors.Calendar {
		result.Calendar[id] = ColorDefinition{
			Background: color.Background,
			Foreground: color.Foreground,
		}
	}

	for id, color := range colors.Event {
		result.Event[id] = ColorDefinition{
			Background: color.Background,
			Foreground: color.Foreground,
		}
	}

	return result, nil
}

// getDefaultCalendarID returns the first configured calendar or "primary"
func (c *Client) getDefaultCalendarID() string {
	if len(c.calendarIDs) > 0 {
		return c.calendarIDs[0]
	}
	return "primary"
}

// GetCalendarList returns all calendars the user has access to
func (c *Client) GetCalendarList(ctx context.Context) ([]CalendarInfo, error) {
	if c.service == nil {
		return nil, fmt.Errorf("calendar service not initialized")
	}

	list, err := c.service.CalendarList.List().Do()
	if err != nil {
		return nil, err
	}

	var calendars []CalendarInfo
	for _, item := range list.Items {
		calendars = append(calendars, CalendarInfo{
			ID:    item.Id,
			Name:  item.Summary,
			Color: item.BackgroundColor,
		})
	}
	return calendars, nil
}

// GetConfiguredCalendars returns the calendars configured in GOOGLE_CALENDARS with their names
func (c *Client) GetConfiguredCalendars(ctx context.Context) ([]CalendarInfo, error) {
	if c.service == nil {
		return nil, fmt.Errorf("calendar service not initialized")
	}

	// Get all calendars to look up names
	list, err := c.service.CalendarList.List().Do()
	if err != nil {
		return nil, err
	}

	// Create a map of ID -> CalendarInfo for quick lookup
	calMap := make(map[string]CalendarInfo)
	for _, item := range list.Items {
		calMap[item.Id] = CalendarInfo{
			ID:    item.Id,
			Name:  item.Summary,
			Color: item.BackgroundColor,
		}
	}

	var result []CalendarInfo

	if len(c.calendarIDs) > 0 {
		// Return only configured calendars
		for _, id := range c.calendarIDs {
			if info, ok := calMap[id]; ok {
				result = append(result, info)
			} else {
				// Calendar not in user's list - fetch metadata directly via Calendars API
				cal, err := c.service.Calendars.Get(id).Do()
				if err != nil {
					log.Printf("Could not fetch calendar metadata for %s: %v", id, err)
					// Fall back to using ID as name
					result = append(result, CalendarInfo{
						ID:    id,
						Name:  id,
						Color: "#4285f4",
					})
				} else {
					result = append(result, CalendarInfo{
						ID:    id,
						Name:  cal.Summary,
						Color: "#4285f4",
					})
				}
			}
		}
	} else {
		// No explicit config - return all calendars
		for _, item := range list.Items {
			result = append(result, CalendarInfo{
				ID:    item.Id,
				Name:  item.Summary,
				Color: item.BackgroundColor,
			})
		}
	}

	return result, nil
}

func (c *Client) saveToken(token *oauth2.Token) error {
	f, err := os.Create(c.tokenFile)
	if err != nil {
		return err
	}
	defer f.Close()
	return json.NewEncoder(f).Encode(token)
}

func (c *Client) loadToken() (*oauth2.Token, error) {
	f, err := os.Open(c.tokenFile)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	var token oauth2.Token
	if err := json.NewDecoder(f).Decode(&token); err != nil {
		return nil, err
	}
	return &token, nil
}

// ClearToken removes the stored token to force re-authorization
func (c *Client) ClearToken() error {
	c.service = nil
	err := os.Remove(c.tokenFile)
	if err != nil && !os.IsNotExist(err) {
		return err
	}
	log.Println("Token cleared, re-authorization required")
	return nil
}

func getEventColor(colorID string) string {
	// Google Calendar color IDs mapped to hex colors
	colors := map[string]string{
		"1":  "#7986cb", // Lavender
		"2":  "#33b679", // Sage
		"3":  "#8e24aa", // Grape
		"4":  "#e67c73", // Flamingo
		"5":  "#f6c026", // Banana
		"6":  "#f5511d", // Tangerine
		"7":  "#039be5", // Peacock
		"8":  "#616161", // Graphite
		"9":  "#3f51b5", // Blueberry
		"10": "#0b8043", // Basil
		"11": "#d60000", // Tomato
	}
	if c, ok := colors[colorID]; ok {
		return c
	}
	return "#4285f4" // Default Google blue
}

// Middleware to check authorization
func (c *Client) RequireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !c.IsAuthorized() {
			http.Redirect(w, r, "/auth/google", http.StatusTemporaryRedirect)
			return
		}
		next.ServeHTTP(w, r)
	})
}

// HandleAuth starts the OAuth flow
func (c *Client) HandleAuth(w http.ResponseWriter, r *http.Request) {
	state := "home-control-auth"
	url := c.GetAuthURL(state)
	log.Printf("Redirecting to Google OAuth: %s", url)
	http.Redirect(w, r, url, http.StatusTemporaryRedirect)
}

// HandleCallback handles the OAuth callback
func (c *Client) HandleCallback(w http.ResponseWriter, r *http.Request) {
	code := r.URL.Query().Get("code")
	if code == "" {
		http.Error(w, "Missing authorization code", http.StatusBadRequest)
		return
	}

	if err := c.ExchangeCode(r.Context(), code); err != nil {
		log.Printf("Failed to exchange code: %v", err)
		http.Error(w, "Failed to authorize: "+err.Error(), http.StatusInternalServerError)
		return
	}

	log.Println("Google Calendar authorized successfully")
	http.Redirect(w, r, "/calendar", http.StatusTemporaryRedirect)
}
