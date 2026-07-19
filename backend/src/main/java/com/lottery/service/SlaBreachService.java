package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lottery.dto.SlaBreachItem;
import com.lottery.dto.SlaSummaryResponse;
import com.lottery.entity.SlaBreachLog;
import com.lottery.mapper.SlaBreachLogMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SlaBreachService {

    private final SlaBreachLogMapper breachLogMapper;
    private final PlatformEventService platformEventService;
    private final SseEventService sseEventService;
    private final WebhookNotificationService webhookNotificationService;
    private final Counter breachCounter;

    @Value("${lottery.sla.alert-enabled:true}")
    private boolean alertEnabled;

    @Value("${lottery.sla.min-success-rate:95}")
    private double minSuccessRate;

    @Value("${lottery.sla.max-p95-ms:800}")
    private double maxP95Ms;

    @Value("${lottery.region:local}")
    private String region;

    public SlaBreachService(SlaBreachLogMapper breachLogMapper,
                            PlatformEventService platformEventService,
                            SseEventService sseEventService,
                            WebhookNotificationService webhookNotificationService,
                            MeterRegistry meterRegistry) {
        this.breachLogMapper = breachLogMapper;
        this.platformEventService = platformEventService;
        this.sseEventService = sseEventService;
        this.webhookNotificationService = webhookNotificationService;
        this.breachCounter = meterRegistry.counter("lottery.sla.breaches");
    }

    public void evaluateSummary(SlaSummaryResponse summary, String correlationId) {
        if (!alertEnabled || summary.getTotalCalls() == 0) {
            return;
        }
        if (summary.getSuccessRate() < minSuccessRate) {
            record("success_rate", minSuccessRate, summary.getSuccessRate(), correlationId,
                    "Success rate below SLO: " + summary.getSuccessRate() + "%");
        }
        if (summary.getP95LatencyMs() > maxP95Ms) {
            record("p95_latency", maxP95Ms, summary.getP95LatencyMs(), correlationId,
                    "P95 latency above SLO: " + summary.getP95LatencyMs() + "ms");
        }
    }

    public void recordCallFailure(String errorType, String correlationId) {
        if (!alertEnabled) {
            return;
        }
        record("call_failure", 0, 1, correlationId, "External API call failed: " + errorType);
    }

    private void record(String metric, double threshold, double actual, String correlationId, String message) {
        SlaBreachLog entry = new SlaBreachLog();
        entry.setMetric(metric);
        entry.setThresholdValue(threshold);
        entry.setActualValue(actual);
        entry.setSeverity("WARN");
        entry.setRegion(region);
        entry.setCorrelationId(correlationId);
        entry.setMessage(message);
        breachLogMapper.insert(entry);
        breachCounter.increment();

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "sla.breach");
        payload.put("breachId", entry.getId());
        payload.put("metric", metric);
        payload.put("thresholdValue", threshold);
        payload.put("actualValue", actual);
        payload.put("severity", entry.getSeverity());
        payload.put("region", region);
        payload.put("correlationId", correlationId);
        payload.put("message", message);

        sseEventService.broadcast("sla-breach", payload);
        platformEventService.publishBreachEvent(payload, correlationId);
        webhookNotificationService.notifyBreach(payload, correlationId, entry.getId());
    }

    public Page<SlaBreachItem> list(int page, int size) {
        Page<SlaBreachLog> result = breachLogMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<SlaBreachLog>().orderByDesc(SlaBreachLog::getId)
        );
        Page<SlaBreachItem> mapped = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapped.setRecords(result.getRecords().stream()
                .map(b -> new SlaBreachItem(
                        b.getId(), b.getMetric(),
                        b.getThresholdValue() != null ? b.getThresholdValue() : 0,
                        b.getActualValue() != null ? b.getActualValue() : 0,
                        b.getSeverity(), b.getRegion(), b.getCorrelationId(),
                        b.getMessage(), b.getCreatedAt()))
                .toList());
        return mapped;
    }
}
