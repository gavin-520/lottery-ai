package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("predict_record")
public class PredictRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String period;
    /** SSQ | FC3D — defaults to SSQ for legacy rows */
    private String lotteryType;
    private String modelName;
    private String redBalls;
    private String blueBall;
    private BigDecimal confidence;
    private Long createdBy;
    private LocalDateTime createdAt;
}
