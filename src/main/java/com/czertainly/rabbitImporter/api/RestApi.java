package com.czertainly.rabbitImporter.api;

import com.czertainly.rabbitImporter.model.ErrorResponse;
import com.czertainly.rabbitImporter.model.OperationResult;
import com.czertainly.rabbitImporter.service.RabbitConfigurationException;
import com.czertainly.rabbitImporter.service.ImportDefinitionsService;
import com.czertainly.rabbitImporter.service.RabbitProxyManagementService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import static org.slf4j.LoggerFactory.getLogger;

@RestController
@RequestMapping("/api")
public class RestApi {

    private static final Logger logger = getLogger(RestApi.class);

    private final ImportDefinitionsService importDefinitionsService;
    private final RabbitProxyManagementService rabbitProxyManagementService;

    public RestApi(ImportDefinitionsService importDefinitionsService, RabbitProxyManagementService rabbitProxyManagementService) {
        this.importDefinitionsService = importDefinitionsService;
        this.rabbitProxyManagementService = rabbitProxyManagementService;
    }

    @PutMapping("/import-definitions")
    public ResponseEntity<?> importDefinitions(@RequestParam(required = false) String username,
                                               @RequestBody(required = false) String definitionsJson) {
        logger.info("Received import-definitions request, hasBody={}", definitionsJson != null);

        try {
            OperationResult operationResult = importDefinitionsService.importDefinitions(definitionsJson, username);
            logger.info("Successfully imported definitions, returning stats");
            return ResponseEntity.ok(operationResult);
        } catch (JsonProcessingException e) {
            logger.error("Invalid JSON provided: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse("Invalid JSON: " + e.getMessage()));
        } catch (RabbitConfigurationException e) {
            logger.error("Import failed: {}", e.getMessage());
            logger.error("Stacktrace:", e);
            return ResponseEntity.internalServerError().body(new ErrorResponse("Import failed: " + e.getMessage()));
        }
    }


    @PutMapping("/add-proxy")
    public ResponseEntity<?> addProxyToRabbit(@RequestParam String proxyName,
                                              @RequestParam(required = false) String vhost,
                                              @RequestParam(required = false) String username) {
        logger.info("Received add-proxy request: proxyName={}, vhost={}", proxyName, vhost != null ? vhost : "not provided");

        if (vhost == null || vhost.isBlank()) {
            vhost = "/";
        }

        if (!vhost.equals("/") && !StringUtils.hasText(username)) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Username must be provided when vhost is not '/'."));
        }

        if (proxyName == null || proxyName.isBlank()) {
            logger.warn("Validation failed: proxyName is required");
            return ResponseEntity.badRequest().body(new ErrorResponse("proxyName is required"));
        }
        if (!proxyName.matches("^[a-zA-Z0-9_-]{1,255}$")) {
            logger.warn("Validation failed: Invalid proxyName format");
            return ResponseEntity.badRequest().body(new ErrorResponse("proxyName must be 1-255 characters and contain only letters, numbers, underscores and hyphens"));
        }

        try {
            OperationResult result = rabbitProxyManagementService.addProxy(proxyName, vhost, username);
            logger.info("Successfully added proxy '{}' in vhost '{}', returning stats", proxyName, vhost);
            return ResponseEntity.ok(result);

        } catch (RabbitConfigurationException e) {
            logger.error("Failed to add proxy '{}' in vhost '{}': {}", proxyName, vhost, e.getMessage());
            logger.error("Stacktrace:", e);
            return ResponseEntity.internalServerError().body(new ErrorResponse("Failed to add proxy '" + proxyName + "': " + e.getMessage()));
        }
    }
}