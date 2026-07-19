package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("lottery_history")
public class LotteryHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String period;
    private LocalDate drawDate;
    private String redBalls;
    private String blueBall;
    private LocalDateTime createdAt;
}
