package com.czertainly.rabbitmq.bootstrap.controller;

import com.czertainly.rabbitmq.bootstrap.api.QueueProvisioningApi;
import com.czertainly.rabbitmq.bootstrap.model.QueueRequest;
import com.czertainly.rabbitmq.bootstrap.service.QueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QueueController implements QueueProvisioningApi {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @Override
    public ResponseEntity<Void> provisionQueue(QueueRequest queueRequest) {
        queueService.provisionQueue(queueRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<Void> deleteQueue(String name) {
        queueService.deleteQueue(name);
        return ResponseEntity.noContent().build();
    }
}
