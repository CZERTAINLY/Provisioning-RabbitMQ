# CZERTAINLY-rabbitmq-bootstrap

The **rabbitmq-bootstrap** is a proxy provisioning service designed as part of the [CZERTAINLY](https://github.com/CZERTAINLY/CZERTAINLY) platform. Its primary purpose is to provision and decommission RabbitMQ queues and bindings for proxy instances, and to generate signed JWT configuration tokens with installation instructions for those proxies.

## Table of Contents

*   [Overview](#overview)
*   [Prerequisites](#prerequisites)
*   [Configuration](#configuration)
*   [Build and Run](#build-and-run)
*   [Docker](#docker)
*   [Contributing](#contributing)
*   [License](#license)

## Overview

The service runs as a web application and exposes REST endpoints for:
1. **Provisioning proxies** - Creating RabbitMQ queues and bindings for a proxy instance
2. **Decommissioning proxies** - Removing RabbitMQ queues for a proxy instance
3. **Installation instructions** - Generating signed JWT configuration tokens and rendering install commands (e.g., Helm)

All endpoints require authentication via the `X-API-Key` header.

### REST Endpoints

All endpoints return **JSON responses** for errors. Authentication is required via `X-API-Key` header.

#### `POST /api/v1/proxies`
Provisions a new proxy instance by creating the required RabbitMQ queue and bindings.

**Headers:**
- `X-API-Key` (required) - API key for authentication

**Request Body:**
```json
{
  "proxyCode": "MY_PROXY_1"
}
```

**Responses:**
- `201 Created` — proxy provisioned successfully (empty body)
- `400 Bad Request` — invalid request
- `401 Unauthorized` — missing or invalid API key
- `409 Conflict` — proxy already exists

---

#### `DELETE /api/v1/proxies/{proxyCode}`
Decommissions an existing proxy instance by removing its RabbitMQ queue.

**Headers:**
- `X-API-Key` (required) - API key for authentication

**Path Parameter:**
- `proxyCode` — unique proxy code identifier

**Responses:**
- `204 No Content` — proxy decommissioned successfully
- `401 Unauthorized` — missing or invalid API key
- `404 Not Found` — proxy not found

---

#### `GET /api/v1/proxies/{proxyCode}/installation`
Returns installation instructions for an existing proxy. Generates a signed JWT configuration token and renders it into the requested install format.

**Headers:**
- `X-API-Key` (required) - API key for authentication

**Path Parameter:**
- `proxyCode` — unique proxy code identifier

**Query Parameters:**
- `format` (required) — installation format; currently supported: `helm`

**Response (Success - 200 OK):**
```json
{
  "command": {
    "shell": "helm repo add czertainly https://cloudfieldcz.github.io/CZERTAINLY-Helm-Charts\nhelm repo update\n\nhelm install proxy czertainly/proxy --set token=\"<jwt-token>\""
  }
}
```

**Responses:**
- `200 OK` — installation instructions returned
- `401 Unauthorized` — missing or invalid API key
- `404 Not Found` — proxy not found

---

**Error response format (all endpoints):**
```json
{
  "error": "descriptive error message"
}
```

## Prerequisites

*   **Java 21** or higher
*   **Maven 3.8+**
*   Running instance of **RabbitMQ** with AMQP access (port 5672) and a configured virtual host

## Configuration

The application is configured via `application.yml` and environment variables.

## Environment Variables

| Variable                  | Description                                                                               | Required                                           | Default value               |
|---------------------------|-------------------------------------------------------------------------------------------|----------------------------------------------------|-----------------------------|
| `PORT`                    | Application port                                                                          | ![](https://img.shields.io/badge/-NO-red.svg)      | `8080`                      |
| `RABBITMQ_HOST`           | RabbitMQ hostname                                                                         | ![](https://img.shields.io/badge/-NO-red.svg)      | `localhost`                 |
| `RABBITMQ_PORT`           | RabbitMQ AMQP port                                                                        | ![](https://img.shields.io/badge/-NO-red.svg)      | `5672`                      |
| `RABBITMQ_USERNAME`       | RabbitMQ username with permission to manage queues and bindings in the virtual host       | ![](https://img.shields.io/badge/-YES-success.svg) | `provisioner`               |
| `RABBITMQ_PASSWORD`       | RabbitMQ password                                                                         | ![](https://img.shields.io/badge/-YES-success.svg) | N/A                         |
| `RABBITMQ_VIRTUAL_HOST`   | RabbitMQ virtual host                                                                     | ![](https://img.shields.io/badge/-NO-red.svg)      | `czertainly`                |
| `SECURITY_API_KEY`        | API key required in the `X-API-Key` header for all requests                               | ![](https://img.shields.io/badge/-YES-success.svg) | N/A                         |
| `PROXY_AMQP_URL`          | External AMQP URL that provisioned proxies use to connect (may differ from internal host) | ![](https://img.shields.io/badge/-NO-red.svg)      | `amqp://localhost:5672`     |
| `PROXY_RABBITMQ_USERNAME` | AMQP username embedded in the proxy configuration token                                   | ![](https://img.shields.io/badge/-NO-red.svg)      | same as `RABBITMQ_USERNAME` |
| `PROXY_RABBITMQ_PASSWORD` | AMQP password embedded in the proxy configuration token                                   | ![](https://img.shields.io/badge/-NO-red.svg)      | same as `RABBITMQ_PASSWORD` |
| `PROXY_EXCHANGE`          | Exchange name used for proxy communication                                                | ![](https://img.shields.io/badge/-NO-red.svg)      | `czertainly-proxy`          |
| `PROXY_RESPONSE_QUEUE`    | Core response queue name                                                                  | ![](https://img.shields.io/badge/-NO-red.svg)      | `core`                      |
| `TOKEN_SIGNING_KEY`       | HMAC-SHA256 signing key for JWT configuration tokens (minimum 32 characters)              | ![](https://img.shields.io/badge/-YES-success.svg) | N/A                         |

## Build and Run

### Build
```bash
mvn clean package
```

### Run locally
```bash
# Set required environment variables
export RABBITMQ_USERNAME=provisioner
export RABBITMQ_PASSWORD=provisioner
export SECURITY_API_KEY=my-secret-api-key
export TOKEN_SIGNING_KEY=my-signing-key-at-least-32-characters-long

# Run the application
java -jar target/rabbitBootstrap-1.0-SNAPSHOT.jar
```

The application will start on port 8080 (configurable via `PORT` environment variable).

### Using the endpoints

Provision a proxy:
```bash
curl -X POST "http://localhost:8080/api/v1/proxies" \
  -H "X-API-Key: my-secret-api-key" \
  -H "Content-Type: application/json" \
  -d '{"proxyCode": "MY_PROXY_1"}'
```

Get installation instructions (Helm):
```bash
curl "http://localhost:8080/api/v1/proxies/MY_PROXY_1/installation?format=helm" \
  -H "X-API-Key: my-secret-api-key"
```

Example response:
```json
{
  "command": {
    "shell": "# Add the Helm repository\nhelm repo add czertainly https://cloudfieldcz.github.io/CZERTAINLY-Helm-Charts\nhelm repo update\n\n# Install the chart\nhelm install proxy czertainly/proxy --set token=\"<jwt-token>\""
  }
}
```

Decommission a proxy:
```bash
curl -X DELETE "http://localhost:8080/api/v1/proxies/MY_PROXY_1" \
  -H "X-API-Key: my-secret-api-key"
```

## Docker

### Build Docker image
```bash
docker build -t czertainly/rabbitmq-bootstrap:latest .
```

### Run with Docker
```bash
docker run -d \
  --name rabbitmq-bootstrap \
  -p 8080:8080 \
  -e RABBITMQ_HOST=rabbitmq \
  -e RABBITMQ_USERNAME=provisioner \
  -e RABBITMQ_PASSWORD=provisioner \
  -e RABBITMQ_VIRTUAL_HOST=czertainly \
  -e SECURITY_API_KEY=my-secret-api-key \
  -e PROXY_AMQP_URL=amqp://rabbitmq:5672 \
  -e TOKEN_SIGNING_KEY=my-signing-key-at-least-32-characters-long \
  czertainly/rabbitmq-bootstrap:latest
```

### Docker Compose

The service expects an external RabbitMQ instance. Create a `.env` file with your connection details:

```bash
RABBITMQ_HOST=your-rabbitmq-host
RABBITMQ_USERNAME=provisioner
RABBITMQ_PASSWORD=provisioner
RABBITMQ_VIRTUAL_HOST=czertainly
SECURITY_API_KEY=my-secret-api-key
PROXY_AMQP_URL=amqp://your-rabbitmq-host:5672
TOKEN_SIGNING_KEY=my-signing-key-at-least-32-characters-long
```

Then run with Docker Compose:

```bash
docker-compose up -d
```

Check logs:
```bash
docker-compose logs -f rabbitmq-bootstrap
```

Stop the service:
```bash
docker-compose down
```
