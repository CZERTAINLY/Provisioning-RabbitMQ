package com.czertainly.rabbitImporter.service;

import com.czertainly.rabbitImporter.config.RabbitMQProperties;
import com.czertainly.rabbitImporter.model.OperationResult;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import static org.slf4j.LoggerFactory.getLogger;

@Service
public class RabbitApiService {

    private static final Logger logger = getLogger(RabbitApiService.class);

    private final RabbitMQProperties rabbitMQProperties;
    private final RestClient restClient;

    public RabbitApiService(RabbitMQProperties rabbitMQProperties, RestClient restClient) {
        this.rabbitMQProperties = rabbitMQProperties;
        this.restClient = restClient;
    }

    public void createVhostIfNotExist(String vhost, OperationResult stats) {
        if ("/".equals(vhost)) {
            logger.info("Vhost '/' already exists, skipping creation");
            return;
        }
        logger.info("Creating vhost: {}", vhost);
        String body = """
                {
                  "description": "automatically created by rabbitmq-bootstrap",
                  "default_queue_type": "quorum",
                  "protected_from_deletion": false
                }
                """;

        ResponseEntity<String> response = callPut(body, rabbitMQProperties.managementUrl() + "/api/vhosts/{vhost}", vhost);
        HttpStatusCode statusCode = response.getStatusCode();
        if (statusCode.isSameCodeAs(HttpStatus.CREATED)) {
            stats.addStats(OperationResult.ObjectType.VHOST, vhost, formatStatus(statusCode));
        } else if (statusCode.isSameCodeAs(HttpStatus.NO_CONTENT)) {
            stats.addStats(OperationResult.ObjectType.VHOST, vhost, formatStatus(statusCode) + " (already exists)");
        } else {
            logger.warn("Unexpected status code {} for vhost creation: {}", statusCode, vhost);
        }
    }

    public void createUserRightsForVhost(String vhost, String username, OperationResult stats) {
        logger.info("Creating user rights for vhost: {}, username: {}", vhost, username);
        String body = """
                {
                    "configure": "^$",
                    "write": ".*",
                    "read": ".*"
                  }
                """;
        ResponseEntity<String> response = callPut(body, rabbitMQProperties.managementUrl() + "/api/permissions/{vhost}/{user}", vhost, username);
        HttpStatusCode statusCode = response.getStatusCode();

        if (statusCode.isSameCodeAs(HttpStatus.CREATED)) {
            stats.addStats(OperationResult.ObjectType.VHOSTRIGHTS, vhost + "-" + username, formatStatus(statusCode));
            logger.info("User rights created: vhost={}, username={}", vhost, username);
        } else {
            logger.warn("Unexpected status code {} for user rights creation: vhost={}, username={}", statusCode.value(), vhost, username);
        }
    }

    public void createBindingIfNotExist(String exchangeName, String routingKey, String queueName, String vhost, OperationResult stats) {
        logger.info("Creating binding for exchange: {}, routingKey: {}, queueName: {}, vhost: {}", exchangeName, routingKey, queueName, vhost);
        String body = """
                {
                  "routing_key": "%s"
                }
                """.formatted(routingKey);

        ResponseEntity<String> response = callPost(body, rabbitMQProperties.managementUrl() + "/api/bindings/{vhost}/e/{exchange}/q/{queue}", vhost, exchangeName, queueName);
        HttpStatusCode statusCode = response.getStatusCode();

        if (statusCode.isSameCodeAs(HttpStatus.CREATED)) {
            stats.addStats(OperationResult.ObjectType.BINDING, vhost + " | " + exchangeName + " | " + queueName + " | " + routingKey, formatStatus(statusCode));
            logger.info("Binding created: exchange={}, routingKey={}, queue={}, vhost={}", exchangeName, routingKey, queueName, vhost);
        } else {
            logger.warn("Unexpected status code {} for binding creation: exchange={}, routingKey={}, queue={}, vhost={}",
                    statusCode.value(), exchangeName, routingKey, queueName, vhost);
        }
    }

    public void createQueueIfNotExist(String queueName, String vhost, OperationResult stats) {
        logger.info("Creating queue: {}, vhost: {}", queueName, vhost);

        String body = """
                {
                   "auto_delete": false,
                   "durable": true,
                   "arguments": {
                      "x-queue-type": "classic"
                   }
                }
                """;

        ResponseEntity<String> response = callPut(body, rabbitMQProperties.managementUrl() + "/api/queues/{vhost}/{name}", vhost, queueName);
        HttpStatusCode statusCode = response.getStatusCode();

        if (statusCode.isSameCodeAs(HttpStatus.CREATED)) {
            stats.addStats(OperationResult.ObjectType.QUEUE, queueName, formatStatus(statusCode));
            logger.info("Queue created: name={}, vhost={}", queueName, vhost);
        } else if (statusCode.isSameCodeAs(HttpStatus.NO_CONTENT)) {
            stats.addStats(OperationResult.ObjectType.QUEUE, queueName, formatStatus(statusCode) + " - (created)");
            logger.info("Queue already exists: name={}, vhost={}", queueName, vhost);
        } else {
            logger.warn("Unexpected status code {} for queue creation: name={}, vhost={}", statusCode.value(), queueName, vhost);
        }
    }

    public void createExchangeIfNotExist(String exchangeName, String vhost, OperationResult stats) {
        logger.info("Creating exchange: {}, vhost: {}", exchangeName, vhost);
        String body = """
                {
                  "type": "topic",
                  "auto_delete": false,
                  "durable": true,
                  "internal": false,
                  "arguments": {}
                }
                """;

        ResponseEntity<String> response = callPut(body, rabbitMQProperties.managementUrl() + "/api/exchanges/{vhost}/{name}", vhost, exchangeName);
        HttpStatusCode statusCode = response.getStatusCode();

        if (statusCode.isSameCodeAs(HttpStatus.CREATED)) {
            stats.addStats(OperationResult.ObjectType.EXCHANGE, exchangeName, formatStatus(statusCode));
            logger.info("Exchange created: name={}, vhost={}", exchangeName, vhost);
        } else if (statusCode.isSameCodeAs(HttpStatus.NO_CONTENT)) {
            stats.addStats(OperationResult.ObjectType.EXCHANGE, exchangeName, formatStatus(statusCode) + " - (created)");
            logger.info("Exchange already exists: name={}, vhost={}", exchangeName, vhost);
        } else {
            logger.warn("Unexpected status code {} for exchange creation: name={}, vhost={}", statusCode.value(), exchangeName, vhost);
        }
    }

    private ResponseEntity<String> callPut(String body, String uri, Object... uriArgs) {
        try {
            return restClient.put()
                    .uri(uri, uriArgs)
                    .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, responseError) -> {
                        String errorBody = new String(responseError.getBody().readAllBytes());
                        logger.error("RabbitMQ API client error (PUT): status={}, uri={}, response={}",
                                responseError.getStatusCode(), uri, errorBody);
                        throw new RabbitConfigurationException(
                                "RabbitMQ API error " + responseError.getStatusCode() + ": " + errorBody);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, responseError) -> {
                        String errorBody = new String(responseError.getBody().readAllBytes());
                        logger.error("RabbitMQ API server error (PUT): status={}, uri={}, response={}",
                                responseError.getStatusCode(), uri, errorBody);
                        throw new RabbitConfigurationException(
                                "RabbitMQ server error " + responseError.getStatusCode() + ": " + errorBody);
                    })
                    .toEntity(String.class);
        } catch (RabbitConfigurationException e) {
            throw e; // Re-throw our exception
        } catch (Exception e) {
            logger.error("Failed to connect to RabbitMQ (PUT): uri={}, error={}", uri, e.getMessage());
            throw new RabbitConfigurationException("Failed to connect to RabbitMQ: " + e.getMessage(), e);
        }
    }

    private ResponseEntity<String> callPost(String body, String uri, Object... uriArgs) {
        try {
            return restClient.post()
                    .uri(uri, uriArgs)
                    .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, responseError) -> {
                        String errorBody = new String(responseError.getBody().readAllBytes());
                        logger.error("RabbitMQ API client error (POST): status={}, uri={}, response={}",
                                responseError.getStatusCode(), uri, errorBody);
                        throw new RabbitConfigurationException("RabbitMQ API error " + responseError.getStatusCode() + ": " + errorBody);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, responseError) -> {
                        String errorBody = new String(responseError.getBody().readAllBytes());
                        logger.error("RabbitMQ API server error (POST): status={}, uri={}, response={}",
                                responseError.getStatusCode(), uri, errorBody);
                        throw new RabbitConfigurationException("RabbitMQ server error " + responseError.getStatusCode() + ": " + errorBody);
                    })
                    .toEntity(String.class);
        } catch (RabbitConfigurationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to connect to RabbitMQ (POST): uri={}, error={}", uri, e.getMessage());
            throw new RabbitConfigurationException("Failed to connect to RabbitMQ: " + e.getMessage(), e);
        }
    }

    private String formatStatus(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status != null) {
            return status.value() + " - " + status.getReasonPhrase();
        }
        return statusCode.value() + " - Unknown";
    }

}
