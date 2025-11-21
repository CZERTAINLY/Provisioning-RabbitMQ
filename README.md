# CZERTAINLY-rabbitmq-bootstrap

The **rabbitmq-bootstrap** is a utility service designed as part of the [CZERTAINLY](https://github.com/CZERTAINLY/CZERTAINLY) platform. Its primary purpose is to initialize, configure, and import definitions (exchanges, queues, bindings) into the RabbitMQ instance used by the platform.

## Table of Contents

*   [Overview](#overview)
*   [Prerequisites](#prerequisites)
*   [Configuration](#configuration)
*   [Build and Run](#build-and-run)
*   [Docker](#docker)
*   [Contributing](#contributing)
*   [License](#license)

## Overview

This application automates the setup of the messaging infrastructure required for CZERTAINLY microservices to communicate. It reads a definition file (JSON) and applies the configuration to the target RabbitMQ server using Spring WebFlux and RabbitMQ client libraries.

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

| Variable             | Description                                                      | Required                                           | Default value |
|----------------------|------------------------------------------------------------------|----------------------------------------------------|---------------|
| `RABBITMQ_URL`       | RabbitMQ URL with port (f.e. http://localhost:15672)             | ![](https://img.shields.io/badge/-NO-red.svg)      | `http://localhost:15672`         |
| `USERNAME`           | Username with rights for creating queues, exchanges and bindings | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`         |
| `PASSWORD`           | Its password                                                     | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`         |
| `VHOST`              | RabbitMQ vhost                                                   | ![](https://img.shields.io/badge/-NO-red.svg)      | `/`           |
| `DEFINITIONS_FILE`   | json file with definitions of queues, exchanges and bindings [doc](https://www.rabbitmq.com/docs/definitions) | ![](https://img.shields.io/badge/-NO-red.svg)      | `definitions.json`         |


## Import file
A path to the import file is defined in application.yml (RabbitMQ file [doc](https://www.rabbitmq.com/docs/definitions))
You can export your definitions from RabbitMQ Management UI or via API (``curl -u username:userpass http://your-rabbitmq:15672/api/definitions``)