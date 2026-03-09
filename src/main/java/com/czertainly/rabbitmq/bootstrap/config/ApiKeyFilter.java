package com.czertainly.rabbitmq.bootstrap.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Component
@ConditionalOnProperty(name = "app.security.api-key")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyFilter implements Filter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final String expectedApiKey;
    private final List<String> publicPaths;

    public ApiKeyFilter(SecurityConfigProperties properties) {
        this.expectedApiKey = Objects.requireNonNullElse(properties.apiKey(), "");
        this.publicPaths = Objects.requireNonNullElse(properties.publicPaths(), List.of());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (expectedApiKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        var httpRequest = (HttpServletRequest) request;
        var httpResponse = (HttpServletResponse) response;

        String requestUri = httpRequest.getRequestURI();
        if (publicPaths.stream().anyMatch(requestUri::startsWith)) {
            chain.doFilter(request, response);
            return;
        }

        String apiKey = httpRequest.getHeader(API_KEY_HEADER);
        if (!expectedApiKey.equals(apiKey)) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Invalid or missing X-API-Key header\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
