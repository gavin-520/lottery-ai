package com.lottery.controller;

import com.lottery.common.Result;
import com.lottery.dto.PlatformInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @Value("${lottery.region:local}")
    private String region;

    @Value("${lottery.kafka.enabled:false}")
    private boolean kafkaEnabled;

    @Value("${lottery.kafka.use-avro:false}")
    private boolean avroEnabled;

    @Value("${lottery.kafka.schema-version:1.0}")
    private String schemaVersion;

    @Value("${lottery.kafka.schema-registry-url:}")
    private String schemaRegistryUrl;

    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        return Result.ok(Map.of(
                "status", "UP",
                "service", "lottery-backend",
                "region", region
        ));
    }

    @GetMapping("/platform/info")
    public Result<PlatformInfoResponse> platformInfo() {
        return Result.ok(new PlatformInfoResponse(region, kafkaEnabled, avroEnabled, schemaVersion, schemaRegistryUrl));
    }
}
