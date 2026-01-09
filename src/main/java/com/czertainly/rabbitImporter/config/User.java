package com.czertainly.rabbitImporter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rabbitmq.core.user")
public record User(
        String username,
        String password,
        boolean create
) {
}
