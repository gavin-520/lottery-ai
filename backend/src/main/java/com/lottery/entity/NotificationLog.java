package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification_log")
public class NotificationLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String channel;
    private String eventType;
    private String targetUrl;
    private String payload;
    private String status;
    private Integer httpStatus;
    private String errorMessage;
    private String correlationId;
    private Long breachId;
    private Long syncLogId;
    private LocalDateTime createdAt;
}
