package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lottery.context.CorrelationIdContext;
import com.lottery.dto.RegionSlaSummary;
import com.lottery.dto.SlaSummaryResponse;
import com.lottery.dto.SyncLogItem;
import com.lottery.dto.SyncStatusResponse;
import com.lottery.entity.DataSyncLog;
import com.lottery.entity.LotteryHistory;
import com.lottery.feed.FeedFetchException;
import com.lottery.feed.LotteryFeedFetcher;
import com.lottery.mapper.DataSyncLogMapper;
import com.lottery.mapper.LotteryHistoryMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataSyncService {

    private static final Logger log = LoggerFactory.getLogger(DataSyncService.class);

    private final LotteryFeedFetcher feedFetcher;
    private final LotteryHistoryMapper lotteryHistoryMapper;
    private final DataSyncLogMapper dataSyncLogMapper;
    private final SseEventService sseEventService;
    private final PlatformEventService platformEventService;
    private final ExternalApiSlaService externalApiSlaService;
    private final SlaBreachService slaBreachService;
    private final WebhookNotificationService webhookNotificationService;
    private final CacheManager cacheManager;
    private final Counter syncSuccessCounter;
    private final Counter syncFailedCounter;
    private final Timer syncTimer;

    @Value("${lottery.sync.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${lottery.sync.cron:0 */30 * * * *}")
    private String cron;

    @Value("${lottery.region:local}")
    private String region;

    @Value("${lottery.sync.backoff-base-seconds:60}")
    private int backoffBaseSeconds;

    public DataSyncService(LotteryFeedFetcher feedFetcher,
                           LotteryHistoryMapper lotteryHistoryMapper,
                           DataSyncLogMapper dataSyncLogMapper,
                           SseEventService sseEventService,
                           PlatformEventService platformEventService,
                           ExternalApiSlaService externalApiSlaService,
                           SlaBreachService slaBreachService,
                           WebhookNotificationService webhookNotificationService,
                           CacheManager cacheManager,
                           MeterRegistry meterRegistry) {
        this.feedFetcher = feedFetcher;
        this.lotteryHistoryMapper = lotteryHistoryMapper;
        this.dataSyncLogMapper = dataSyncLogMapper;
        this.sseEventService = sseEventService;
        this.platformEventService = platformEventService;
        this.externalApiSlaService = externalApiSlaService;
        this.slaBreachService = slaBreachService;
        this.webhookNotificationService = webhookNotificationService;
        this.cacheManager = cacheManager;
        this.syncSuccessCounter = meterRegistry.counter("lottery.sync.success");
        this.syncFailedCounter = meterRegistry.counter("lottery.sync.failed");
        this.syncTimer = meterRegistry.timer("lottery.sync.duration");
    }

    public SyncStatusResponse getStatus() {
        DataSyncLog last = dataSyncLogMapper.selectOne(
                new LambdaQueryWrapper<DataSyncLog>().orderByDesc(DataSyncLog::getId).last("LIMIT 1")
        );
        if (last == null) {
            return new SyncStatusResponse(null, feedFetcher.source(), "NEVER", 0, 0, "No sync yet",
                    null, null, null, null, null, null, schedulerEnabled, cron);
        }
        return toStatus(last);
    }

    public Page<SyncLogItem> listLogs(int page, int size, String status) {
        LambdaQueryWrapper<DataSyncLog> query = new LambdaQueryWrapper<DataSyncLog>()
                .orderByDesc(DataSyncLog::getId);
        if (StringUtils.hasText(status)) {
            query.eq(DataSyncLog::getStatus, status);
        }
        Page<DataSyncLog> result = dataSyncLogMapper.selectPage(new Page<>(page, size), query);
        Page<SyncLogItem> mapped = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapped.setRecords(result.getRecords().stream().map(this::toItem).toList());
        return mapped;
    }

    public List<RegionSlaSummary> regionStats(int hours) {
        return externalApiSlaService.summaryByRegion(hours).stream()
                .map(s -> {
                    long failedSyncs = dataSyncLogMapper.selectCount(
                            new LambdaQueryWrapper<DataSyncLog>()
                                    .eq(DataSyncLog::getRegion, s.getRegion())
                                    .eq(DataSyncLog::getStatus, "FAILED")
                                    .ge(DataSyncLog::getStartedAt, LocalDateTime.now().minusHours(hours))
                    );
                    return new RegionSlaSummary(s.getRegion(), s.getTotalCalls(),
                            s.getSuccessRate(), s.getP95LatencyMs(), failedSyncs);
                }).toList();
    }

    public SyncStatusResponse syncNow() {
        return syncNow(CorrelationIdContext.getOrGenerate(), null);
    }

    public SyncStatusResponse syncNow(String correlationId) {
        return syncNow(correlationId, null);
    }

    public SyncStatusResponse retryByLogId(Long logId) {
        DataSyncLog failed = dataSyncLogMapper.selectById(logId);
        if (failed == null || !"FAILED".equals(failed.getStatus())) {
            throw new IllegalArgumentException("Sync log not found or not in FAILED status");
        }
        String correlationId = StringUtils.hasText(failed.getCorrelationId())
                ? failed.getCorrelationId()
                : CorrelationIdContext.getOrGenerate();
        return syncNow(correlationId, logId);
    }

    public SyncStatusResponse syncNow(String correlationId, Long parentLogId) {
        return syncTimer.record(() -> doSync(correlationId, parentLogId));
    }

    private SyncStatusResponse doSync(String correlationId, Long parentLogId) {
        DataSyncLog job = new DataSyncLog();
        job.setSource(feedFetcher.source());
        job.setStatus("RUNNING");
        job.setFetchedCount(0);
        job.setNewCount(0);
        job.setRegion(region);
        job.setCorrelationId(correlationId);
        job.setParentLogId(parentLogId);
        job.setRetryCount(0);
        job.setStartedAt(LocalDateTime.now());
        dataSyncLogMapper.insert(job);

        try {
            List<LotteryHistory> fetched = feedFetcher.fetch();
            int newCount = 0;
            StringBuilder message = new StringBuilder();

            for (LotteryHistory item : fetched) {
                Long exists = lotteryHistoryMapper.selectCount(
                        new LambdaQueryWrapper<LotteryHistory>().eq(LotteryHistory::getPeriod, item.getPeriod())
                );
                if (exists == 0) {
                    lotteryHistoryMapper.insert(item);
                    newCount++;
                    message.append("inserted ").append(item.getPeriod()).append("; ");
                }
            }

            job.setFetchedCount(fetched.size());
            job.setNewCount(newCount);
            job.setStatus("SUCCESS");
            job.setMessage(message.isEmpty() ? "No new records" : message.toString());
            job.setFinishedAt(LocalDateTime.now());
            job.setNextRetryAt(null);
            dataSyncLogMapper.updateById(job);

            evictCaches();
            syncSuccessCounter.increment();
            log.info("Data sync completed: fetched={}, new={}", fetched.size(), newCount);
            publishSyncEvent(job, correlationId);
            SlaSummaryResponse summary = externalApiSlaService.summary(1);
            slaBreachService.evaluateSummary(summary, correlationId);
        } catch (FeedFetchException ex) {
            job.setStatus("FAILED");
            job.setMessage(ex.getMessage());
            job.setErrorType(ex.getErrorType());
            job.setHttpStatus(ex.getHttpStatus() > 0 ? ex.getHttpStatus() : null);
            job.setFinishedAt(LocalDateTime.now());
            job.setRetryCount(0);
            job.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffBaseSeconds));
            dataSyncLogMapper.updateById(job);
            syncFailedCounter.increment();
            log.error("Data sync failed: {}", ex.getMessage());
            slaBreachService.recordCallFailure(ex.getErrorType(), correlationId);
            publishSyncFailedEvent(job, ex, correlationId);
        }

        return getStatus();
    }

    private void evictCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            if (cacheManager.getCache(name) != null) {
                cacheManager.getCache(name).clear();
            }
        });
    }

    private void publishSyncEvent(DataSyncLog job, String correlationId) {
        Map<String, Object> payload = basePayload(job, correlationId);
        payload.put("type", "sync.completed");
        sseEventService.broadcast("sync", payload);
        platformEventService.publishSyncEvent(payload, correlationId);
    }

    private void publishSyncFailedEvent(DataSyncLog job, FeedFetchException ex, String correlationId) {
        Map<String, Object> payload = basePayload(job, correlationId);
        payload.put("type", "sync.failed");
        payload.put("errorType", ex.getErrorType());
        payload.put("httpStatus", ex.getHttpStatus());
        sseEventService.broadcast("sync-failed", payload);
        platformEventService.publishSyncFailedEvent(payload, correlationId);
        webhookNotificationService.notifySyncFailed(payload, correlationId, job.getId());
    }

    private Map<String, Object> basePayload(DataSyncLog job, String correlationId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("source", job.getSource());
        payload.put("newCount", job.getNewCount());
        payload.put("fetchedCount", job.getFetchedCount());
        payload.put("status", job.getStatus());
        payload.put("message", job.getMessage());
        payload.put("correlationId", correlationId);
        payload.put("region", region);
        if (job.getParentLogId() != null) {
            payload.put("parentLogId", job.getParentLogId());
        }
        return payload;
    }

    private SyncStatusResponse toStatus(DataSyncLog last) {
        return new SyncStatusResponse(
                last.getId(), last.getSource(), last.getStatus(),
                last.getFetchedCount(), last.getNewCount(), last.getMessage(),
                last.getStartedAt(), last.getFinishedAt(),
                last.getRegion(), last.getCorrelationId(), last.getErrorType(), last.getHttpStatus(),
                schedulerEnabled, cron
        );
    }

    private SyncLogItem toItem(DataSyncLog job) {
        return new SyncLogItem(
                job.getId(), job.getSource(), job.getStatus(),
                job.getFetchedCount(), job.getNewCount(), job.getMessage(),
                job.getRegion(), job.getCorrelationId(), job.getErrorType(),
                job.getHttpStatus(), job.getStartedAt(), job.getFinishedAt(),
                job.getParentLogId(), job.getRetryCount(), job.getNextRetryAt()
        );
    }
}
