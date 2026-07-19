package com.lottery.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "lottery.kafka.enabled", havingValue = "true")
public class KafkaTopicConfig {

    @Bean
    public NewTopic syncTopic(@Value("${lottery.kafka.topic-sync:lottery.sync.completed}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic predictTopic(@Value("${lottery.kafka.topic-predict:lottery.predict.created}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic syncFailedTopic(@Value("${lottery.kafka.topic-sync-failed:lottery.sync.failed}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic slaBreachTopic(@Value("${lottery.kafka.topic-sla-breach:lottery.sla.breach}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic dlqTopic(@Value("${lottery.kafka.topic-dlq:lottery.events.dlq}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
