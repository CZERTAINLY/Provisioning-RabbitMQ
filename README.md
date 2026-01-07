# CZERTAINLY-rabbitmq-bootstrap

The **rabbitmq-bootstrap** is a utility service designed as part of the [CZERTAINLY](https://github.com/CZERTAINLY/CZERTAINLY) platform. Its primary purpose is to initialize, configure, and import definitions (exchanges, queues, bindings) into the RabbitMQ instance used by the platform and adding new Proxy to the RabbitMQ broker.

## Table of Contents

*   [Overview](#overview)
*   [Prerequisites](#prerequisites)
*   [Configuration](#configuration)
*   [Build and Run](#build-and-run)
*   [Docker](#docker)
*   [Contributing](#contributing)
*   [License](#license)

## Overview

**Note**: For now there is no authentication for the service.

The service runs as a web application and exposes REST endpoints for:
1. **Importing definitions** - Bulk creation of exchanges, queues, and bindings from a JSON file
2. **Managing proxies** - Creating proxy communication infrastructure for CZERTAINLY connectors

### REST Endpoints

All endpoints return **JSON responses** (both success and error cases).

#### `/api/import-definitions` (PUT)
Imports definitions from the file specified in `application.yml` (by default it is `definitions.json`) or from a custom JSON provided in the request body.
Format of Definitions is described in section **'Import file'**.

**Query Parameters:**
- `username` (optional) - Username for vhost permissions. Required when definitions contain non-default vhosts (other than "/").

**Note:** The service automatically creates vhosts defined in the definitions file and sets up user permissions.

**Response (Success - 200 OK):**
```json
{
  "stats": {
    "VHOST": {
      "czertainly": "201 - Created"
    },
    "VHOSTRIGHTS": {
      "czertainly-admin": "201 - Created"
    },
    "EXCHANGE": {
      "my-exchange": "201 - Created"
    },
    "QUEUE": {
      "my-queue": "204 - Already exists"
    },
    "BINDING": {
      "my-exchange-routing.key-my-queue": "201 - Created"
    }
  }
}
```

**Response (Error - 400/500):**
```json
{
  "error": "Invalid JSON: Unexpected character..."
}
```

#### `/api/add-proxy` (PUT)
Adds a proxy to the RabbitMQ broker.

**Query Parameters:**
- `proxyName` (required) - Name of the proxy (1-255 characters, alphanumeric with underscores and hyphens)
- `vhost` (optional, default: "/") - Virtual host name
- `username` (optional) - Username for vhost permissions. Required when vhost is not "/".

**Note:** The service automatically creates the vhost if it doesn't exist and sets up user permissions.

**Response (Success - 200 OK):**
```json
{
  "stats": {
    "VHOST": {
      "czertainly": "201 - Created"
    },
    "VHOSTRIGHTS": {
      "czertainly-admin": "201 - Created"
    },
    "EXCHANGE": {
      "proxy": "204 - Already exists"
    },
    "QUEUE": {
      "my-proxy": "201 - Created",
      "proxy-response": "204 - Already exists"
    },
    "BINDING": {
      "proxy-request.my-proxy-my-proxy": "201 - Created",
      "proxy-response.*-proxy-response": "204 - Already exists"
    }
  }
}
```

**Response (Error - 400/500):**
```json
{
  "error": "proxyName must be 1-255 characters and contain only letters, numbers, underscores and hyphens"
}
``` 

## Prerequisites

*   **Java 21** or higher
*   **Maven 3.8+**
*   Running instance of **RabbitMQ**

## Configuration

The application is configured via `application.yml` and a specific definitions file.

### RabbitMQ Connection

Configure the connection details in `src/main/resources/application.yml` (or override them via environment variables):

## Environment Variables
in this table you can find all environment variables that can be used to override the default values in `application.yml`

| Variable           | Description                                                                                                   | Required                                           | Default value            |
|--------------------|---------------------------------------------------------------------------------------------------------------|----------------------------------------------------|--------------------------|
| `PORT`             | Application port                                                                                              | ![](https://img.shields.io/badge/-NO-red.svg)      | `8077`                   |
| `RABBITMQ_URL`     | RabbitMQ Management API URL with port (e.g. http://localhost:15672)                                           | ![](https://img.shields.io/badge/-NO-red.svg)      | `http://localhost:15672` |
| `USERNAME`         | RabbitMQ admin username with rights for creating vhosts, queues, exchanges and bindings                       | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`                    |
| `PASSWORD`         | RabbitMQ admin password                                                                                       | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`                    |
| `DEFINITIONS_FILE` | JSON file with definitions of queues, exchanges and bindings [doc](https://www.rabbitmq.com/docs/definitions) | ![](https://img.shields.io/badge/-NO-red.svg)      | `definitions.json`       |


## Import file
A path to the import file is defined in application.yml (RabbitMQ file [doc](https://www.rabbitmq.com/docs/definitions))
You can export your definitions from RabbitMQ Management UI or via API (``curl -u username:userpass http://your-rabbitmq:15672/api/definitions``)

## Build and Run

### Build
```bash
mvn clean package
```

### Run locally
```bash
# Set required environment variables
export USERNAME=admin
export PASSWORD=admin

# Run the application
java -jar target/rabbitBootstrap-1.0-SNAPSHOT.jar
```

The application will start on port 8077 (configurable via PORT environment variable).

### Using the endpoints

Import definitions (from default file):
```bash
curl -X PUT "http://localhost:8077/api/import-definitions?username=admin"
```

Import definitions (with custom JSON):
```bash
curl -X PUT "http://localhost:8077/api/import-definitions?username=admin" \
  -H "Content-Type: application/json" \
  -d @my-definitions.json
```

Add a proxy (default vhost "/"):
```bash
curl -X PUT "http://localhost:8077/api/add-proxy?proxyName=connector-x509"
```

Add a proxy (custom vhost):
```bash
curl -X PUT "http://localhost:8077/api/add-proxy?proxyName=connector-x509&vhost=czertainly&username=admin"
```

Example response:
```json
{
  "stats": {
    "VHOST": {
      "czertainly": "201 - Created"
    },
    "VHOSTRIGHTS": {
      "czertainly-admin": "201 - Created"
    },
    "EXCHANGE": {
      "proxy": "201 - Created"
    },
    "QUEUE": {
      "connector-x509": "201 - Created",
      "proxy-response": "201 - Created"
    },
    "BINDING": {
      "proxy-request.connector-x509-connector-x509": "201 - Created",
      "proxy-response.*-proxy-response": "201 - Created"
    }
  }
}
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
  -p 8077:8077 \
  -e USERNAME=admin \
  -e PASSWORD=admin \
  -e RABBITMQ_URL=http://rabbitmq:15672 \
  czertainly/rabbitmq-bootstrap:latest
```

### Run with custom definitions file
If you want to use a custom definitions file, mount it as a volume:

```bash
docker run -d \
  --name rabbitmq-bootstrap \
  -p 8077:8077 \
  -e USERNAME=admin \
  -e PASSWORD=admin \
  -e RABBITMQ_URL=http://rabbitmq:15672 \
  -e DEFINITIONS_FILE=/app/config/custom-definitions.json \
  -v $(pwd)/custom-definitions.json:/app/config/custom-definitions.json:ro \
  czertainly/rabbitmq-bootstrap:latest
```

### Docker Compose

The service expects an external RabbitMQ instance. Create a `.env` file with your RabbitMQ connection details:

```bash
# Edit .env with your RabbitMQ connection details
# USERNAME=admin
# PASSWORD=admin
# RABBITMQ_URL=http://your-rabbitmq-host:15672
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

### Health check
The Docker image includes a health check endpoint via Spring Boot Actuator:

```bash
curl http://localhost:8077/actuator/health
```