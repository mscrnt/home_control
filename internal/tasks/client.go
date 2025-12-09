package tasks

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"sort"
	"time"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
	"google.golang.org/api/option"
	gtasks "google.golang.org/api/tasks/v1"
)

// emptyString is used for clearing the Completed field
var emptyString = ""

type Client struct {
	config    *oauth2.Config
	tokenFile string
	service   *gtasks.Service
	timezone  *time.Location
}

// Task represents a Google Task
type Task struct {
	ID        string    `json:"id"`
	ListID    string    `json:"listId"`
	Title     string    `json:"title"`
	Notes     string    `json:"notes"`
	Due       time.Time `json:"due,omitempty"`
	Completed bool      `json:"completed"`
	Position  string    `json:"position"`
}

// TaskList represents a Google Tasks list
type TaskList struct {
	ID    string `json:"id"`
	Title string `json:"title"`
}

// NewClient creates a tasks client that shares the OAuth token with calendar
func NewClient(clientID, clientSecret, tokenFile string, timezone *time.Location) *Client {
	config := &oauth2.Config{
		ClientID:     clientID,
		ClientSecret: clientSecret,
		Scopes: []string{
			"https://www.googleapis.com/auth/tasks",
		},
		Endpoint: google.Endpoint,
	}

	client := &Client{
		config:    config,
		tokenFile: tokenFile,
		timezone:  timezone,
	}

	return client
}

// Init initializes the tasks service with the shared OAuth token
func (c *Client) Init(ctx context.Context) error {
	token, err := c.loadToken()
	if err != nil {
		return fmt.Errorf("failed to load token: %w", err)
	}

	httpClient := c.config.Client(ctx, token)
	service, err := gtasks.NewService(ctx, option.WithHTTPClient(httpClient))
	if err != nil {
		return fmt.Errorf("failed to create tasks service: %w", err)
	}
	c.service = service
	return nil
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

func (c *Client) IsAuthorized() bool {
	return c.service != nil
}

// resolveListID converts "@default" or empty string to the actual list ID
func (c *Client) resolveListID(listID string) (string, error) {
	if listID != "" && listID != "@default" {
		return listID, nil
	}

	// Get the first task list (which is the default)
	lists, err := c.service.Tasklists.List().MaxResults(1).Do()
	if err != nil {
		return "", fmt.Errorf("failed to get default list: %w", err)
	}
	if len(lists.Items) == 0 {
		return "", fmt.Errorf("no task lists found")
	}
	return lists.Items[0].Id, nil
}

// GetTaskLists returns all task lists
func (c *Client) GetTaskLists(ctx context.Context) ([]TaskList, error) {
	if c.service == nil {
		return nil, fmt.Errorf("tasks service not initialized")
	}

	lists, err := c.service.Tasklists.List().Do()
	if err != nil {
		return nil, fmt.Errorf("failed to list task lists: %w", err)
	}

	var result []TaskList
	for _, list := range lists.Items {
		result = append(result, TaskList{
			ID:    list.Id,
			Title: list.Title,
		})
	}
	return result, nil
}

// GetTasks returns all tasks from a list (or default list if empty)
func (c *Client) GetTasks(ctx context.Context, listID string) ([]Task, error) {
	if c.service == nil {
		return nil, fmt.Errorf("tasks service not initialized")
	}

	// Resolve @default to actual list ID
	resolvedListID, err := c.resolveListID(listID)
	if err != nil {
		return nil, err
	}

	tasks, err := c.service.Tasks.List(resolvedListID).
		ShowCompleted(true).
		ShowHidden(false).
		MaxResults(100).
		Do()
	if err != nil {
		return nil, fmt.Errorf("failed to list tasks: %w", err)
	}

	var result []Task
	for _, t := range tasks.Items {
		task := Task{
			ID:        t.Id,
			ListID:    resolvedListID,
			Title:     t.Title,
			Notes:     t.Notes,
			Completed: t.Status == "completed",
			Position:  t.Position,
		}

		if t.Due != "" {
			// Google Tasks due dates are stored as midnight UTC (YYYY-MM-DDT00:00:00.000Z)
			// but represent a date, not a specific time. Parse just the date portion
			// to avoid timezone shift issues.
			if len(t.Due) >= 10 {
				dateStr := t.Due[:10] // Extract YYYY-MM-DD
				loc := c.timezone
				if loc == nil {
					loc = time.Local
				}
				if due, err := time.ParseInLocation("2006-01-02", dateStr, loc); err == nil {
					task.Due = due
				}
			}
		}

		result = append(result, task)
	}

	// Sort: incomplete first (by position), then completed
	sort.Slice(result, func(i, j int) bool {
		if result[i].Completed != result[j].Completed {
			return !result[i].Completed // incomplete tasks first
		}
		return result[i].Position < result[j].Position
	})

	return result, nil
}

// CreateTask creates a new task
func (c *Client) CreateTask(ctx context.Context, listID, title, notes string, due *time.Time) (*Task, error) {
	if c.service == nil {
		return nil, fmt.Errorf("tasks service not initialized")
	}

	// Resolve @default to actual list ID
	resolvedListID, err := c.resolveListID(listID)
	if err != nil {
		return nil, err
	}

	task := &gtasks.Task{
		Title: title,
		Notes: notes,
	}

	if due != nil {
		task.Due = due.Format(time.RFC3339)
	}

	created, err := c.service.Tasks.Insert(resolvedListID, task).Do()
	if err != nil {
		return nil, fmt.Errorf("failed to create task: %w", err)
	}

	result := &Task{
		ID:        created.Id,
		ListID:    resolvedListID,
		Title:     created.Title,
		Notes:     created.Notes,
		Completed: created.Status == "completed",
		Position:  created.Position,
	}

	if created.Due != "" {
		if d, err := time.Parse(time.RFC3339, created.Due); err == nil {
			result.Due = d
		}
	}

	log.Printf("Task created: ID=%s, Title=%s", result.ID, result.Title)
	return result, nil
}

// ToggleTask toggles the completed status of a task
func (c *Client) ToggleTask(ctx context.Context, listID, taskID string) (*Task, error) {
	if c.service == nil {
		return nil, fmt.Errorf("tasks service not initialized")
	}

	// Resolve @default to actual list ID
	resolvedListID, err := c.resolveListID(listID)
	if err != nil {
		return nil, err
	}

	// Get current task
	existing, err := c.service.Tasks.Get(resolvedListID, taskID).Do()
	if err != nil {
		return nil, fmt.Errorf("failed to get task: %w", err)
	}

	// Toggle status
	if existing.Status == "completed" {
		existing.Status = "needsAction"
		existing.Completed = &emptyString
	} else {
		existing.Status = "completed"
	}

	updated, err := c.service.Tasks.Update(resolvedListID, taskID, existing).Do()
	if err != nil {
		return nil, fmt.Errorf("failed to update task: %w", err)
	}

	result := &Task{
		ID:        updated.Id,
		ListID:    resolvedListID,
		Title:     updated.Title,
		Notes:     updated.Notes,
		Completed: updated.Status == "completed",
		Position:  updated.Position,
	}

	log.Printf("Task toggled: ID=%s, Completed=%v", result.ID, result.Completed)
	return result, nil
}

// DeleteTask deletes a task
func (c *Client) DeleteTask(ctx context.Context, listID, taskID string) error {
	if c.service == nil {
		return fmt.Errorf("tasks service not initialized")
	}

	// Resolve @default to actual list ID
	resolvedListID, err := c.resolveListID(listID)
	if err != nil {
		return err
	}

	if err := c.service.Tasks.Delete(resolvedListID, taskID).Do(); err != nil {
		return fmt.Errorf("failed to delete task: %w", err)
	}

	log.Printf("Task deleted: ID=%s", taskID)
	return nil
}

// ClearCompleted removes all completed tasks from a list
func (c *Client) ClearCompleted(ctx context.Context, listID string) error {
	if c.service == nil {
		return fmt.Errorf("tasks service not initialized")
	}

	// Resolve @default to actual list ID
	resolvedListID, err := c.resolveListID(listID)
	if err != nil {
		return err
	}

	if err := c.service.Tasks.Clear(resolvedListID).Do(); err != nil {
		return fmt.Errorf("failed to clear completed tasks: %w", err)
	}

	log.Printf("Cleared completed tasks from list: %s", resolvedListID)
	return nil
}
