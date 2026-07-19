package com.lottery.controller;

import com.lottery.common.Result;
import com.lottery.dto.*;
import com.lottery.service.AgentService;
import com.lottery.service.AnalyticsService;
import com.lottery.service.BacktestService;
import com.lottery.service.PredictService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PredictController {

    private final PredictService predictService;
    private final BacktestService backtestService;
    private final AnalyticsService analyticsService;
    private final AgentService agentService;

    public PredictController(PredictService predictService,
                             BacktestService backtestService,
                             AnalyticsService analyticsService,
                             AgentService agentService) {
        this.predictService = predictService;
        this.backtestService = backtestService;
        this.analyticsService = analyticsService;
        this.agentService = agentService;
    }

    @GetMapping("/predict/rules")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<PredictResponse> predictRules(@RequestParam(required = false) String period) {
        return Result.ok(predictService.predictByRules(period));
    }

    @GetMapping("/predict/ai")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<PredictResponse> predictAi(@RequestParam(required = false) String period) {
        return Result.ok(predictService.predictByAi(period));
    }

    @PostMapping("/predict")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<PredictResponse> predictHybrid(@RequestParam(required = false) String period) {
        return Result.ok(predictService.predictHybrid(period));
    }

    @GetMapping("/predict/models")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<ModelCompareResponse> compareModels(@RequestParam(required = false) String period) {
        return Result.ok(agentService.compareModels(period));
    }

    @PostMapping("/backtest/run")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<BacktestResponse> runBacktest(@RequestBody(required = false) BacktestRequest request) {
        BacktestRequest req = request != null ? request : new BacktestRequest();
        return Result.ok(backtestService.run(req));
    }

    @GetMapping("/analytics/frequency")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','USER')")
    public Result<AnalyticsSummary> frequency() {
        return Result.ok(analyticsService.getFrequencySummary());
    }

    @PostMapping("/agent/analyze")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<AgentAnalysisResponse> analyze(@RequestParam(required = false) String question) {
        return Result.ok(agentService.analyze(question));
    }

    @PostMapping("/agent/workflow")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public Result<AgentWorkflowResponse> workflow(@RequestParam(required = false) String question) {
        return Result.ok(agentService.runWorkflow(question));
    }
}
