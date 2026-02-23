package com.czertainly.rabbitmq.bootstrap.controller;

import com.czertainly.rabbitmq.bootstrap.api.ProxyProvisioningApi;
import com.czertainly.rabbitmq.bootstrap.model.Command;
import com.czertainly.rabbitmq.bootstrap.model.InstallationInstructions;
import com.czertainly.rabbitmq.bootstrap.model.ProxyProvisioningRequest;
import com.czertainly.rabbitmq.bootstrap.service.QueueProvisioningService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProxyProvisioningController implements ProxyProvisioningApi {

    private final QueueProvisioningService provisioningService;

    public ProxyProvisioningController(QueueProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    public ResponseEntity<Void> provisionProxy(ProxyProvisioningRequest request) {
        provisioningService.provisionQueue(request.getProxyCode());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<Void> decommissionProxy(String proxyCode) {
        provisioningService.decommissionQueue(proxyCode);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<InstallationInstructions> getInstallationInstructions(String proxyCode, String format) {
        String mockCommand = switch (format) {
            case "helm" -> "helm install proxy-%s oci://registry.example.com/charts/proxy --version 1.0.0 --set config.proxyCode=%s".formatted(proxyCode, proxyCode);
            case "curl" -> "curl -X POST https://api.example.com/install -H 'Authorization: Bearer TOKEN' -d '{\"proxyCode\":\"%s\"}'".formatted(proxyCode);
            case "terraform" -> """
                    resource "proxy_instance" "%s" { proxy_code = "%s" }""".formatted(proxyCode, proxyCode);
            case "docker-compose" -> "docker compose -f proxy-%s-compose.yaml up -d".formatted(proxyCode);
            default -> "echo 'Unknown format: %s'".formatted(format);
        };

        var instructions = new InstallationInstructions()
                .command(new Command().shell(mockCommand));
        return ResponseEntity.ok(instructions);
    }
}
