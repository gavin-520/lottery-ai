package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("external_api_sla_log")
public class ExternalApiSlaLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String provider;
    private String endpoint;
    private Integer latencyMs;
    private Integer httpStatus;
    private Integer success;
    private String errorType;
    private String region;
    private String correlationId;
    private LocalDateTime createdAt;
}
