package com.czertainly.rabbitmq.bootstrap.service;

import com.czertainly.rabbitmq.bootstrap.config.ProxyConfigProperties;
import com.czertainly.rabbitmq.bootstrap.exception.QueueNotFoundException;
import com.czertainly.rabbitmq.bootstrap.model.Command;
import com.czertainly.rabbitmq.bootstrap.model.InstallationInstructions;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Service
public class QueueProvisioningServiceImpl implements QueueProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(QueueProvisioningServiceImpl.class);
    private static final String HELM_TEMPLATE_CLASSPATH = "templates/helm.install.template";

    private final RabbitAdmin rabbitAdmin;
    private final ProxyConfigTokenGenerator tokenGenerator;
    private final Mustache.Compiler mustacheCompiler;
    private final ProxyConfigProperties proxyConfig;

    private Template helmInstallTemplate;

    public QueueProvisioningServiceImpl(
            RabbitAdmin rabbitAdmin,
            ProxyConfigTokenGenerator tokenGenerator,
            Mustache.Compiler mustacheCompiler,
            ProxyConfigProperties proxyConfig) {
        this.rabbitAdmin = rabbitAdmin;
        this.tokenGenerator = tokenGenerator;
        this.mustacheCompiler = mustacheCompiler;
        this.proxyConfig = proxyConfig;
    }

    @PostConstruct
    void init() throws IOException {
        var resource = new ClassPathResource(HELM_TEMPLATE_CLASSPATH);
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            this.helmInstallTemplate = mustacheCompiler.compile(reader);
        }
        log.debug("Loaded helm install template from {}", HELM_TEMPLATE_CLASSPATH);
    }

    @Override
    public void provisionQueue(String proxyCode) {
        var queue = QueueBuilder.durable(proxyCode).build();
        rabbitAdmin.declareQueue(queue);

        var exchange = new TopicExchange(proxyConfig.exchange());
        var binding = BindingBuilder.bind(queue).to(exchange).with(proxyCode);
        rabbitAdmin.declareBinding(binding);

        log.debug("Provisioned queue '{}' with binding to '{}' (routing key: '{}')",
                proxyCode, proxyConfig.exchange(), proxyCode);
    }

    @Override
    public void decommissionQueue(String proxyCode) {
        Properties properties = rabbitAdmin.getQueueProperties(proxyCode);
        if (properties == null) {
            throw new QueueNotFoundException(proxyCode);
        }

        rabbitAdmin.deleteQueue(proxyCode);
        log.debug("Decommissioned queue '{}'", proxyCode);
    }

    @Override
    public InstallationInstructions getInstallationInstructions(String proxyCode, String format) {
        if (!"helm".equals(format)) {
            throw new IllegalArgumentException("Format '%s' is not supported".formatted(format));
        }

        if (rabbitAdmin.getQueueProperties(proxyCode) == null) {
            throw new QueueNotFoundException(proxyCode);
        }

        // TODO: replace with per-proxy credentials
        var configData = new ProxyConfigData(
                proxyConfig.amqpUrl(), proxyConfig.username(), proxyConfig.password(),
                proxyCode, proxyConfig.exchange(), proxyCode);

        String token = tokenGenerator.generateToken(configData);
        String command = helmInstallTemplate.execute(new HelmInstallData(token));
        return new InstallationInstructions().command(new Command().shell(command));
    }
}