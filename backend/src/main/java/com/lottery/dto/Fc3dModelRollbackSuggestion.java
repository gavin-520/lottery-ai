package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Sprint 10-E §4: advisory-only rollback suggestion. {@code Fc3dModelRollbackService} never
 * executes a rollback itself — it only recommends one for an operator to review and apply
 * (e.g. via {@code POST /model/activate}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dModelRollbackSuggestion {
    private boolean rollbackSuggested;
    private String current;
    /** Suggested version to roll back to, or null if no safe fallback could be determined. */
    private String fallback;
    private List<String> reason;
}
