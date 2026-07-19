package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SlaLogItem {

    private Long id;
    private String provider;
    private String endpoint;
    private int latencyMs;
    private Integer httpStatus;
    private boolean success;
    private String errorType;
    private String region;
    private LocalDateTime createdAt;
}
