package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dSyncStatusResponse {

    private String source;
    private String status;
    private int fetchedCount;
    private int newCount;
    private String message;
    private String latestIssue;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private boolean schedulerEnabled;
    private String cron;
    private String zone;
    private long totalInDb;
}
