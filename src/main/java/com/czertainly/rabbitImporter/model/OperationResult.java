package com.czertainly.rabbitImporter.model;

import java.util.HashMap;
import java.util.Map;

public class OperationResult {
    private final Map<ObjectType, Map<String, String>> stats;

    public enum ObjectType {
        QUEUE, EXCHANGE, BINDING, VHOST, VHOSTRIGHTS
    }

    public OperationResult() {
        stats = new HashMap<>();
        stats.put(ObjectType.VHOST, new HashMap<>());
        stats.put(ObjectType.VHOSTRIGHTS, new HashMap<>());
        stats.put(ObjectType.QUEUE, new HashMap<>());
        stats.put(ObjectType.EXCHANGE, new HashMap<>());
        stats.put(ObjectType.BINDING, new HashMap<>());
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
