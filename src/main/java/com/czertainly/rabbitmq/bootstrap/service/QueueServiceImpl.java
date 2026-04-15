package com.czertainly.rabbitmq.bootstrap.service;

import com.czertainly.rabbitmq.bootstrap.exception.QueueAlreadyExistsException;
import com.czertainly.rabbitmq.bootstrap.model.QueueRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

@Service
public class QueueServiceImpl implements QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueServiceImpl.class);

    private final RabbitAdmin rabbitAdmin;

    public QueueServiceImpl(RabbitAdmin rabbitAdmin) {
        this.rabbitAdmin = rabbitAdmin;
    }

    @Override
    public void provisionQueue(QueueRequest request) {
        var queueBuilder = QueueBuilder.durable(request.getName());
        if (request.getProperties() != null && !request.getProperties().isEmpty()) {
            queueBuilder.withArguments(request.getProperties());
        }
        var queue = queueBuilder.build();

        try {
            rabbitAdmin.declareQueue(queue);
        } catch (AmqpException e) {
            if (containsPreconditionFailed(e)) {
                throw new QueueAlreadyExistsException(request.getName());
            }
            throw e;
        }

        var exchange = new TopicExchange(request.getExchange());
        var binding = BindingBuilder.bind(queue).to(exchange).with(request.getRoutingKey());
        rabbitAdmin.declareBinding(binding);

        log.info("Provisioned queue '{}' with binding to exchange '{}' (routing key: '{}')",
                request.getName(), request.getExchange(), request.getRoutingKey());
    }

    @Override
    public void deleteQueue(String name) {
        rabbitAdmin.deleteQueue(name);
        log.info("Deleted queue '{}'", name);
    }

    // Walk the full cause chain: Spring AMQP can wrap the RabbitMQ channel exception
    // (e.g. ShutdownSignalException) through multiple levels before reaching AmqpIOException.
    private static boolean containsPreconditionFailed(AmqpException e) {
        Throwable t = e;
        while (t != null) {
            if (messageContains(t.getMessage(), "PRECONDITION_FAILED")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static boolean messageContains(String message, String substring) {
        return message != null && message.contains(substring);
    }
}
