package icons

import (
	"embed"
	"io/fs"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/go-chi/chi/v5"
)

//go:embed svg/*.svg
var svgFS embed.FS

// DevIconsPath is the filesystem path to check for icons during development.
// Set via ICONS_DEV_PATH env var. If empty or path doesn't exist, uses embedded.
var DevIconsPath = os.Getenv("ICONS_DEV_PATH")

// Handler returns an http.HandlerFunc that serves SVG icons
func Handler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		name := chi.URLParam(r, "name")
		if name == "" {
			http.Error(w, "icon name required", http.StatusBadRequest)
			return
		}

		// Sanitize the name - only allow alphanumeric and hyphens
		name = strings.TrimSuffix(name, ".svg")
		for _, c := range name {
			if !((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-') {
				http.Error(w, "invalid icon name", http.StatusBadRequest)
				return
			}
		}

		var data []byte
		var err error

		// Try filesystem first if DevIconsPath is set
		if DevIconsPath != "" {
			fsPath := filepath.Join(DevIconsPath, name+".svg")
			data, err = os.ReadFile(fsPath)
		}

		// Fall back to embedded if filesystem failed or not configured
		if data == nil || err != nil {
			data, err = fs.ReadFile(svgFS, filepath.Join("svg", name+".svg"))
		}

		if err != nil {
			http.Error(w, "icon not found", http.StatusNotFound)
			return
		}

		w.Header().Set("Content-Type", "image/svg+xml")
		// Short cache for dev, long for prod
		if DevIconsPath != "" {
			w.Header().Set("Cache-Control", "no-cache")
		} else {
			w.Header().Set("Cache-Control", "public, max-age=31536000")
		}
		w.Write(data)
	}
}

// List returns all available icon names
func List() ([]string, error) {
	entries, err := fs.ReadDir(svgFS, "svg")
	if err != nil {
		return nil, err
	}

	var names []string
	for _, e := range entries {
		if !e.IsDir() && strings.HasSuffix(e.Name(), ".svg") {
			names = append(names, strings.TrimSuffix(e.Name(), ".svg"))
		}
	}
	return names, nil
}
