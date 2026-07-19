package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TraceResponse {

    private String correlationId;
    private List<PlatformEventItem> events;
    private List<SyncLogItem> syncLogs;
    private List<SlaLogItem> slaLogs;
    private List<SlaBreachItem> breaches;
}
