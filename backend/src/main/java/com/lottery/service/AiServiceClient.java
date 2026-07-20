package com.lottery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.context.CorrelationIdContext;
import com.lottery.filter.CorrelationIdFilter;
import com.lottery.dto.AgentAnalysisResponse;
import com.lottery.dto.AgentWorkflowResponse;
import com.lottery.dto.AnalyticsSummary;
import com.lottery.dto.Fc3dAnalyzeResponse;
import com.lottery.dto.Fc3dCandidate;
import com.lottery.dto.Fc3dCandidateAnalysisItem;
import com.lottery.dto.Fc3dFeatureSummary;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dOddEvenResponse;
import com.lottery.dto.Fc3dRecommendation;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.dto.ModelCompareResponse;
import com.lottery.dto.ModelPredictionItem;
import com.lottery.domain.LotteryType;
import com.lottery.dto.Fc3dPredictResponse;
import com.lottery.dto.PredictResponse;
import com.lottery.dto.WorkflowStep;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.entity.LotteryHistory;
import com.lottery.util.Fc3dBallUtils;
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

    public Fc3dPredictResponse predictFc3d(List<Fc3dDrawEntity> history, String issue) {
        try {
            JsonNode response = post("/api/v1/fc3d/predict",
                    Map.of("issue", issue != null ? issue : "next", "history", toFc3dHistoryPayload(history)));
            if (response == null) {
                return null;
            }
            int d1 = response.get("digit1").asInt();
            int d2 = response.get("digit2").asInt();
            int d3 = response.get("digit3").asInt();
            Fc3dPredictResponse result = new Fc3dPredictResponse();
            result.setLotteryType(LotteryType.FC3D.name());
            result.setIssue(response.has("issue") ? response.get("issue").asText() : (issue != null ? issue : "next"));
            result.setDigit1(d1);
            result.setDigit2(d2);
            result.setDigit3(d3);
            result.setSumValue(response.has("sum_value") ? response.get("sum_value").asInt() : Fc3dBallUtils.sum(d1, d2, d3));
            result.setSpanValue(response.has("span_value") ? response.get("span_value").asInt() : Fc3dBallUtils.span(d1, d2, d3));
            result.setOddEvenPattern(response.has("odd_even_pattern") ? response.get("odd_even_pattern").asText()
                    : Fc3dBallUtils.oddEvenPattern(d1, d2, d3));
            result.setModelName(response.has("model_name") ? response.get("model_name").asText() : "fc3d-ai");
            result.setConfidence(response.has("confidence") ? response.get("confidence").asDouble() : 0.6);
            result.setSource("ai");
            return result;
        } catch (Exception ex) {
            log.warn("FC3D AI service unavailable: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Asks the AI service to EXPLAIN existing statistical candidates.
     * This never generates new numbers — it only interprets analytics + candidates.
     */
    public Fc3dAnalyzeResponse analyzeFc3d(Fc3dFrequencyResponse frequency,
                                           Fc3dMissingResponse missing,
                                           Fc3dSumAnalysisResponse sumAnalysis,
                                           Fc3dOddEvenResponse oddEven,
                                           List<Fc3dCandidate> candidates,
                                           String best,
                                           List<Fc3dDrawEntity> recentHistory,
                                           String issue,
                                           String question) {
        try {
            Map<String, Object> analytics = Map.of(
                    "frequency", Map.of(
                            "hundreds", frequency.getHundreds(),
                            "tens", frequency.getTens(),
                            "units", frequency.getUnits()
                    ),
                    "missing", missing.getItems(),
                    "sum", Map.of(
                            "average", sumAnalysis.getAverage(),
                            "distribution", sumAnalysis.getDistribution()
                    ),
                    "odd_even", Map.of(
                            "pattern", oddEven.getPattern(),
                            "odd_count", oddEven.getOddCount(),
                            "even_count", oddEven.getEvenCount()
                    )
            );

            List<Map<String, Object>> candidatePayload = candidates.stream()
                    .map(c -> Map.<String, Object>of(
                            "number", c.getNumber(),
                            "score", c.getScore(),
                            "reasons", c.getReasons() != null ? c.getReasons() : List.of()
                    )).toList();

            JsonNode response = post("/api/v1/fc3d/analyze", Map.of(
                    "issue", issue != null ? issue : "next",
                    "question", question != null ? question : "",
                    "analytics", analytics,
                    "candidates", candidatePayload,
                    "best", best != null ? best : "",
                    "history", toFc3dHistoryPayload(recentHistory)
            ));

            if (response == null) {
                return null;
            }
            return parseFc3dAnalyzeResponse(response);
        } catch (Exception ex) {
            log.warn("FC3D AI analyze unavailable: {}", ex.getMessage());
            return null;
        }
    }

    private Fc3dAnalyzeResponse parseFc3dAnalyzeResponse(JsonNode response) {
        Fc3dFeatureSummary features = new Fc3dFeatureSummary();
        JsonNode featuresNode = response.get("features");
        if (featuresNode != null) {
            Map<String, List<Integer>> hotDigits = new java.util.LinkedHashMap<>();
            JsonNode hotDigitsNode = featuresNode.get("hot_digits");
            if (hotDigitsNode != null) {
                hotDigitsNode.fieldNames().forEachRemaining(pos -> {
                    List<Integer> digits = new ArrayList<>();
                    hotDigitsNode.get(pos).forEach(n -> digits.add(n.asInt()));
                    hotDigits.put(pos, digits);
                });
            }
            features.setHotDigits(hotDigits);
            features.setSumAverage(featuresNode.has("sum_average") && !featuresNode.get("sum_average").isNull()
                    ? featuresNode.get("sum_average").asDouble() : null);
            features.setDominantOddEven(featuresNode.has("dominant_odd_even") && !featuresNode.get("dominant_odd_even").isNull()
                    ? featuresNode.get("dominant_odd_even").asText() : null);
            List<String> notes = new ArrayList<>();
            if (featuresNode.has("notes")) {
                featuresNode.get("notes").forEach(n -> notes.add(n.asText()));
            }
            features.setNotes(notes);
        }

        List<Fc3dCandidateAnalysisItem> candidateAnalysis = new ArrayList<>();
        if (response.has("candidate_analysis")) {
            response.get("candidate_analysis").forEach(node -> {
                Fc3dCandidateAnalysisItem item = new Fc3dCandidateAnalysisItem();
                item.setNumber(node.get("number").asText());
                item.setScore(node.get("score").asInt());
                List<String> aligned = new ArrayList<>();
                node.get("aligned_signals").forEach(n -> aligned.add(n.asText()));
                item.setAlignedSignals(aligned);
                List<String> risks = new ArrayList<>();
                node.get("risk_flags").forEach(n -> risks.add(n.asText()));
                item.setRiskFlags(risks);
                item.setComment(node.get("comment").asText());
                candidateAnalysis.add(item);
            });
        }

        Fc3dRecommendation recommendation = new Fc3dRecommendation();
        JsonNode recNode = response.get("recommendation");
        if (recNode != null) {
            recommendation.setPreferred(recNode.has("preferred") && !recNode.get("preferred").isNull()
                    ? recNode.get("preferred").asText() : null);
            List<String> rationale = new ArrayList<>();
            if (recNode.has("rationale")) {
                recNode.get("rationale").forEach(n -> rationale.add(n.asText()));
            }
            recommendation.setRationale(rationale);
            recommendation.setDisclaimer(recNode.has("disclaimer") ? recNode.get("disclaimer").asText() : "");
        }

        Fc3dAnalyzeResponse result = new Fc3dAnalyzeResponse();
        result.setLotteryType(LotteryType.FC3D.name());
        result.setFeatures(features);
        result.setCandidateAnalysis(candidateAnalysis);
        result.setRecommendation(recommendation);
        result.setConfidence(response.has("confidence") ? response.get("confidence").asDouble() : null);
        result.setModelName(response.has("model_name") ? response.get("model_name").asText() : "fc3d-analyze-ai");
        return result;
    }

    private List<Map<String, Object>> toFc3dHistoryPayload(List<Fc3dDrawEntity> history) {
        return history.stream().map(h -> Map.<String, Object>of(
                "issue", h.getIssue(),
                "digit1", h.getDigit1(),
                "digit2", h.getDigit2(),
                "digit3", h.getDigit3(),
                "sum_value", h.getSumValue(),
                "span_value", h.getSpanValue(),
                "odd_even_pattern", h.getOddEvenPattern() != null ? h.getOddEvenPattern() : ""
        )).toList();
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
