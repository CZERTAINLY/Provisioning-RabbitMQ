package com.czertainly.rabbitImporter.model;

import java.util.List;
import java.util.Map;

public record Definitions(
        List<Exchange> exchanges,
        List<Queue> queues,
        List<Binding> bindings
) {
    public record Exchange(
            String name,
            String type,
            boolean durable,
            boolean auto_delete,
            Map<String, Object> arguments)
    {}
    public record Queue(
            String name,
            boolean durable,
            boolean auto_delete,
            Map<String, Object> arguments)
    {}
    public record Binding(
            String source,
            String vhost,
            String destination,
            String destination_type,
            String routing_key,
            Map<String,Object> arguments)
    {}
}