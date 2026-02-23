package com.czertainly.rabbitmq.bootstrap.service;

public interface QueueProvisioningService {

    void provisionQueue(String proxyCode);

    void decommissionQueue(String proxyCode);
}
