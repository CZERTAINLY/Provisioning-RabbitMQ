package com.czertainly.rabbitImporter.service;

import com.czertainly.rabbitImporter.config.RabbitMQProperties;
import org.slf4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Prepares the structure for proxy exchanges, queues, and their bindings.
 * <p>
 * <b>Communication Flow:</b>
 * <pre>
 * Exchange | Routing Key           | Queue
 * ---------|-----------------------|--------------------
 * proxy    | request.{proxyName}   | {proxyName}
 * proxy    | response.{proxyName}  | proxy-response
 * </pre>
 * <p>
 * <b>Initial Setup:</b><br>
 * At least one proxy must exist. The following structure is required:
 * <ul>
 *   <li>Exchange: "proxy" (constant)</li>
 *   <li>Queue: "{proxyName}"</li>
 *   <li>Binding (Core &rarr; Proxy): {@code /exchanges/proxy/request.{proxyName} --> /queues/{proxyName}}</li>
 *   <li>Binding (Proxy &rarr; Core): {@code /exchanges/proxy/response.{proxyName} --> /queues/proxy-response}</li>
 * </ul>
 * <p>
 * <b>For any additional Proxy:</b>
 * <ul>
 *   <li>Queue: "proxy2"</li>
 *   <li>Binding (Core &rarr; Proxy2): {@code /exchanges/proxy/request.proxy2 --> /queues/proxy2}</li>
 *   <li>Binding (Proxy2 &rarr; Core): {@code /exchanges/proxy/response.proxy2 --> /queues/proxy-response}</li>
 * </ul>
 */
@Service
public class RabbitProxyManagementService {

    private static final Logger log = getLogger(RabbitProxyManagementService.class);
    private static final String PROXY_RESPONSE_QUEUE_NAME = "proxy-response";

    private final RabbitApiService rabbitApiService;

    public RabbitProxyManagementService(RabbitApiService rabbitApiService) {
        this.rabbitApiService = rabbitApiService;
    }

    public void addProxy(String proxyName, String vhost) throws RabbitConfigurationException {
        log.info("=== Adding proxy: {}, vhost: {} ===", proxyName, vhost);
        try {
            rabbitApiService.createExchangeIfNotExist("proxy", vhost);
            rabbitApiService.createQueueIfNotExist(proxyName, vhost);
            rabbitApiService.createQueueIfNotExist(PROXY_RESPONSE_QUEUE_NAME, vhost);
            rabbitApiService.createBindingIfNotExist("proxy", "request." + proxyName, proxyName, vhost);
            rabbitApiService.createBindingIfNotExist("proxy", "response.*", PROXY_RESPONSE_QUEUE_NAME, vhost);
        } catch (Exception e) {
            log.error("Failed to add proxy", e);
            throw new RabbitConfigurationException("Failed to add proxy", e);
        }
        log.info("=== Proxy added successfully: {}, vhost: {} ===", proxyName, vhost);
    }
}
