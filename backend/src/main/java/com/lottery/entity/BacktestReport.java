package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("backtest_report")
public class BacktestReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String startPeriod;
    private String endPeriod;
    private BigDecimal hitRate;
    private String summary;
    private Long createdBy;
    private LocalDateTime createdAt;
}
