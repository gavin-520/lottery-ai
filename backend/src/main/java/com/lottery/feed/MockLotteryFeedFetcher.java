package com.lottery.feed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.entity.LotteryHistory;
import com.lottery.mapper.LotteryHistoryMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "lottery.sync.feed-type", havingValue = "mock", matchIfMissing = true)
public class MockLotteryFeedFetcher implements LotteryFeedFetcher {

    private final LotteryHistoryMapper lotteryHistoryMapper;
    private final Random random = new Random();

    public MockLotteryFeedFetcher(LotteryHistoryMapper lotteryHistoryMapper) {
        this.lotteryHistoryMapper = lotteryHistoryMapper;
    }

    @Override
    public String source() {
        return "mock-feed";
    }

    @Override
    public List<LotteryHistory> fetch() {
        LotteryHistory latest = lotteryHistoryMapper.selectOne(
                new LambdaQueryWrapper<LotteryHistory>().orderByDesc(LotteryHistory::getPeriod).last("LIMIT 1")
        );
        if (latest == null) {
            return List.of();
        }

        String nextPeriod = nextPeriod(latest.getPeriod());
        Long exists = lotteryHistoryMapper.selectCount(
                new LambdaQueryWrapper<LotteryHistory>().eq(LotteryHistory::getPeriod, nextPeriod)
        );
        if (exists > 0) {
            return List.of();
        }

        List<Integer> reds = new ArrayList<>();
        while (reds.size() < 6) {
            int n = random.nextInt(33) + 1;
            if (!reds.contains(n)) {
                reds.add(n);
            }
        }
        reds.sort(Integer::compareTo);

        LotteryHistory record = new LotteryHistory();
        record.setPeriod(nextPeriod);
        record.setDrawDate(LocalDate.now());
        record.setRedBalls(reds.stream().map(n -> String.format("%02d", n)).collect(Collectors.joining(",")));
        record.setBlueBall(String.format("%02d", random.nextInt(16) + 1));
        return List.of(record);
    }

    private String nextPeriod(String period) {
        try {
            int year = Integer.parseInt(period.substring(0, 4));
            int seq = Integer.parseInt(period.substring(4));
            if (seq >= 999) {
                return (year + 1) + "001";
            }
            return year + String.format("%03d", seq + 1);
        } catch (Exception ex) {
            return period + "-next";
        }
    }
}
