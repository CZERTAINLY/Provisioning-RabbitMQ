package com.czertainly.rabbitImporter.api;

import com.czertainly.rabbitImporter.service.RabbitConfigurationException;
import com.czertainly.rabbitImporter.service.ImportDefinitionsService;
import com.czertainly.rabbitImporter.service.RabbitProxyManagementService;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/import-definitions")
    public ResponseEntity<String> importDefinitions() {
        try {
            importDefinitionsService.importDefinitions();
            return ResponseEntity.ok("RabbitMQ import done.");
        } catch (RabbitConfigurationException e) {
            logger.error("Import failed", e);
            return ResponseEntity.internalServerError().body("Import failed. Check server logs for details.");
        }
    }

    @GetMapping("/add-proxy")
    public ResponseEntity<String> addProxyToRabbit(@RequestParam String proxyName, @RequestParam(required = false) String vhost) {
        if (vhost == null || vhost.isBlank()) {
            vhost = "/";
        }
        if (proxyName == null || proxyName.isBlank()) {
            return ResponseEntity.badRequest().body("proxyName is required");
        }
        if (!proxyName.matches("^[a-zA-Z0-9_-]+$")) {
            logger.warn("Invalid proxyName format: {}", proxyName);
            return ResponseEntity.badRequest().body("proxyName can only contain letters, numbers, underscores and hyphens");
        }
        if (proxyName.length() > 255) {
            return ResponseEntity.badRequest().body("proxyName is too long (max 255 characters)");
        }
        try {
            rabbitProxyManagementService.addProxy(proxyName, vhost);
            return ResponseEntity.ok("Proxy added.");
        } catch (RabbitConfigurationException e) {
            logger.error("Failed to add proxy: {}", proxyName, e);
            return ResponseEntity.internalServerError().body("Failed to add proxy. Check server logs.");
        }
    }
}