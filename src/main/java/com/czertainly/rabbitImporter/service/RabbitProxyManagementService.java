package com.czertainly.rabbitImporter.service;

import com.czertainly.rabbitImporter.model.OperationResult;
import org.slf4j.Logger;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

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
 */
@Service
public class RabbitProxyManagementService {

    private static final Logger logger = getLogger(RabbitProxyManagementService.class);
    private static final String PROXY_RESPONSE_QUEUE_NAME = "proxy-response";
    private static final String PROXY_NAME = "proxy";
    private static final String ROUTING_KEY_REQUEST_PREFIX = "request.";
    private static final String ROUTING_KEY_RESPONSE_WITH_WILDCART = "response.*";

    private final RabbitApiService rabbitApiService;

    public RabbitProxyManagementService(RabbitApiService rabbitApiService) {
        this.rabbitApiService = rabbitApiService;
    }

    public OperationResult addProxy(String proxyName, String vhost, String username) throws RabbitConfigurationException {
        logger.info("=== Adding proxy: {}, vhost: {} ===", proxyName, vhost);
        OperationResult stats = new OperationResult();
        rabbitApiService.createVhostIfNotExist(vhost, stats);
        rabbitApiService.createUserRightsForVhost(vhost, username, stats);
        rabbitApiService.createExchangeIfNotExist(PROXY_NAME, vhost, stats);
        rabbitApiService.createQueueIfNotExist(proxyName, vhost, stats);
        rabbitApiService.createQueueIfNotExist(PROXY_RESPONSE_QUEUE_NAME, vhost, stats);
        rabbitApiService.createBindingIfNotExist(PROXY_NAME, ROUTING_KEY_REQUEST_PREFIX + proxyName, proxyName, vhost, stats);
        rabbitApiService.createBindingIfNotExist(PROXY_NAME, ROUTING_KEY_RESPONSE_WITH_WILDCART, PROXY_RESPONSE_QUEUE_NAME, vhost, stats);

        logger.info("=== Proxy added successfully: {}, vhost: {} ===", proxyName, vhost);
        return stats;
    }
}
