package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_event")
public class PlatformEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventType;
    private String topic;
    private String payload;
    private String schemaVersion;
    private String region;
    private String correlationId;
    private Integer published;
    private LocalDateTime createdAt;
}
