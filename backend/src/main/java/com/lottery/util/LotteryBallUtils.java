package com.lottery.util;

import com.lottery.entity.LotteryHistory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class LotteryBallUtils {

    private LotteryBallUtils() {}

    public static List<Integer> parseRedBalls(LotteryHistory record) {
        return Arrays.stream(record.getRedBalls().split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    public static int parseBlueBall(LotteryHistory record) {
        return Integer.parseInt(record.getBlueBall().trim());
    }

    public static int sumRedBalls(LotteryHistory record) {
        return parseRedBalls(record).stream().mapToInt(Integer::intValue).sum();
    }

    public static int spanRedBalls(LotteryHistory record) {
        List<Integer> balls = parseRedBalls(record);
        int min = balls.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = balls.stream().mapToInt(Integer::intValue).max().orElse(0);
        return max - min;
    }

    public static int oddRedCount(LotteryHistory record) {
        return (int) parseRedBalls(record).stream().filter(n -> n % 2 == 1).count();
    }
}
