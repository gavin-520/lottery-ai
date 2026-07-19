package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WorkflowStep {

    private String agent;
    private String role;
    private String output;
}
