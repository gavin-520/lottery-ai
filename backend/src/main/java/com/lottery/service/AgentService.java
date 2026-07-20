package com.lottery.service;

import com.lottery.dto.AgentAnalysisResponse;
import com.lottery.dto.AgentWorkflowResponse;
import com.lottery.dto.BallFrequencyItem;
import com.lottery.dto.Fc3dAnalyticsSummary;
import com.lottery.dto.ModelCompareResponse;
import com.lottery.dto.ModelPredictionItem;
import com.lottery.dto.WorkflowStep;
import com.lottery.entity.LotteryHistory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Agent entrypoints. Primary analysis path is FC3D; SSQ model-compare remains available for legacy.
 */
@Service
public class AgentService {

    private final Fc3dAnalyticsService fc3dAnalyticsService;
    private final HistoryService historyService;
    private final AiServiceClient aiServiceClient;

    public AgentService(Fc3dAnalyticsService fc3dAnalyticsService,
                        HistoryService historyService,
                        AiServiceClient aiServiceClient) {
        this.fc3dAnalyticsService = fc3dAnalyticsService;
        this.historyService = historyService;
        this.aiServiceClient = aiServiceClient;
    }

    public AgentAnalysisResponse analyze(String question) {
        Fc3dAnalyticsSummary summary = fc3dAnalyticsService.getFrequencySummary();
        return buildFc3dAnalysis(summary, question);
    }

    public ModelCompareResponse compareModels(String period) {
        List<LotteryHistory> history = historyService.listAllAsc();
        ModelCompareResponse response = aiServiceClient.compareModels(history, period);
        if (response != null) {
            return response;
        }
        return new ModelCompareResponse(List.of());
    }

    public AgentWorkflowResponse runWorkflow(String question) {
        AgentAnalysisResponse analysis = analyze(question);
        return new AgentWorkflowResponse(
                analysis.getSummary(),
                List.of(
                        new WorkflowStep("fc3d-analyst", "analyst", analysis.getSummary()),
                        new WorkflowStep("fc3d-reviewer", "reviewer", String.join("；", analysis.getInsights())),
                        new WorkflowStep("fc3d-reporter", "reporter", String.join("；", analysis.getRecommendations()))
                ),
                "fc3d-workflow-v1"
        );
    }

    private AgentAnalysisResponse buildFc3dAnalysis(Fc3dAnalyticsSummary summary, String question) {
        List<String> insights = new ArrayList<>();
        topDigits(summary.getDigitFrequency(), 5).forEach(i ->
                insights.add("号码 " + i.getBall() + " 共出现 " + i.getCount() + " 次"));
        topDigits(summary.getPos1Frequency(), 3).forEach(i ->
                insights.add("百位热号 " + i.getBall() + "（" + i.getCount() + "）"));
        topDigits(summary.getPos2Frequency(), 3).forEach(i ->
                insights.add("十位热号 " + i.getBall() + "（" + i.getCount() + "）"));
        topDigits(summary.getPos3Frequency(), 3).forEach(i ->
                insights.add("个位热号 " + i.getBall() + "（" + i.getCount() + "）"));

        String topSum = topEntry(summary.getSumDistribution());
        String topSpan = topEntry(summary.getSpanDistribution());
        String topOe = topEntry(summary.getOddEvenDistribution());
        if (topSum != null) {
            insights.add("高频和值 " + topSum);
        }
        if (topSpan != null) {
            insights.add("高频跨度 " + topSpan);
        }
        if (topOe != null) {
            insights.add("高频奇偶形态 " + topOe);
        }
        insights.add("共分析福彩3D " + summary.getTotalPeriods() + " 期");

        List<String> recommendations = List.of(
                "关注百/十/个位各自热号组合，避免三位同热硬凑",
                "结合和值与跨度区间过滤极端组合",
                "奇偶形态优先参考历史高频模式（O=奇 E=偶）"
        );

        String q = question == null || question.isBlank() ? "福彩3D走势分析" : question;
        String summaryText = "【福彩3D】针对「" + q + "」：基于 "
                + summary.getTotalPeriods() + " 期开奖，完成号码/位置/和值/跨度/奇偶分析。";

        return new AgentAnalysisResponse(summaryText, insights, recommendations, "fc3d-rule-agent");
    }

    private List<BallFrequencyItem> topDigits(List<BallFrequencyItem> items, int limit) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .sorted(Comparator.comparingInt(BallFrequencyItem::getCount).reversed())
                .limit(limit)
                .toList();
    }

    private String topEntry(Map<?, Integer> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return map.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> String.valueOf(e.getKey()) + "（" + e.getValue() + " 次）")
                .orElse(null);
    }
}
