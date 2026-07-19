package com.lottery.event;

import java.util.Map;

public interface EventPublisher {

    /**
     * @return true if publish succeeded (or was logged locally when Kafka disabled)
     */
    boolean publish(String topic, String eventType, Map<String, Object> payload);
}
