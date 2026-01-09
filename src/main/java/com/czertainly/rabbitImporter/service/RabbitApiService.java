package com.czertainly.rabbitImporter.service;

import com.czertainly.rabbitImporter.config.RabbitMQProperties;
import com.czertainly.rabbitImporter.config.User;
import com.czertainly.rabbitImporter.model.Definitions;
import com.czertainly.rabbitImporter.model.OperationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

@Service
public class RabbitApiService {

    private static final Logger logger = getLogger(RabbitApiService.class);

    private final RabbitMQProperties rabbitMQProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RabbitApiService(RabbitMQProperties rabbitMQProperties, RestClient restClient, ObjectMapper objectMapper) {
        this.rabbitMQProperties = rabbitMQProperties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public void createUserIfNotExist(User user, OperationResult stats) {
        if (StringUtils.hasLength(user.username()) && !StringUtils.hasLength(user.password())) {
            throw new RabbitConfigurationException("Username provided without password, do not create user.");
        }

        logger.info("Creating user: {}", user.username());
        Map<String, String> bodyMap = Map.of("password", user.password(), "tags", "impersonator");
        String body;
        try {
            body = objectMapper.writeValueAsString(bodyMap);
        } catch (JsonProcessingException e) {
            throw new RabbitConfigurationException("JSON processing exception. ", e);
        }

        ResponseEntity<String> response = callPut(body, rabbitMQProperties.managementUrl(), "/api/users/{username}", user.username());
        HttpStatusCode statusCode = response.getStatusCode();
        if (statusCode.isSameCodeAs(HttpStatus.CREATED)) {
            stats.addStats(OperationResult.ObjectType.USER, user.username(), formatStatus(statusCode));
        } else if (statusCode.isSameCodeAs(HttpStatus.NO_CONTENT)) {
            stats.addStats(OperationResult.ObjectType.USER, user.username(), formatStatus(statusCode) + " (already exists)");
        } else {
            logger.warn("Unexpected status code {} for user creation: {}", statusCode, user.username());
            throw new RabbitConfigurationException("Failed to create user: HTTP " + statusCode);
        }
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

        ResponseEntity<String> response = callPut(body, rabbitMQProperties.managementUrl(), "/api/vhosts/{vhost}", vhost);
        HttpStatusCode statusCode = response.getStatusCode();
        if (statusCode.isSameCodeAs(HttpStatus.CREATED)) {
            stats.addStats(OperationResult.ObjectType.VHOST, vhost, formatStatus(statusCode));
        } else if (statusCode.isSameCodeAs(HttpStatus.NO_CONTENT)) {
            stats.addStats(OperationResult.ObjectType.VHOST, vhost, formatStatus(statusCode) + " (already exists)");
        } else {
            logger.warn("Unexpected status code {} for vhost creation: {}", statusCode, vhost);
        }
    }

    public void createUserRightsForProxy(String vhost, String proxyName, String username, OperationResult stats) {
        logger.info("Creating user rights for vhost: {}, proxy: {}, username: {}", vhost, proxyName, username);

        String body;
        Map<String, String> bodyMap = Map.of(
                "configure", "^$",
                "write", RabbitProxyManagementService.PROXY_RESPONSE_QUEUE_NAME,
                "read", proxyName
        );
        try {
            body = objectMapper.writeValueAsString(bodyMap);
        } catch (JsonProcessingException e) {
            throw new RabbitConfigurationException("JSON processing exception. ", e);
        }

        ResponseEntity<String> response = callPut(body, rabbitMQProperties.managementUrl(), "/api/permissions/{vhost}/{user}", vhost, username);
        HttpStatusCode statusCode = response.getStatusCode();

        if (statusCode.isSameCodeAs(HttpStatus.CREATED)) {
            stats.addStats(OperationResult.ObjectType.USERRIGHTS, vhost + " - " + username + " - " + body, formatStatus(statusCode));
            logger.info("User rights created: vhost={}, username={}", vhost, username);
        } else {
            logger.warn("Unexpected status code {} for user rights creation: vhost={}, username={}", statusCode.value(), vhost, username);
        }
    }

    public void createUserRightsForCoreUser(String vhost, String username, String readRegExp, String writeRegExp, OperationResult stats) {
        logger.info("Creating user rights for vhost: {}, readRegExp: {}, writeRegExp: {}, username: {}", vhost, readRegExp, writeRegExp, username);

        String body;
        Map<String, String> bodyMap = Map.of(
                "configure", "^$",
                "write", writeRegExp,
                "read", readRegExp
        );
        try {
            body = objectMapper.writeValueAsString(bodyMap);
        } catch (JsonProcessingException e) {
            throw new RabbitConfigurationException("JSON processing exception. ", e);
        }

        ResponseEntity<String> response = callPut(body, rabbitMQProperties.managementUrl(), "/api/permissions/{vhost}/{user}", vhost, username);
        HttpStatusCode statusCode = response.getStatusCode();

        if (statusCode.isSameCodeAs(HttpStatus.CREATED)) {
            stats.addStats(OperationResult.ObjectType.USERRIGHTS, vhost + " - " + username + " - " + body, formatStatus(statusCode));
            logger.info("User rights created: vhost={}, username={}", vhost, username);
        } else {
            logger.warn("Unexpected status code {} for user rights creation: vhost={}, username={}", statusCode.value(), vhost, username);
        }
    }

    public void createBindingIfNotExist(String exchangeName, String routingKey, String queueName, String vhost, OperationResult stats) {
        Definitions.Binding binding = new Definitions.Binding(
                exchangeName, vhost, queueName, "queue", routingKey, new HashMap<>());
        createBindingIfNotExist(binding, stats);
    }

    public void createBindingIfNotExist(Definitions.Binding binding, OperationResult stats) {
        String exchangeName = binding.source();
        String queueName = binding.destination();
        String routingKey = binding.routing_key();
        String vhost = binding.vhost();
        logger.info("Creating binding for exchange: {}, routingKey: {}, queueName: {}, vhost: {}", exchangeName, routingKey, queueName, binding.vhost());

        ResponseEntity<String> response = callPost(binding, rabbitMQProperties.managementUrl(), "/api/bindings/{vhost}/e/{exchange}/q/{queue}", vhost, exchangeName, queueName);
        HttpStatusCode statusCode = response.getStatusCode();

        if (statusCode.isSameCodeAs(HttpStatus.CREATED)) {
            stats.addStats(OperationResult.ObjectType.BINDING, vhost + "/" + exchangeName + " + " + routingKey + " >>> " + queueName, formatStatus(statusCode));
            logger.info("Binding created: exchange={}, routingKey={}, queue={}, vhost={}", exchangeName, routingKey, queueName, vhost);
        } else {
            logger.warn("Unexpected status code {} for binding creation: exchange={}, routingKey={}, queue={}, vhost={}",
                    statusCode.value(), exchangeName, routingKey, queueName, vhost);
        }
    }

    public void createQueueIfNotExist(String queueName, String vhost, OperationResult stats) {
        Definitions.Queue queue = new Definitions.Queue(queueName, vhost, true, false, Map.of("x-queue-type", "classic"));
        createQueueIfNotExist(queue, stats);
    }

    public void createQueueIfNotExist(Definitions.Queue queue, OperationResult stats) {
        logger.info("Creating queue: {}, vhost: {}", queue.name(), queue.vhost());

        ResponseEntity<String> response = callPut(queue, rabbitMQProperties.managementUrl(), "/api/queues/{vhost}/{name}", queue.vhost(), queue.name());
        HttpStatusCode statusCode = response.getStatusCode();

        if (statusCode.isSameCodeAs(HttpStatus.CREATED)) {
            stats.addStats(OperationResult.ObjectType.QUEUE, queue.name(), formatStatus(statusCode));
            logger.info("Queue created: name={}, vhost={}", queue.name(), queue.vhost());
        } else if (statusCode.isSameCodeAs(HttpStatus.NO_CONTENT)) {
            stats.addStats(OperationResult.ObjectType.QUEUE, queue.name(), formatStatus(statusCode) + " - (already exist / created)");
            logger.info("Queue already exists: name={}, vhost={}", queue.name(), queue.vhost());
        } else {
            logger.warn("Unexpected status code {} for queue creation: name={}, vhost={}", statusCode.value(), queue.name(), queue.vhost());
        }
    }

    public void createExchangeIfNotExist(String exchangeName, String vhost, OperationResult stats) {
        Definitions.Exchange exchange = new Definitions.Exchange(
                exchangeName, vhost, "topic", true, false, new HashMap<>()
        );
        createExchangeIfNotExist(exchange, stats);
    }

    public void createExchangeIfNotExist(Definitions.Exchange exchange, OperationResult stats) {
        logger.info("Creating exchange: {}, vhost: {}", exchange.name(), exchange.vhost());

        ResponseEntity<String> response = callPut(exchange, rabbitMQProperties.managementUrl(),"/api/exchanges/{vhost}/{name}", exchange.vhost(), exchange.name());
        HttpStatusCode statusCode = response.getStatusCode();

        if (statusCode.isSameCodeAs(HttpStatus.CREATED)) {
            stats.addStats(OperationResult.ObjectType.EXCHANGE, exchange.name(), formatStatus(statusCode));
            logger.info("Exchange created: name={}, vhost={}", exchange.name(), exchange.vhost());
        } else if (statusCode.isSameCodeAs(HttpStatus.NO_CONTENT)) {
            stats.addStats(OperationResult.ObjectType.EXCHANGE, exchange.name(), formatStatus(statusCode) + " - (already exist / created)");
            logger.info("Exchange already exists: name={}, vhost={}", exchange.name(), exchange.vhost());
        } else {
            logger.warn("Unexpected status code {} for exchange creation: name={}, vhost={}", statusCode.value(), exchange.name(), exchange.vhost());
        }
    }

    private <T> ResponseEntity<String> callPut(T body, String uri, String endpointAddress, Object... uriArgs) {
        return callHttp(restClient.put(), body, uri, endpointAddress, "PUT", uriArgs);
    }

    private <T> ResponseEntity<String> callPost(T body, String uri,  String endpointAddress, Object... uriArgs) {
        return callHttp(restClient.post(), body, uri, endpointAddress, "POST", uriArgs);
    }

    private <T> ResponseEntity<String> callHttp(
            RestClient.RequestBodyUriSpec requestSpec,
            T body,
            String uri,
            String endpointAddress,
            String methodName,
            Object... uriArgs) {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(uri).path(endpointAddress);
            String builtUri = uriBuilder.buildAndExpand(uriArgs).toUriString();
            return requestSpec
                    .uri(builtUri)
                    .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, responseError) -> {
                        String errorBody = new String(responseError.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        logger.error("RabbitMQ API client error ({}): status={}, uri={}, response={}",
                                methodName, responseError.getStatusCode(), uri, errorBody);
                        throw new RabbitConfigurationException(
                                "RabbitMQ API error " + responseError.getStatusCode() + ": " + errorBody);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, responseError) -> {
                        String errorBody = new String(responseError.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        logger.error("RabbitMQ API server error ({}): status={}, uri={}, response={}",
                                methodName, responseError.getStatusCode(), uri, errorBody);
                        throw new RabbitConfigurationException(
                                "RabbitMQ server error " + responseError.getStatusCode() + ": " + errorBody);
                    })
                    .toEntity(String.class);
        } catch (RabbitConfigurationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to connect to RabbitMQ ({}): uri={}, error={}", methodName, uri, e.getMessage());
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
