package com.czertainly.rabbitImporter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rabbitmq")
public record RabbitMQProperties(
        String managementUrl,
        String username,
        String password,
        String vhost
) {
}
