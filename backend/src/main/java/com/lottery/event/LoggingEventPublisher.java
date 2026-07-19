package com.lottery.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "lottery.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public boolean publish(String topic, String eventType, Map<String, Object> payload) {
        log.info("Event [{}] on topic {}: {}", eventType, topic, payload);
        return true;
    }
}
