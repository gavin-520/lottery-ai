package com.lottery.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnExpression("'${lottery.kafka.enabled:false}'.equals('true') && !'${lottery.kafka.use-avro:false}'.equals('true')")
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public boolean publish(String topic, String eventType, Map<String, Object> payload) {
        try {
            kafkaTemplate.send(topic, eventType, payload).get();
            log.debug("Published to Kafka topic={} type={}", topic, eventType);
            return true;
        } catch (Exception ex) {
            log.warn("Kafka publish failed: {}", ex.getMessage());
            return false;
        }
    }
}
