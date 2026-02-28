package com.czertainly.rabbitmq.bootstrap.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    static final String MDC_KEY = "correlationId";
    private static final int MAX_BODY_LENGTH = 1000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);

        var wrappedRequest = new ContentCachingRequestWrapper(request);
        var wrappedResponse = new ContentCachingResponseWrapper(response);

        long startNano = System.nanoTime();
        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;

            logRequest(wrappedRequest);
            logResponse(wrappedResponse, elapsedMs);

            wrappedResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            wrappedResponse.copyBodyToResponse();

            MDC.clear();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request) {
        String query = request.getQueryString();
        String uri = request.getRequestURI() + (query != null ? "?" + query : "");
        String body = extractBody(request.getContentAsByteArray(), request.getContentType());
        log.info(">> {} {} body={}", request.getMethod(), uri, body);
    }

    private void logResponse(ContentCachingResponseWrapper response, long elapsedMs) {
        String body = extractBody(response.getContentAsByteArray(), response.getContentType());
        log.info("<< {} in {}ms body={}", response.getStatus(), elapsedMs, body);
    }

    private String extractBody(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            return "<empty>";
        }
        if (isBinary(contentType)) {
            return "<binary>";
        }
        String body = new String(content, StandardCharsets.UTF_8);
        if (body.length() > MAX_BODY_LENGTH) {
            return body.substring(0, MAX_BODY_LENGTH) + "...<truncated>";
        }
        return body;
    }

    private boolean isBinary(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase();
        return ct.contains("octet-stream") || ct.contains("multipart");
    }
}
