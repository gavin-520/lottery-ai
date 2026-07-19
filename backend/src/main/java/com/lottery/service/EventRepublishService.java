package com.lottery.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.entity.PlatformEvent;
import com.lottery.event.EventPublisher;
import com.lottery.mapper.PlatformEventMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class EventRepublishService {

    private final PlatformEventMapper platformEventMapper;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${lottery.kafka.topic-dlq:lottery.events.dlq}")
    private String dlqTopic;

    public EventRepublishService(PlatformEventMapper platformEventMapper,
                                 EventPublisher eventPublisher,
                                 ObjectMapper objectMapper) {
        this.platformEventMapper = platformEventMapper;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    public boolean republish(Long eventId) {
        PlatformEvent event = platformEventMapper.selectById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("Event not found: " + eventId);
        }
        Map<String, Object> payload = parsePayload(event.getPayload());
        boolean ok = eventPublisher.publish(event.getTopic(), event.getEventType(), payload);
        if (ok) {
            event.setPublished(1);
            platformEventMapper.updateById(event);
        } else {
            publishDlq(event, "Republish failed");
        }
        return ok;
    }

    public void publishDlq(PlatformEvent event, String errorMessage) {
        Map<String, Object> dlq = new HashMap<>();
        dlq.put("originalTopic", event.getTopic());
        dlq.put("eventType", event.getEventType());
        dlq.put("payloadJson", event.getPayload());
        dlq.put("errorMessage", errorMessage);
        dlq.put("region", event.getRegion());
        dlq.put("correlationId", event.getCorrelationId());
        dlq.put("schemaVersion", event.getSchemaVersion());
        dlq.put("timestamp", System.currentTimeMillis());
        eventPublisher.publish(dlqTopic, "events.dlq", dlq);
    }

    private Map<String, Object> parsePayload(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of("raw", json != null ? json : "{}");
        }
    }
}
