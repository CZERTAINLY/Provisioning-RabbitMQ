package com.czertainly.rabbitmq.bootstrap.service;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Generates JWT configuration tokens for proxy instances.
 * <p>
 * Renders {@code templates/rabbitmq.proxy_config.template} via Mustache with the supplied
 * {@link ProxyConfigData}, parses the resulting YAML into the {@code config} JWT claim,
 * and signs the token with HMAC-SHA256.
 * <p>
 * Configure via {@code app.token.signing-key} (min. 32 characters / 256 bits).
 */
@Service
public class ProxyConfigTokenGenerator {

    private static final int TOKEN_VERSION = 1;
    private static final String TOKEN_SUBJECT = "proxy-config";
    private static final String TEMPLATE_CLASSPATH = "classpath:templates/rabbitmq.proxy_config.template";

    private final SecretKey signingKey;
    private final Template proxyConfigTemplate;

    public ProxyConfigTokenGenerator(
        @Value("${app.token.signing-key}") String rawKey,
        Mustache.Compiler mustacheCompiler,
        @Value(TEMPLATE_CLASSPATH) Resource proxyConfigResource) throws IOException {

        // Validate raw key length (min. 32 bytes for HMAC-SHA256) and set signingKey if provided
        if (!rawKey.isBlank()) {
            byte[] keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                throw new IllegalArgumentException(
                    "app.token.signing-key must be at least 32 characters (256 bits) for HMAC-SHA256");
            }
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        } else {
            this.signingKey = null;
        }

        try (var reader = new InputStreamReader(proxyConfigResource.getInputStream(), StandardCharsets.UTF_8)) {
            this.proxyConfigTemplate = mustacheCompiler.compile(reader);
        }
    }

    /**
     * Generates a non-expiring JWT config token.
     */
    public String generateToken(ProxyConfigData data) {
        return generateToken(data, Duration.ZERO);
    }

    /**
     * Generates a JWT config token. Pass {@link Duration#ZERO} or {@code null}
     * for a token without expiration.
     */
    public String generateToken(ProxyConfigData data, Duration expiresIn) {
        String renderedYaml = proxyConfigTemplate.execute(data);

        Map<String, Object> configMap = new Yaml().load(renderedYaml);

        JwtBuilder builder = Jwts.builder()
            .subject(TOKEN_SUBJECT)
            .claim("v", TOKEN_VERSION)
            .claim("config", configMap);

        if (signingKey != null) {
            builder.signWith(signingKey);
        }

        if (expiresIn != null && !expiresIn.isZero()) {
            builder.expiration(Date.from(Instant.now().plus(expiresIn)));
        }

        return builder.compact();
    }
}
