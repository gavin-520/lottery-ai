package com.lottery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.context.CorrelationIdContext;
import com.lottery.filter.CorrelationIdFilter;
import com.lottery.dto.AgentAnalysisResponse;
import com.lottery.dto.AgentWorkflowResponse;
import com.lottery.dto.AnalyticsSummary;
import com.lottery.dto.ModelCompareResponse;
import com.lottery.dto.ModelPredictionItem;
import com.lottery.dto.PredictResponse;
import com.lottery.dto.WorkflowStep;
import com.lottery.entity.LotteryHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiServiceClient(@Value("${lottery.ai-service-url:http://localhost:8000}") String baseUrl,
                           ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    private JsonNode post(String uri, Object body) {
        return restClient.post()
                .uri(uri)
                .header(CorrelationIdFilter.HEADER, CorrelationIdContext.getOrGenerate())
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    public PredictResponse predict(List<LotteryHistory> history, String period) {
        try {
            JsonNode response = post("/api/v1/predict",
                    Map.of("period", period != null ? period : "next", "history", toHistoryPayload(history)));
            return parsePredictResponse(response, "ai");
        } catch (Exception ex) {
            log.warn("AI service unavailable: {}", ex.getMessage());
            return null;
        }
    }

    public PredictResponse predictEnsemble(List<LotteryHistory> history, String period) {
        try {
            JsonNode response = post("/api/v1/predict/ensemble",
                    Map.of("period", period != null ? period : "next", "history", toHistoryPayload(history)));
            return parsePredictResponse(response, "ensemble");
        } catch (Exception ex) {
            log.warn("AI ensemble unavailable: {}", ex.getMessage());
            return null;
        }
    }

    public ModelCompareResponse compareModels(List<LotteryHistory> history, String period) {
        try {
            JsonNode response = post("/api/v1/models/compare",
                    Map.of("period", period != null ? period : "next", "history", toHistoryPayload(history)));

            if (response == null || !response.has("models")) {
                return null;
            }

            List<ModelPredictionItem> models = new ArrayList<>();
            response.get("models").forEach(node -> {
                List<Integer> reds = new ArrayList<>();
                node.get("red_balls").forEach(n -> reds.add(n.asInt()));
                models.add(new ModelPredictionItem(
                        node.get("model_name").asText(),
                        reds,
                        node.get("blue_ball").asInt(),
                        node.get("confidence").asDouble()
                ));
            });
            return new ModelCompareResponse(models);
        } catch (Exception ex) {
            log.warn("AI model compare unavailable: {}", ex.getMessage());
            return null;
        }
    }

    public AgentAnalysisResponse analyze(AnalyticsSummary summary, List<LotteryHistory> history, String question) {
        try {
            JsonNode response = post("/api/v1/agent/analyze", Map.of(
                    "question", question != null ? question : "",
                    "total_periods", summary.getTotalPeriods(),
                    "red_frequency", summary.getRedFrequency(),
                    "blue_frequency", summary.getBlueFrequency(),
                    "history", toHistoryPayload(history.subList(Math.max(0, history.size() - 10), history.size()))
            ));

            if (response == null) {
                return null;
            }

            List<String> insights = new ArrayList<>();
            response.get("insights").forEach(n -> insights.add(n.asText()));
            List<String> recommendations = new ArrayList<>();
            response.get("recommendations").forEach(n -> recommendations.add(n.asText()));

            return new AgentAnalysisResponse(
                    response.get("summary").asText(),
                    insights,
                    recommendations,
                    response.get("agent_name").asText()
            );
        } catch (Exception ex) {
            log.warn("AI agent unavailable: {}", ex.getMessage());
            return null;
        }
    }

    public AgentWorkflowResponse runWorkflow(AnalyticsSummary summary, List<LotteryHistory> history, String question) {
        try {
            JsonNode response = post("/api/v1/agent/workflow", Map.of(
                    "question", question != null ? question : "",
                    "total_periods", summary.getTotalPeriods(),
                    "red_frequency", summary.getRedFrequency(),
                    "blue_frequency", summary.getBlueFrequency(),
                    "history", toHistoryPayload(history.subList(Math.max(0, history.size() - 10), history.size()))
            ));

            if (response == null) {
                return null;
            }

            List<WorkflowStep> steps = new ArrayList<>();
            response.get("steps").forEach(node -> steps.add(new WorkflowStep(
                    node.get("agent").asText(),
                    node.get("role").asText(),
                    node.get("output").asText()
            )));

            return new AgentWorkflowResponse(
                    response.get("final_report").asText(),
                    steps,
                    response.get("workflow_name").asText()
            );
        } catch (Exception ex) {
            log.warn("AI workflow unavailable: {}", ex.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> toHistoryPayload(List<LotteryHistory> history) {
        return history.stream().map(h -> Map.<String, Object>of(
                "period", h.getPeriod(),
                "red_balls", h.getRedBalls(),
                "blue_ball", h.getBlueBall()
        )).toList();
    }

    private PredictResponse parsePredictResponse(JsonNode response, String source) {
        if (response == null) {
            return null;
        }
        List<Integer> redBalls = new ArrayList<>();
        response.get("red_balls").forEach(n -> redBalls.add(n.asInt()));
        return new PredictResponse(
                response.get("period").asText(),
                redBalls,
                response.get("blue_ball").asInt(),
                response.get("model_name").asText(),
                response.get("confidence").asDouble(),
                source
        );
    }
}
