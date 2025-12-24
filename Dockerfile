# Build stage
FROM golang:1.24-alpine AS builder

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOTOOLCHAIN=auto go build -ldflags="-s -w" -o server ./cmd/server

# Runtime stage
FROM alpine:3.21

RUN apk --no-cache add ca-certificates tzdata android-tools nmap

WORKDIR /app

COPY --from=builder /app/server .
COPY templates ./templates
COPY static ./static

EXPOSE 8080

CMD ["./server"]
