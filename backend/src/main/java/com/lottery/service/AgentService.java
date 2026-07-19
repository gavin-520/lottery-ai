package com.lottery.service;

import com.lottery.dto.AgentAnalysisResponse;
import com.lottery.dto.AnalyticsSummary;
import com.lottery.dto.ModelCompareResponse;
import com.lottery.dto.ModelPredictionItem;
import com.lottery.entity.LotteryHistory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AgentService {

    private final AnalyticsService analyticsService;
    private final HistoryService historyService;
    private final AiServiceClient aiServiceClient;

    public AgentService(AnalyticsService analyticsService,
                        HistoryService historyService,
                        AiServiceClient aiServiceClient) {
        this.analyticsService = analyticsService;
        this.historyService = historyService;
        this.aiServiceClient = aiServiceClient;
    }

    public AgentAnalysisResponse analyze(String question) {
        AnalyticsSummary summary = analyticsService.getFrequencySummary();
        List<LotteryHistory> history = historyService.listAllAsc();
        AgentAnalysisResponse aiResult = aiServiceClient.analyze(summary, history, question);
        if (aiResult != null) {
            return aiResult;
        }
        return buildFallbackAnalysis(summary, question);
    }

    public ModelCompareResponse compareModels(String period) {
        List<LotteryHistory> history = historyService.listAllAsc();
        ModelCompareResponse response = aiServiceClient.compareModels(history, period);
        if (response != null) {
            return response;
        }
        return new ModelCompareResponse(List.of());
    }

    public com.lottery.dto.AgentWorkflowResponse runWorkflow(String question) {
        AnalyticsSummary summary = analyticsService.getFrequencySummary();
        List<LotteryHistory> history = historyService.listAllAsc();
        com.lottery.dto.AgentWorkflowResponse result = aiServiceClient.runWorkflow(summary, history, question);
        if (result != null) {
            return result;
        }
        AgentAnalysisResponse fallback = buildFallbackAnalysis(summary, question);
        return new com.lottery.dto.AgentWorkflowResponse(
                fallback.getSummary(),
                List.of(new com.lottery.dto.WorkflowStep("rule-agent", "analyst", fallback.getSummary())),
                "rule-workflow-v1"
        );
    }

    private AgentAnalysisResponse buildFallbackAnalysis(AnalyticsSummary summary, String question) {
        List<String> insights = new ArrayList<>();
        var topRed = summary.getRedFrequency().stream()
                .sorted(Comparator.comparingInt(com.lottery.dto.BallFrequencyItem::getCount).reversed())
                .limit(5)
                .map(i -> "红球 " + i.getBall() + " 出现 " + i.getCount() + " 次")
                .toList();
        var topBlue = summary.getBlueFrequency().stream()
                .sorted(Comparator.comparingInt(com.lottery.dto.BallFrequencyItem::getCount).reversed())
                .limit(3)
                .map(i -> "蓝球 " + i.getBall() + " 出现 " + i.getCount() + " 次")
                .toList();

        insights.addAll(topRed);
        insights.addAll(topBlue);
        insights.add("共分析 " + summary.getTotalPeriods() + " 期历史数据");

        List<String> recommendations = List.of(
                "关注高频红球组合，结合遗漏值进行二次筛选",
                "蓝球建议参考近 10 期冷热趋势",
                "预测结果仅供研究，不构成投注建议"
        );

        String summaryText = question != null && !question.isBlank()
                ? "针对问题「" + question + "」的分析：当前热号集中在高频红球区间。"
                : "基于历史频率的统计分析：热号与冷号分布存在明显差异。";

        return new AgentAnalysisResponse(summaryText, insights, recommendations, "rule-agent-v1");
    }
}
