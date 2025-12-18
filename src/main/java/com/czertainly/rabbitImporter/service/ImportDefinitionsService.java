package com.czertainly.rabbitImporter.service;

import com.czertainly.rabbitImporter.config.RabbitMQProperties;
import com.czertainly.rabbitImporter.model.Definitions;
import com.czertainly.rabbitImporter.model.OperationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class ImportDefinitionsService {

    private static final Logger logger = LoggerFactory.getLogger(ImportDefinitionsService.class);

    private final ObjectMapper mapper;

    private final RabbitMQProperties rabbitMQProperties;
    private final RabbitApiService rabbitApiService;

    @Value("${rabbitmq.definitions-file}")
    private String definitionsFile;

    public ImportDefinitionsService(RabbitMQProperties rabbitMQProperties, RabbitApiService rabbitApiService, ObjectMapper mapper) {
        this.rabbitMQProperties = rabbitMQProperties;
        this.rabbitApiService = rabbitApiService;
        this.mapper = mapper;
    }

    public OperationResult importDefinitions(String jsonDefinitions) throws RabbitConfigurationException, JsonProcessingException {
        logger.info("=== Starting RabbitMQ definitions import from file {} ===", definitionsFile);
        OperationResult operationResult = new OperationResult();
        Definitions defs;
        if (jsonDefinitions != null) {
            defs = mapper.readValue(jsonDefinitions, Definitions.class);
        } else {
            defs = loadDefinitions();
        }

        createExchanges(defs, operationResult);
        createQueues(defs, operationResult);
        createBindings(defs, operationResult);

        logger.info("=== RabbitMQ import done. ===");
        return operationResult;
    }

    private Definitions loadDefinitions() throws RabbitConfigurationException {
        logger.info("Loading definitions from: {}", definitionsFile);
        try {
            InputStream is = new ClassPathResource(definitionsFile).getInputStream();
            return mapper.readValue(is, Definitions.class);
        } catch (Exception e) {
            logger.error("Error loading definitions", e);
            throw new RabbitConfigurationException("Error loading definitions", e);
        }
    }

    private void createExchanges(Definitions defs, OperationResult stats) {
        logger.info("Creating exchanges...");

        for (Definitions.Exchange exchange : defs.exchanges()) {
            logger.info("   -> Creating exchange: {}", exchange.name());
            rabbitApiService.createExchangeIfNotExist(exchange.name(), rabbitMQProperties.vhost(), stats);
        }
    }

    private void createQueues(Definitions defs, OperationResult stats) {
        logger.info("Creating queues");

        for (Definitions.Queue queue : defs.queues()) {
            logger.info("   -> Creating queue: {}", queue.name());
            rabbitApiService.createQueueIfNotExist(queue.name(), rabbitMQProperties.vhost(), stats);
        }
    }

    private void createBindings(Definitions defs, OperationResult stats) {
        logger.info("Creating bindings...");

        for (Definitions.Binding binding : defs.bindings()) {
            if (!binding.destination_type().equals("queue")) {
                continue;
            }

            logger.info("   -> Binding: exchange={} → queue={} (key={})",
                    binding.source(), binding.destination(), binding.routing_key());

            rabbitApiService.createBindingIfNotExist(binding.source(), binding.routing_key(), binding.destination(), rabbitMQProperties.vhost(), stats);
        }
    }
}
