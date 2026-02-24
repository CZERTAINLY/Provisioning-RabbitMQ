package com.czertainly.rabbitmq.bootstrap.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.core.io.Resource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "app.token")
public record TokenProperties(
        SecretKey signingKey,
        String subject,
        int version,
        Resource configTemplate
) {
    @ConstructorBinding
    public TokenProperties(String signingKey, String subject, int version, Resource configTemplate) {
        this(toSecretKey(signingKey), subject, version, configTemplate);
    }

    private static SecretKey toSecretKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return null;
        byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                "app.token.signing-key must be at least 32 characters (256 bits) for HMAC-SHA256");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}