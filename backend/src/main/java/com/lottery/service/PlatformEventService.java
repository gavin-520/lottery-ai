package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.dto.PlatformEventItem;
import com.lottery.entity.PlatformEvent;
import com.lottery.event.EventPublisher;
import com.lottery.mapper.PlatformEventMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PlatformEventService {

    private final PlatformEventMapper platformEventMapper;
    private final EventPublisher eventPublisher;
    private final EventRepublishService eventRepublishService;
    private final ObjectMapper objectMapper;

    @Value("${lottery.kafka.topic-sync:lottery.sync.completed}")
    private String syncTopic;

    @Value("${lottery.kafka.topic-predict:lottery.predict.created}")
    private String predictTopic;

    @Value("${lottery.kafka.topic-sync-failed:lottery.sync.failed}")
    private String syncFailedTopic;

    @Value("${lottery.kafka.topic-sla-breach:lottery.sla.breach}")
    private String slaBreachTopic;

    @Value("${lottery.region:local}")
    private String region;

    @Value("${lottery.kafka.schema-version:1.0}")
    private String schemaVersion;

    public PlatformEventService(PlatformEventMapper platformEventMapper,
                                EventPublisher eventPublisher,
                                EventRepublishService eventRepublishService,
                                ObjectMapper objectMapper) {
        this.platformEventMapper = platformEventMapper;
        this.eventPublisher = eventPublisher;
        this.eventRepublishService = eventRepublishService;
        this.objectMapper = objectMapper;
    }

    public void publishSyncEvent(Map<String, Object> payload) {
        publish(syncTopic, "sync.completed", enrich(payload, "sync.completed"));
    }

    public void publishSyncEvent(Map<String, Object> payload, String correlationId) {
        payload.put("correlationId", correlationId);
        publishSyncEvent(payload);
    }

    public void publishSyncFailedEvent(Map<String, Object> payload) {
        publish(syncFailedTopic, "sync.failed", enrich(payload, "sync.failed"));
    }

    public void publishSyncFailedEvent(Map<String, Object> payload, String correlationId) {
        payload.put("correlationId", correlationId);
        publishSyncFailedEvent(payload);
    }

    public void publishPredictEvent(Map<String, Object> payload) {
        Map<String, Object> enriched = enrich(payload, "predict.created");
        enriched.putIfAbsent("type", "predict.created");
        publish(predictTopic, "predict.created", enriched);
    }

    public void publishPredictEvent(Map<String, Object> payload, String correlationId) {
        payload.put("correlationId", correlationId);
        publishPredictEvent(payload);
    }

    public void publishBreachEvent(Map<String, Object> payload) {
        publish(slaBreachTopic, "sla.breach", enrich(payload, "sla.breach"));
    }

    public void publishBreachEvent(Map<String, Object> payload, String correlationId) {
        payload.put("correlationId", correlationId);
        publishBreachEvent(payload);
    }

    private Map<String, Object> enrich(Map<String, Object> payload, String eventType) {
        Map<String, Object> copy = new HashMap<>(payload);
        copy.putIfAbsent("type", eventType);
        copy.put("region", region);
        copy.put("schemaVersion", schemaVersion);
        copy.put("timestamp", System.currentTimeMillis());
        copy.putIfAbsent("correlationId", UUID.randomUUID().toString());
        return copy;
    }

    private void publish(String topic, String eventType, Map<String, Object> payload) {
        PlatformEvent event = new PlatformEvent();
        event.setEventType(eventType);
        event.setTopic(topic);
        event.setSchemaVersion(schemaVersion);
        event.setRegion(region);
        event.setCorrelationId(String.valueOf(payload.get("correlationId")));
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            event.setPayload("{}");
        }
        event.setPublished(0);
        platformEventMapper.insert(event);

        boolean published = eventPublisher.publish(topic, eventType, payload);
        if (published) {
            event.setPublished(1);
            platformEventMapper.updateById(event);
        } else {
            eventRepublishService.publishDlq(event, "Kafka publish failed");
        }
    }

    public Page<PlatformEventItem> list(int page, int size) {
        return list(page, size, null, null, null);
    }

    public Page<PlatformEventItem> list(int page, int size, String eventType, String targetRegion, String correlationId) {
        LambdaQueryWrapper<PlatformEvent> query = new LambdaQueryWrapper<PlatformEvent>()
                .orderByDesc(PlatformEvent::getId);
        if (StringUtils.hasText(eventType)) {
            query.eq(PlatformEvent::getEventType, eventType);
        }
        if (StringUtils.hasText(targetRegion)) {
            query.eq(PlatformEvent::getRegion, targetRegion);
        }
        if (StringUtils.hasText(correlationId)) {
            query.eq(PlatformEvent::getCorrelationId, correlationId);
        }
        Page<PlatformEvent> result = platformEventMapper.selectPage(new Page<>(page, size), query);
        Page<PlatformEventItem> mapped = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapped.setRecords(result.getRecords().stream()
                .map(e -> new PlatformEventItem(
                        e.getId(), e.getEventType(), e.getTopic(), e.getPayload(),
                        e.getSchemaVersion(), e.getRegion(), e.getCorrelationId(),
                        e.getPublished() != null && e.getPublished() == 1, e.getCreatedAt()))
                .toList());
        return mapped;
    }
}
