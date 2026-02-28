package com.czertainly.rabbitmq.bootstrap.service;

import com.czertainly.rabbitmq.bootstrap.config.ProxyConfigProperties;
import com.czertainly.rabbitmq.bootstrap.exception.QueueNotFoundException;
import com.czertainly.rabbitmq.bootstrap.model.Command;
import com.czertainly.rabbitmq.bootstrap.model.InstallationInstructions;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Service
public class QueueProvisioningServiceImpl implements QueueProvisioningService {
    private static final Logger log = LoggerFactory.getLogger(QueueProvisioningServiceImpl.class);

    private final RabbitAdmin rabbitAdmin;
    private final ProxyConfigTokenGenerator tokenGenerator;
    private final ProxyConfigProperties proxyConfig;
    private final Template helmInstallTemplate;

    public QueueProvisioningServiceImpl(
        RabbitAdmin rabbitAdmin,
        ProxyConfigTokenGenerator tokenGenerator,
        Mustache.Compiler mustacheCompiler,
        ProxyConfigProperties proxyConfig) throws IOException {
        this.rabbitAdmin = rabbitAdmin;
        this.tokenGenerator = tokenGenerator;
        this.proxyConfig = proxyConfig;

        try (var reader = new InputStreamReader(proxyConfig.helmInstallTemplate().getInputStream(), StandardCharsets.UTF_8)) {
            this.helmInstallTemplate = mustacheCompiler.compile(reader);
        }
    }

    @Override
    public void provisionQueue(String proxyCode) {
        // create a durable queue with the name of the proxy code
        var queue = QueueBuilder.durable(proxyCode).build();
        rabbitAdmin.declareQueue(queue);

        // create bindings for the request from core to proxy
        var exchange = new TopicExchange(proxyConfig.exchange());
        // TODO: verify that the exchange exists

        var requestRoutingKey = proxyConfig.requestRoutingKeyPrefix() + proxyCode;
        var requestBinding = BindingBuilder.bind(queue).to(exchange).with(requestRoutingKey);
        rabbitAdmin.declareBinding(requestBinding);

        log.info("Provisioned queue '{}' with binding to '{}' (routing key: '{}')",
            proxyCode, proxyConfig.exchange(), requestRoutingKey);

        // create bindings for the response from proxy to core
        var responseQueue = new Queue(proxyConfig.responseQueue());
        // TODO: verify that the response queue exists

        var responseRoutingKey = proxyConfig.responseRoutingKeyPrefix() + proxyCode;
        var responseBinding = BindingBuilder.bind(responseQueue).to(exchange).with(responseRoutingKey);
        rabbitAdmin.declareBinding(responseBinding);

        log.info("Provisioned response binding for queue '{}' to exchange '{}' with routing key '{}'",
            proxyConfig.responseQueue(), proxyConfig.exchange(), responseRoutingKey);
    }

    @Override
    public void decommissionQueue(String proxyCode) {
        rabbitAdmin.deleteQueue(proxyCode);
        log.info("Decommissioned queue '{}'", proxyCode);
    }

    @Override
    public InstallationInstructions getInstallationInstructions(String proxyCode, String format) {
        if (!"helm".equals(format)) {
            throw new IllegalArgumentException("Format '%s' is not supported".formatted(format));
        }

        if (rabbitAdmin.getQueueInfo(proxyCode) == null) {
            throw new QueueNotFoundException(proxyCode);
        }

        var configData = new ProxyConfigData(
            proxyConfig.amqpUrl(),
            proxyConfig.username(),
            proxyConfig.password(),
            proxyCode,
            proxyConfig.exchange());

        String token = tokenGenerator.generateToken(configData);
        String command = helmInstallTemplate.execute(new HelmInstallData(token));
        return new InstallationInstructions().command(new Command().shell(command));
    }
}
