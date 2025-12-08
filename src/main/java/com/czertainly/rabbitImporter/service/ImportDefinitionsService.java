package com.czertainly.rabbitImporter.service;

import com.czertainly.rabbitImporter.config.RabbitMQProperties;
import com.czertainly.rabbitImporter.model.Definitions;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class ImportDefinitionsService {

    private static final Logger log = LoggerFactory.getLogger(ImportDefinitionsService.class);

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RabbitMQProperties rabbitMQProperties;
    private final RabbitApiService rabbitApiService;

    @Value("${rabbitmq.definitions-file}")
    private String definitionsFile;

    public ImportDefinitionsService(RabbitMQProperties rabbitMQProperties, RabbitApiService rabbitApiService) {
        this.rabbitMQProperties = rabbitMQProperties;
        this.rabbitApiService = rabbitApiService;
    }

    public void importDefinitions() throws RabbitConfigurationException {
        log.info("=== Starting RabbitMQ defintions import from file {} ===", definitionsFile);

        Definitions defs = loadDefinitions();
        try {
            createExchanges(defs);
            createQueues(defs);
            createBindings(defs);
        } catch (Exception e) {
            log.error("Error creating RabbitMQ objects", e);
            throw new RabbitConfigurationException("Error creating RabbitMQ objects", e);
        }

        log.info("=== RabbitMQ import done. ===");
    }

    private Definitions loadDefinitions() throws RabbitConfigurationException {
        log.info("Loading definitions from: {}", definitionsFile);
        try {
            InputStream is = new ClassPathResource(definitionsFile).getInputStream();
            return mapper.readValue(is, Definitions.class);
        } catch (Exception e) {
            log.error("Error loading definitions", e);
            throw new RabbitConfigurationException("Error loading definitions", e);
        }
    }

    private void createExchanges(Definitions defs) {
        log.info("Creating exchanges...");

        for (Definitions.Exchange exchange : defs.exchanges()) {
            log.info("   -> Creating exchange: {}", exchange.name());
            rabbitApiService.createExchangeIfNotExist(exchange.name(), rabbitMQProperties.vhost());
        }
    }

    private void createQueues(Definitions defs) {
        log.info("Creating queues");

        for (Definitions.Queue queue : defs.queues()) {
            log.info("   -> Creating queue: {}", queue.name());
            rabbitApiService.createQueueIfNotExist(queue.name(), rabbitMQProperties.vhost());
        }
    }

    private void createBindings(Definitions defs) {
        log.info("Creating bindings...");

        for (Definitions.Binding binding : defs.bindings()) {
            if (!binding.destination_type().equals("queue")) {
                continue;
            }

            log.info("   -> Binding: exchange={} → queue={} (key={})",
                    binding.source(), binding.destination(), binding.routing_key());

            rabbitApiService.createBindingIfNotExist(binding.source(), binding.routing_key(), binding.destination(), rabbitMQProperties.vhost());
        }
    }
}
