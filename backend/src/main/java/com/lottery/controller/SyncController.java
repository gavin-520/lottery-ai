package com.lottery.controller;

import com.lottery.common.Result;
import com.lottery.dto.RegionSlaSummary;
import com.lottery.dto.SyncLogItem;
import com.lottery.dto.SyncStatusResponse;
import com.lottery.service.DataSyncService;
import com.lottery.service.SseEventService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SyncController {

    private final DataSyncService dataSyncService;
    private final SseEventService sseEventService;

    public SyncController(DataSyncService dataSyncService, SseEventService sseEventService) {
        this.dataSyncService = dataSyncService;
        this.sseEventService = sseEventService;
    }

    @GetMapping("/sync/status")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','USER')")
    public Result<SyncStatusResponse> status() {
        return Result.ok(dataSyncService.getStatus());
    }

    @GetMapping("/admin/sync/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<SyncLogItem>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return Result.ok(dataSyncService.listLogs(page, size, status));
    }

    @GetMapping("/admin/sync/regions")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<RegionSlaSummary>> regionStats(@RequestParam(defaultValue = "24") int hours) {
        return Result.ok(dataSyncService.regionStats(hours));
    }

    @PostMapping("/admin/sync/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SyncStatusResponse> trigger() {
        return Result.ok(dataSyncService.syncNow());
    }

    @PostMapping("/admin/sync/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SyncStatusResponse> retry() {
        return Result.ok(dataSyncService.syncNow());
    }

    @PostMapping("/admin/sync/retry/{logId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SyncStatusResponse> retryByLogId(@PathVariable Long logId) {
        return Result.ok(dataSyncService.retryByLogId(logId));
    }

    @GetMapping("/events/stream")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','USER')")
    public SseEmitter stream() {
        return sseEventService.subscribe();
    }
}
