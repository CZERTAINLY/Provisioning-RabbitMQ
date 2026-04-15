package com.czertainly.rabbitmq.bootstrap.service;

import com.czertainly.rabbitmq.bootstrap.model.QueueRequest;

public interface QueueService {

    void provisionQueue(QueueRequest request);

    void deleteQueue(String name);
}
