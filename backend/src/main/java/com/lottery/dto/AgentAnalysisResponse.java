package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class AgentAnalysisResponse {

    private String summary;
    private List<String> insights;
    private List<String> recommendations;
    private String agentName;
}
