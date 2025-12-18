package com.czertainly.rabbitImporter.model;

import java.util.HashMap;
import java.util.Map;

public class OperationResult {
    private final Map<ObjectType, Map<String, String>> stats;

    public enum ObjectType {
        QUEUE, EXCHANGE, BINDING
    }

    public OperationResult() {
        stats = new HashMap<>();
        stats.put(ObjectType.QUEUE, new HashMap<>());
        stats.put(ObjectType.EXCHANGE, new HashMap<>());
        stats.put(ObjectType.BINDING, new HashMap<>());
    }

    public void queueCreated(String queueName) {
        objectCreated(ObjectType.QUEUE, queueName);
    }

    public void exchangeCreated(String exchangeName) {
        objectCreated(ObjectType.EXCHANGE, exchangeName);
    }

    public void bindingCreated(String bindingName) {
        objectCreated(ObjectType.BINDING, bindingName);
    }

    private void objectCreated(ObjectType type, String objectName) {
        stats.get(type).put(objectName, "200 - Created");
    }

    public void queueAlreadyExists(String queueName) {
        objectAlreadyExists(ObjectType.QUEUE, queueName);
    }

    public void exchangeAlreadyExists(String exchangeName) {
        objectAlreadyExists(ObjectType.EXCHANGE, exchangeName);
    }

    public void bindingAlreadyExists(String bindingName) {
        objectAlreadyExists(ObjectType.BINDING, bindingName);
    }

    private void objectAlreadyExists(ObjectType type, String objectName) {
        stats.get(type).put(objectName, "204 - Not created, already exists");
    }

    public void addStats(ObjectType type, String objectName, String status) {
        stats.get(type).put(objectName, status);
    }

    public Map<ObjectType, Map<String, String>> getStats() {
        return stats;
    }

    @Override
    public String toString() {
        return stats.toString();
    }
}
