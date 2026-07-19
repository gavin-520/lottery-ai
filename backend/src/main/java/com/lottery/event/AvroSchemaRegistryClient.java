package com.lottery.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "lottery.kafka.use-avro", havingValue = "true")
public class AvroSchemaRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(AvroSchemaRegistryClient.class);

    private final RestClient restClient;
    private final String registryUrl;
    private final Map<String, Integer> schemaIds = new ConcurrentHashMap<>();

    public AvroSchemaRegistryClient(@Value("${lottery.kafka.schema-registry-url:}") String registryUrl) {
        this.registryUrl = registryUrl == null ? "" : registryUrl.replaceAll("/$", "");
        this.restClient = RestClient.create();
    }

    public int registerOrGet(String subject, String schemaJson) {
        return schemaIds.computeIfAbsent(subject, key -> resolveSchemaId(key, schemaJson));
    }

    private int resolveSchemaId(String subject, String schemaJson) {
        if (registryUrl.isBlank()) {
            log.warn("Schema registry URL not set; using fallback schema id=1 for {}", subject);
            return 1;
        }
        try {
            var latest = restClient.get()
                    .uri(registryUrl + "/subjects/" + subject + "/versions/latest")
                    .retrieve()
                    .body(Map.class);
            if (latest != null && latest.get("id") instanceof Number id) {
                return id.intValue();
            }
        } catch (Exception ignored) {
            // register below
        }
        try {
            var body = Map.of("schema", schemaJson);
            var created = restClient.post()
                    .uri(registryUrl + "/subjects/" + subject + "/versions")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (created != null && created.get("id") instanceof Number id) {
                log.info("Registered Avro schema {} id={}", subject, id.intValue());
                return id.intValue();
            }
        } catch (Exception ex) {
            log.warn("Schema registry register failed for {}: {}", subject, ex.getMessage());
        }
        return 1;
    }
}
