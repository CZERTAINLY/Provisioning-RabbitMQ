package com.czertainly.rabbitImporter.service;

import com.czertainly.rabbitImporter.model.Definitions;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.util.HashMap;

@Service
public class RabbitImportService {

    private static final Logger log = LoggerFactory.getLogger(RabbitImportService.class);

    private final RestClient rest;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${rabbitmq.management-url}")
    private String baseUrl;

    @Value("${rabbitmq.username}")
    private String username;

    @Value("${rabbitmq.password}")
    private String password;

    @Value("${rabbitmq.vhost}")
    private String vhost;

    @Value("${rabbitmq.definitions-file}")
    private String definitionsFile;

    public RabbitImportService(RestClient rest) {
        this.rest = rest;
    }

    public void importDefinitions() throws Exception {
        log.info("Starting RabbitMQ import...");

        Definitions defs = loadDefinitions();
        createExchanges(defs);
        createQueues(defs);
        createBindings(defs);

        log.info("RabbitMQ import done.");
    }

    private Definitions loadDefinitions() throws Exception {
        log.info("Loading definitions from: {}", definitionsFile);
        InputStream is = new ClassPathResource(definitionsFile).getInputStream();
        return mapper.readValue(is, Definitions.class);
    }

    private void createExchanges(Definitions defs) {
        log.info("Creating exchanges...");

        for (Definitions.Exchange exchange : defs.exchanges()) {
            log.info("   -> Creating exchange: {}", exchange.name());

            rest.put()
                    .uri(baseUrl + "/exchanges/{vhost}/{name}", vhost, exchange.name())
                    .body(exchange)
                    .headers(h -> h.setBasicAuth(username, password))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    private void createQueues(Definitions defs) {
        log.info("Creating queues");

        for (Definitions.Queue queue : defs.queues()) {
            log.info("   -> Creating queue: {}", queue.name());

            rest.put()
                    .uri(baseUrl + "/queues/{vhost}/{name}", vhost, queue.name())
                    .body(queue)
                    .headers(h -> h.setBasicAuth(username, password))
                    .retrieve()
                    .toBodilessEntity();
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

            var body = new HashMap<>();
            body.put("routing_key", binding.routing_key());
            body.put("arguments", binding.arguments());

            rest.post()
                    .uri(baseUrl + "/bindings/{vhost}/e/{exchange}/q/{queue}",
                            vhost, binding.source(), binding.destination())
                    .body(body)
                    .headers(h -> h.setBasicAuth(username, password))
                    .retrieve()
                    .toBodilessEntity();
        }
    }
}
