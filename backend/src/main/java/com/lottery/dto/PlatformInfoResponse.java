package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PlatformInfoResponse {

    private String region;
    private boolean kafkaEnabled;
    private boolean avroEnabled;
    private String schemaVersion;
    private String schemaRegistryUrl;
}
