package com.czertainly.rabbitmq.bootstrap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityConfigProperties(
        String apiKey,
        List<String> publicPaths
) {}