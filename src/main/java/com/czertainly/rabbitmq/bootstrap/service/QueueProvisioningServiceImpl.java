package com.czertainly.rabbitmq.bootstrap.service;

import com.czertainly.rabbitmq.bootstrap.exception.QueueNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class QueueProvisioningServiceImpl implements QueueProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(QueueProvisioningServiceImpl.class);

    private final RabbitAdmin rabbitAdmin;
    private final String queuePrefix;
    private final String exchangeName;

    public QueueProvisioningServiceImpl(
        RabbitAdmin rabbitAdmin,
        @Value("${app.queue.prefix}") String queuePrefix,
        @Value("${app.queue.exchange}") String exchangeName) {
        this.rabbitAdmin = rabbitAdmin;
        this.queuePrefix = queuePrefix;
        this.exchangeName = exchangeName;
    }

    @Override
    public void provisionQueue(String proxyCode) {
        String queueName = queuePrefix + proxyCode;
        var queue = QueueBuilder.durable(queueName).build();
        rabbitAdmin.declareQueue(queue);

        var exchange = new TopicExchange(exchangeName);
        var binding = BindingBuilder.bind(queue).to(exchange).with(queueName);
        rabbitAdmin.declareBinding(binding);

        log.debug("Provisioned queue '{}' with binding to '{}' (routing key: '{}')",
            queueName, exchangeName, queueName);
    }

    @Override
    public void decommissionQueue(String proxyCode) {
        String queueName = queuePrefix + proxyCode;
        Properties properties = rabbitAdmin.getQueueProperties(queueName);
        if (properties == null) {
            throw new QueueNotFoundException(proxyCode);
        }

        rabbitAdmin.deleteQueue(queueName);
        log.debug("Decommissioned queue '{}'", queueName);
    }
}
