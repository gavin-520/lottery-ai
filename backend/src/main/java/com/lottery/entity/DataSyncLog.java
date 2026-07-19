package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_sync_log")
public class DataSyncLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String source;
    private String status;
    private Integer fetchedCount;
    private Integer newCount;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String region;
    private String correlationId;
    private String errorType;
    private Integer httpStatus;
    private Long parentLogId;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
}
