package com.lottery.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lottery.common.Result;
import com.lottery.entity.LotteryHistory;
import com.lottery.service.HistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public Result<Page<LotteryHistory>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(historyService.list(page, size));
    }
}
