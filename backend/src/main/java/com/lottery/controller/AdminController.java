package com.lottery.controller;

import com.lottery.common.Result;
import com.lottery.dto.CreateUserRequest;
import com.lottery.dto.ImportResult;
import com.lottery.dto.NotificationLogItem;
import com.lottery.dto.OpsOverviewResponse;
import com.lottery.dto.PlatformEventItem;
import com.lottery.dto.SlaBreachItem;
import com.lottery.dto.SlaLogItem;
import com.lottery.dto.SlaSummaryResponse;
import com.lottery.dto.TraceResponse;
import com.lottery.dto.UserSummary;
import com.lottery.service.EventRepublishService;
import com.lottery.service.ExternalApiSlaService;
import com.lottery.service.ImportService;
import com.lottery.service.OpsService;
import com.lottery.service.PlatformEventService;
import com.lottery.service.SlaBreachService;
import com.lottery.service.TraceService;
import com.lottery.service.UserService;
import com.lottery.service.WebhookNotificationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;
    private final ImportService importService;
    private final PlatformEventService platformEventService;
    private final ExternalApiSlaService externalApiSlaService;
    private final SlaBreachService slaBreachService;
    private final TraceService traceService;
    private final OpsService opsService;

    public AdminController(UserService userService,
                           ImportService importService,
                           PlatformEventService platformEventService,
                           ExternalApiSlaService externalApiSlaService,
                           SlaBreachService slaBreachService,
                           TraceService traceService,
                           OpsService opsService) {
        this.userService = userService;
        this.importService = importService;
        this.platformEventService = platformEventService;
        this.externalApiSlaService = externalApiSlaService;
        this.slaBreachService = slaBreachService;
        this.traceService = traceService;
        this.opsService = opsService;
    }

    @GetMapping("/users")
    public Result<List<UserSummary>> listUsers() {
        return Result.ok(userService.listUsers());
    }

    @PostMapping("/users")
    public Result<UserSummary> createUser(@Valid @RequestBody CreateUserRequest request) {
        return Result.ok(userService.createUser(request));
    }

    @PostMapping("/import")
    public Result<ImportResult> importCsv(@RequestParam("file") MultipartFile file) throws Exception {
        return Result.ok(importService.importCsv(file));
    }

    @GetMapping("/events")
    public Result<Page<PlatformEventItem>> listEvents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String correlationId) {
        return Result.ok(platformEventService.list(page, size, eventType, region, correlationId));
    }

    @GetMapping("/sla/summary")
    public Result<SlaSummaryResponse> slaSummary(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(required = false) String region) {
        return Result.ok(externalApiSlaService.summary(hours, region));
    }

    @GetMapping("/sla/logs")
    public Result<Page<SlaLogItem>> slaLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String region) {
        return Result.ok(externalApiSlaService.list(page, size, region));
    }

    @GetMapping("/sla/breaches")
    public Result<Page<SlaBreachItem>> slaBreaches(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(slaBreachService.list(page, size));
    }

    @GetMapping("/trace/{correlationId}")
    public Result<TraceResponse> trace(@PathVariable String correlationId) {
        return Result.ok(traceService.trace(correlationId));
    }

    @GetMapping("/ops/overview")
    public Result<OpsOverviewResponse> opsOverview(@RequestParam(defaultValue = "24") int hours) {
        return Result.ok(opsService.overview(hours));
    }
}
