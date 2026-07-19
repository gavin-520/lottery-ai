package com.lottery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lottery.entity.LotteryHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LotteryHistoryMapper extends BaseMapper<LotteryHistory> {
}
