package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AgentWorkflowResponse {

    private String finalReport;
    private List<WorkflowStep> steps;
    private String workflowName;
}
