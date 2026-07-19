package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sla_breach_log")
public class SlaBreachLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String metric;
    private Double thresholdValue;
    private Double actualValue;
    private String severity;
    private String region;
    private String correlationId;
    private String message;
    private LocalDateTime createdAt;
}
