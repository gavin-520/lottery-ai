package com.lottery.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnExpression("'${lottery.kafka.enabled:false}'.equals('true') && '${lottery.kafka.use-avro:false}'.equals('true')")
public class AvroKafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AvroKafkaEventPublisher.class);

    private final KafkaTemplate<String, byte[]> avroKafkaTemplate;
    private final AvroEventEncoder avroEventEncoder;
    private final AvroSchemaRegistryClient schemaRegistryClient;

    @Value("${lottery.kafka.topic-sync:lottery.sync.completed}")
    private String syncTopic;

    @Value("${lottery.kafka.topic-predict:lottery.predict.created}")
    private String predictTopic;

    @Value("${lottery.kafka.topic-sync-failed:lottery.sync.failed}")
    private String syncFailedTopic;

    @Value("${lottery.kafka.topic-sla-breach:lottery.sla.breach}")
    private String slaBreachTopic;

    public AvroKafkaEventPublisher(KafkaTemplate<String, byte[]> avroKafkaTemplate,
                                   AvroEventEncoder avroEventEncoder,
                                   AvroSchemaRegistryClient schemaRegistryClient) {
        this.avroKafkaTemplate = avroKafkaTemplate;
        this.avroEventEncoder = avroEventEncoder;
        this.schemaRegistryClient = schemaRegistryClient;
    }

    @Override
    public boolean publish(String topic, String eventType, Map<String, Object> payload) {
        try {
            int schemaId;
            byte[] bytes;
            if (topic.equals(predictTopic)) {
                schemaId = schemaRegistryClient.registerOrGet(
                        predictTopic + "-value", avroEventEncoder.predictSchema().toString());
                bytes = avroEventEncoder.encodePredictEvent(payload, schemaId);
            } else if (topic.equals(syncFailedTopic)) {
                schemaId = schemaRegistryClient.registerOrGet(
                        syncFailedTopic + "-value", avroEventEncoder.syncFailedSchema().toString());
                bytes = avroEventEncoder.encodeSyncFailedEvent(payload, schemaId);
            } else if (topic.equals(slaBreachTopic)) {
                schemaId = schemaRegistryClient.registerOrGet(
                        slaBreachTopic + "-value", avroEventEncoder.slaBreachSchema().toString());
                bytes = avroEventEncoder.encodeSlaBreachEvent(payload, schemaId);
            } else if (topic.contains("dlq")) {
                schemaId = schemaRegistryClient.registerOrGet(
                        topic + "-value", avroEventEncoder.dlqSchema().toString());
                bytes = avroEventEncoder.encodeDlqEvent(payload, schemaId);
            } else {
                schemaId = schemaRegistryClient.registerOrGet(
                        syncTopic + "-value", avroEventEncoder.syncSchema().toString());
                bytes = avroEventEncoder.encodeSyncEvent(payload, schemaId);
            }
            avroKafkaTemplate.send(topic, eventType, bytes).get();
            log.debug("Published Avro event topic={} type={} schemaId={}", topic, eventType, schemaId);
            return true;
        } catch (Exception ex) {
            log.warn("Avro Kafka publish failed: {}", ex.getMessage());
            return false;
        }
    }
}
