package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SyncLogItem {

    private Long id;
    private String source;
    private String status;
    private Integer fetchedCount;
    private Integer newCount;
    private String message;
    private String region;
    private String correlationId;
    private String errorType;
    private Integer httpStatus;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long parentLogId;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
}
