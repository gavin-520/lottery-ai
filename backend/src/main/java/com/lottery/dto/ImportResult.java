package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImportResult {

    private Long jobId;
    private String filename;
    private int totalRows;
    private int successRows;
    private int failedRows;
    private String status;
}
