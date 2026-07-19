package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SyncStatusResponse {

    private Long lastSyncId;
    private String source;
    private String status;
    private Integer fetchedCount;
    private Integer newCount;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String region;
    private String correlationId;
    private String errorType;
    private Integer httpStatus;
    private boolean schedulerEnabled;
    private String cron;
}
