package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("import_job")
public class ImportJob {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String filename;
    private Integer totalRows;
    private Integer successRows;
    private Integer failedRows;
    private String status;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime createdAt;
}
