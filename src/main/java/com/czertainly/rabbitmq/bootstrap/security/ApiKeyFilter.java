package com.czertainly.rabbitmq.bootstrap.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiKeyFilter implements Filter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final String expectedApiKey;

    public ApiKeyFilter(@Value("${app.security.api-key}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var httpRequest = (HttpServletRequest) request;
        var httpResponse = (HttpServletResponse) response;

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
