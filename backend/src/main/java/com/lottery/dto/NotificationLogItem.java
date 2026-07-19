package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class NotificationLogItem {

    private Long id;
    private String channel;
    private String eventType;
    private String targetUrl;
    private String status;
    private Integer httpStatus;
    private String errorMessage;
    private String correlationId;
    private Long breachId;
    private Long syncLogId;
    private LocalDateTime createdAt;
}
