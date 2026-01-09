package com.czertainly.rabbitImporter.service;

import com.czertainly.rabbitImporter.config.User;
import com.czertainly.rabbitImporter.model.Definitions;
import com.czertainly.rabbitImporter.model.OperationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ImportDefinitionsService {

    private static final Logger logger = LoggerFactory.getLogger(ImportDefinitionsService.class);

    private final ObjectMapper mapper;

    private final RabbitApiService rabbitApiService;
    private final User coreUser;

    @Value("${rabbitmq.definitions-file}")
    private String definitionsFile;

    public ImportDefinitionsService(RabbitApiService rabbitApiService, ObjectMapper mapper, User coreUser) {
        this.rabbitApiService = rabbitApiService;
        this.mapper = mapper;
        this.coreUser = coreUser;
    }

    /**
     * Configs RabbitMQ for czertailnly-core scheduler and internal communication
     * @param jsonDefinitions rabbitMQ definitions in JSON format
     * @return stats of the operation
     * @throws RabbitConfigurationException
     * @throws JsonProcessingException
     */
    public OperationResult importDefinitions(String jsonDefinitions) throws RabbitConfigurationException, JsonProcessingException {
        logger.info("=== Starting RabbitMQ definitions import from file {} ===", definitionsFile);
        OperationResult operationResult = new OperationResult();

        if (coreUser.create() && StringUtils.hasText(coreUser.password())) {
            rabbitApiService.createUserIfNotExist(coreUser, operationResult);
        }

        Definitions defs;
        if (jsonDefinitions != null) {
            defs = mapper.readValue(jsonDefinitions, Definitions.class);
        } else {
            defs = loadDefinitions();
        }

        createVhost(defs, coreUser.username(), operationResult);
        createExchanges(defs, operationResult);
        createQueues(defs, operationResult);
        createUserRights(defs, coreUser, operationResult);
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

    private void createVhost(Definitions defs, String username, OperationResult stats) {
        Set<String> vhosts = new HashSet<>();
        for (Definitions.Exchange exchange : defs.exchanges()) {
            vhosts.add(exchange.vhost());
        }
        for (Definitions.Queue queue : defs.queues()) {
            vhosts.add(queue.vhost());
        }
        for (Definitions.Binding binding : defs.bindings()) {
            vhosts.add(binding.vhost());
        }

        if (vhosts.stream().anyMatch(v -> !"/".equals(v))  && !StringUtils.hasText(username)) {
            throw new RabbitConfigurationException("Username must be provided when creating non-default vhosts.");
        }

        logger.info("Creating found vhosts: {}", vhosts);
        for (String vhost : vhosts) {
            rabbitApiService.createVhostIfNotExist(vhost, stats);
        }
    }

    private void createExchanges(Definitions defs, OperationResult stats) {
        logger.info("Creating exchanges...");

        for (Definitions.Exchange exchange : defs.exchanges()) {
            logger.info("   -> Creating exchange: {}", exchange.name());
            rabbitApiService.createExchangeIfNotExist(exchange, stats);
        }
    }

    private void createQueues(Definitions defs, OperationResult stats) {
        logger.info("Creating queues");

        for (Definitions.Queue queue : defs.queues()) {
            logger.info("   -> Creating queue: {}", queue.name());
            rabbitApiService.createQueueIfNotExist(queue, stats);
        }
    }

    private void createUserRights(Definitions defs, User user, OperationResult stats) {
        logger.info("Creating user rights for core user: {}", user.username());
        Set<String> exchanges = defs.exchanges().stream().map(Definitions.Exchange::name).collect(Collectors.toSet());
        Set<String> escapedExchanges = exchanges.stream().map(this::escapeErlangRegex).collect(Collectors.toSet());
        String writeRegExp = "^(" + String.join("|", escapedExchanges) + ")$";

        Set<String> queues = defs.queues().stream().map(Definitions.Queue::name).collect(Collectors.toSet());
        Set<String> escapedQueues = queues.stream().map(this::escapeErlangRegex).collect(Collectors.toSet());
        String readRegExp = "^(" + String.join("|", escapedQueues) + ")$";

        // expect queues and exchanges to be in the same vhost
        String vhost = null;
        if (!defs.queues().isEmpty()) {
            vhost = defs.queues().getFirst().vhost();
        }

        if (vhost == null && !defs.exchanges().isEmpty()) {
            vhost = defs.exchanges().getFirst().vhost();
        }

        rabbitApiService.createUserRightsForCoreUser(vhost, user.username(), readRegExp, writeRegExp, stats);
    }

    private void createBindings(Definitions defs, OperationResult stats) {
        logger.info("Creating bindings...");

        for (Definitions.Binding binding : defs.bindings()) {
            if (!binding.destination_type().equals("queue")) {
                continue;
            }
            logger.info("   -> Binding: exchange={} → queue={} (key={})", binding.source(), binding.destination(), binding.routing_key());
            rabbitApiService.createBindingIfNotExist(binding, stats);
        }
    }

    /**
     * Escapes special characters for Erlang regex (used by RabbitMQ permissions).
     * RabbitMQ uses Erlang regex syntax, not Java regex.
     * Special characters: . * + ? [ ] { } ( ) | ^ $ \
     *
     * @param input string to escape
     * @return escaped string safe for use in Erlang regex
     */
    private String escapeErlangRegex(String input) {
        if (input == null) {
            return null;
        }

        // Escape each Erlang regex special character with backslash
        return input
                .replace("\\", "\\\\")  // Must be first! Escape backslash itself
                .replace(".", "\\.")
                .replace("*", "\\*")
                .replace("+", "\\+")
                .replace("?", "\\?")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("|", "\\|")
                .replace("^", "\\^")
                .replace("$", "\\$");
    }
}
