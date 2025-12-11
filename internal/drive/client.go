package drive

import (
	"context"
	"fmt"
	"io"
	"math/rand"
	"net/http"
	"sync"
	"time"

	"google.golang.org/api/drive/v3"
	"google.golang.org/api/option"
)

// Photo represents a photo from Google Drive
type Photo struct {
	ID           string `json:"id"`
	Name         string `json:"name"`
	MimeType     string `json:"mimeType"`
	ThumbnailURL string `json:"thumbnailUrl,omitempty"`
	WebContentURL string `json:"webContentUrl,omitempty"`
}

// Client handles Google Drive photo operations
type Client struct {
	service              *drive.Service
	screensaverFolderID  string
	backgroundFolderID   string
	screensaverPhotos    []Photo
	backgroundPhotos     []Photo
	mu                   sync.RWMutex
	lastScreensaverFetch time.Time
	lastBackgroundFetch  time.Time
	cacheDuration        time.Duration
}

// NewClient creates a new Drive client
func NewClient(httpClient *http.Client, screensaverFolderID, backgroundFolderID string) (*Client, error) {
	service, err := drive.NewService(context.Background(), option.WithHTTPClient(httpClient))
	if err != nil {
		return nil, fmt.Errorf("failed to create drive service: %w", err)
	}

	return &Client{
		service:             service,
		screensaverFolderID: screensaverFolderID,
		backgroundFolderID:  backgroundFolderID,
		cacheDuration:       5 * time.Minute,
	}, nil
}

// fetchPhotosFromFolder fetches all image files from a folder
func (c *Client) fetchPhotosFromFolder(ctx context.Context, folderID string) ([]Photo, error) {
	if folderID == "" {
		return nil, nil
	}

	query := fmt.Sprintf("'%s' in parents and mimeType contains 'image/' and trashed = false", folderID)

	var photos []Photo
	pageToken := ""

	for {
		call := c.service.Files.List().
			Q(query).
			Fields("nextPageToken, files(id, name, mimeType, thumbnailLink, webContentLink)").
			PageSize(100)

		if pageToken != "" {
			call = call.PageToken(pageToken)
		}

		result, err := call.Context(ctx).Do()
		if err != nil {
			return nil, fmt.Errorf("failed to list files: %w", err)
		}

		for _, file := range result.Files {
			photos = append(photos, Photo{
				ID:           file.Id,
				Name:         file.Name,
				MimeType:     file.MimeType,
				ThumbnailURL: file.ThumbnailLink,
				WebContentURL: file.WebContentLink,
			})
		}

		pageToken = result.NextPageToken
		if pageToken == "" {
			break
		}
	}

	return photos, nil
}

// GetScreensaverPhotos returns cached screensaver photos, refreshing if needed
func (c *Client) GetScreensaverPhotos(ctx context.Context) ([]Photo, error) {
	c.mu.RLock()
	if time.Since(c.lastScreensaverFetch) < c.cacheDuration && len(c.screensaverPhotos) > 0 {
		photos := c.screensaverPhotos
		c.mu.RUnlock()
		return photos, nil
	}
	c.mu.RUnlock()

	c.mu.Lock()
	defer c.mu.Unlock()

	// Double-check after acquiring write lock
	if time.Since(c.lastScreensaverFetch) < c.cacheDuration && len(c.screensaverPhotos) > 0 {
		return c.screensaverPhotos, nil
	}

	photos, err := c.fetchPhotosFromFolder(ctx, c.screensaverFolderID)
	if err != nil {
		return nil, err
	}

	c.screensaverPhotos = photos
	c.lastScreensaverFetch = time.Now()
	return photos, nil
}

// GetBackgroundPhotos returns cached background photos, refreshing if needed
func (c *Client) GetBackgroundPhotos(ctx context.Context) ([]Photo, error) {
	c.mu.RLock()
	if time.Since(c.lastBackgroundFetch) < c.cacheDuration && len(c.backgroundPhotos) > 0 {
		photos := c.backgroundPhotos
		c.mu.RUnlock()
		return photos, nil
	}
	c.mu.RUnlock()

	c.mu.Lock()
	defer c.mu.Unlock()

	// Double-check after acquiring write lock
	if time.Since(c.lastBackgroundFetch) < c.cacheDuration && len(c.backgroundPhotos) > 0 {
		return c.backgroundPhotos, nil
	}

	photos, err := c.fetchPhotosFromFolder(ctx, c.backgroundFolderID)
	if err != nil {
		return nil, err
	}

	c.backgroundPhotos = photos
	c.lastBackgroundFetch = time.Now()
	return photos, nil
}

// GetRandomScreensaverPhoto returns a random screensaver photo
func (c *Client) GetRandomScreensaverPhoto(ctx context.Context) (*Photo, error) {
	photos, err := c.GetScreensaverPhotos(ctx)
	if err != nil {
		return nil, err
	}
	if len(photos) == 0 {
		return nil, nil
	}
	return &photos[rand.Intn(len(photos))], nil
}

// GetRandomBackgroundPhoto returns a random background photo
func (c *Client) GetRandomBackgroundPhoto(ctx context.Context) (*Photo, error) {
	photos, err := c.GetBackgroundPhotos(ctx)
	if err != nil {
		return nil, err
	}
	if len(photos) == 0 {
		return nil, nil
	}
	return &photos[rand.Intn(len(photos))], nil
}

// GetPhotoURL returns a direct URL to view a photo (only works for public files)
func (c *Client) GetPhotoURL(photoID string) string {
	return fmt.Sprintf("https://drive.google.com/uc?export=view&id=%s", photoID)
}

// GetFileContent downloads a file's content using OAuth authentication
func (c *Client) GetFileContent(ctx context.Context, fileID string) ([]byte, string, error) {
	resp, err := c.service.Files.Get(fileID).Context(ctx).Download()
	if err != nil {
		return nil, "", fmt.Errorf("failed to download file: %w", err)
	}
	defer resp.Body.Close()

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, "", fmt.Errorf("failed to read file content: %w", err)
	}

	contentType := resp.Header.Get("Content-Type")
	if contentType == "" {
		contentType = "application/octet-stream"
	}

	return data, contentType, nil
}

// HasScreensaverFolder returns true if screensaver folder is configured
func (c *Client) HasScreensaverFolder() bool {
	return c.screensaverFolderID != ""
}

// HasBackgroundFolder returns true if background folder is configured
func (c *Client) HasBackgroundFolder() bool {
	return c.backgroundFolderID != ""
}
