package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Sprint 10-D §6: audit entry recorded each time the production FC3D model version changes. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fc3dModelSwitchRecord {
    private String fromVersion;
    /** Null for a pure "deactivate" entry (no confirmed replacement was promoted). */
    private String toVersion;
    private String operator;
    private String reason;
    private LocalDateTime switchedAt;
}
