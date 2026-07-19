package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lottery.entity.LotteryHistory;
import com.lottery.mapper.LotteryHistoryMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HistoryService {

    private final LotteryHistoryMapper lotteryHistoryMapper;

    public HistoryService(LotteryHistoryMapper lotteryHistoryMapper) {
        this.lotteryHistoryMapper = lotteryHistoryMapper;
    }

    @Cacheable(value = "historyPage", key = "#page + '-' + #size")
    public Page<LotteryHistory> list(int page, int size) {
        return lotteryHistoryMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<LotteryHistory>().orderByDesc(LotteryHistory::getPeriod)
        );
    }

    @Cacheable(value = "historyAll")
    public List<LotteryHistory> listAllAsc() {
        return lotteryHistoryMapper.selectList(
                new LambdaQueryWrapper<LotteryHistory>().orderByAsc(LotteryHistory::getPeriod)
        );
    }
}
