package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PlatformEventItem {

    private Long id;
    private String eventType;
    private String topic;
    private String payload;
    private String schemaVersion;
    private String region;
    private String correlationId;
    private boolean published;
    private LocalDateTime createdAt;
}
