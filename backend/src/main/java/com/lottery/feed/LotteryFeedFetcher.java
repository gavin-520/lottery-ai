package com.lottery.feed;

import com.lottery.entity.LotteryHistory;

import java.util.List;

public interface LotteryFeedFetcher {

    String source();

    List<LotteryHistory> fetch();
}
