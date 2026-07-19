package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SlaBreachItem {

    private Long id;
    private String metric;
    private double thresholdValue;
    private double actualValue;
    private String severity;
    private String region;
    private String correlationId;
    private String message;
    private LocalDateTime createdAt;
}
