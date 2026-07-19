package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lottery.dto.SlaLogItem;
import com.lottery.dto.SlaSummaryResponse;
import com.lottery.entity.ExternalApiSlaLog;
import com.lottery.mapper.ExternalApiSlaLogMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExternalApiSlaService {

    private final ExternalApiSlaLogMapper slaLogMapper;

    @Value("${lottery.region:local}")
    private String region;

    public ExternalApiSlaService(ExternalApiSlaLogMapper slaLogMapper) {
        this.slaLogMapper = slaLogMapper;
    }

    public void logCall(String provider, String endpoint, int latencyMs, Integer httpStatus,
                        boolean success, String errorType) {
        logCall(provider, endpoint, latencyMs, httpStatus, success, errorType, null);
    }

    public void logCall(String provider, String endpoint, int latencyMs, Integer httpStatus,
                        boolean success, String errorType, String correlationId) {
        ExternalApiSlaLog entry = new ExternalApiSlaLog();
        entry.setProvider(provider);
        entry.setEndpoint(endpoint);
        entry.setLatencyMs(latencyMs);
        entry.setHttpStatus(httpStatus);
        entry.setSuccess(success ? 1 : 0);
        entry.setErrorType(errorType);
        entry.setRegion(region);
        entry.setCorrelationId(correlationId);
        slaLogMapper.insert(entry);
    }

    public SlaSummaryResponse summary(int hours) {
        return summary(hours, region);
    }

    public SlaSummaryResponse summary(int hours, String targetRegion) {
        var since = java.time.LocalDateTime.now().minusHours(hours);
        LambdaQueryWrapper<ExternalApiSlaLog> query = new LambdaQueryWrapper<ExternalApiSlaLog>()
                .ge(ExternalApiSlaLog::getCreatedAt, since)
                .orderByDesc(ExternalApiSlaLog::getId);
        if (StringUtils.hasText(targetRegion)) {
            query.eq(ExternalApiSlaLog::getRegion, targetRegion);
        }
        List<ExternalApiSlaLog> logs = slaLogMapper.selectList(query);
        return buildSummary(logs, StringUtils.hasText(targetRegion) ? targetRegion : region);
    }

    public List<SlaSummaryResponse> summaryByRegion(int hours) {
        var since = java.time.LocalDateTime.now().minusHours(hours);
        List<ExternalApiSlaLog> logs = slaLogMapper.selectList(
                new LambdaQueryWrapper<ExternalApiSlaLog>()
                        .ge(ExternalApiSlaLog::getCreatedAt, since)
        );
        Map<String, List<ExternalApiSlaLog>> grouped = logs.stream()
                .collect(Collectors.groupingBy(l -> l.getRegion() != null ? l.getRegion() : "local"));
        List<SlaSummaryResponse> results = new ArrayList<>();
        grouped.forEach((r, items) -> results.add(buildSummary(items, r)));
        return results;
    }

    public Page<SlaLogItem> list(int page, int size) {
        return list(page, size, null);
    }

    public Page<SlaLogItem> list(int page, int size, String targetRegion) {
        LambdaQueryWrapper<ExternalApiSlaLog> query = new LambdaQueryWrapper<ExternalApiSlaLog>()
                .orderByDesc(ExternalApiSlaLog::getId);
        if (StringUtils.hasText(targetRegion)) {
            query.eq(ExternalApiSlaLog::getRegion, targetRegion);
        }
        Page<ExternalApiSlaLog> result = slaLogMapper.selectPage(new Page<>(page, size), query);
        Page<SlaLogItem> mapped = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapped.setRecords(result.getRecords().stream()
                .map(l -> new SlaLogItem(
                        l.getId(), l.getProvider(), l.getEndpoint(),
                        l.getLatencyMs() != null ? l.getLatencyMs() : 0,
                        l.getHttpStatus(),
                        l.getSuccess() != null && l.getSuccess() == 1,
                        l.getErrorType(), l.getRegion(), l.getCreatedAt()))
                .toList());
        return mapped;
    }

    private SlaSummaryResponse buildSummary(List<ExternalApiSlaLog> logs, String targetRegion) {
        long total = logs.size();
        long success = logs.stream().filter(l -> l.getSuccess() != null && l.getSuccess() == 1).count();
        long failed = total - success;
        double rate = total == 0 ? 100 : (success * 100.0 / total);
        double avg = logs.stream().mapToInt(l -> l.getLatencyMs() != null ? l.getLatencyMs() : 0).average().orElse(0);
        double p95 = percentile(logs.stream()
                .map(l -> l.getLatencyMs() != null ? l.getLatencyMs() : 0)
                .sorted().toList(), 95);
        return new SlaSummaryResponse(total, success, failed, rate, avg, p95, targetRegion);
    }

    private double percentile(List<Integer> sorted, int pct) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}
