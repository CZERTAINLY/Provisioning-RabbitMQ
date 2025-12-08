package com.czertainly.rabbitImporter.service;

import com.czertainly.rabbitImporter.config.RabbitMQProperties;
import org.slf4j.Logger;
import org.springframework.http.HttpMethod;
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

    public void createBindingIfNotExist(String exchangeName, String routingKey, String queueName, String vhost) {
        logger.info("Creating binding for exchange: {}, routingKey: {}, queueName: {}, vhost: {}", exchangeName, routingKey, queueName, vhost);
        String body = """
                {
                  "routing_key": "%s"
                }
                """.formatted(routingKey);

        ResponseEntity<String> response = callPost(body, rabbitMQProperties.managementUrl() + "/api/bindings/{vhost}/e/{exchange}/q/{queue}", vhost, exchangeName, queueName);
        logger.info("Binding created successfully/or already exists.. Response HTTP code: {}", response.getStatusCode());
    }

    public void createQueueIfNotExist(String queueName, String vhost) {
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
        logger.info("Queue created successfully/or already exists. Response HTTP code: {}", response.getStatusCode());
    }

    public void createExchangeIfNotExist(String exchangeName, String vhost) {
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
        logger.info("Exchange created successfully/or already exists.. Response HTTP code: {}", response.getStatusCode());
    }

    private ResponseEntity<String> callPut(String body, String uri, Object... uriArgs) {
        return restClient.put()
                .uri(uri, uriArgs)
                .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> callPost(String body, String uri, Object... uriArgs) {
        return restClient.post()
                .uri(uri, uriArgs)
                .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }


}
