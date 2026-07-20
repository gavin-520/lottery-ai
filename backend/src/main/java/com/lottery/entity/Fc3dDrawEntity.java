package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fc3d_draw_record")
public class Fc3dDrawEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String issue;
    private Integer digit1;
    private Integer digit2;
    private Integer digit3;
    private Integer sumValue;
    private Integer spanValue;
    private String oddEvenPattern;
    private LocalDate drawDate;
    private String lotteryType;
    private LocalDateTime createdAt;
}
