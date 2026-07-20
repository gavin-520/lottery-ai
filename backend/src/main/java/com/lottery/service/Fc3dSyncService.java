package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.context.CorrelationIdContext;
import com.lottery.dto.Fc3dSyncStatusResponse;
import com.lottery.entity.DataSyncLog;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.feed.Fc3dHistoryFeedClient;
import com.lottery.feed.FeedFetchException;
import com.lottery.mapper.DataSyncLogMapper;
import com.lottery.mapper.Fc3dDrawMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class Fc3dSyncService {

    private static final Logger log = LoggerFactory.getLogger(Fc3dSyncService.class);

    private final Fc3dHistoryFeedClient feedClient;
    private final Fc3dDrawMapper fc3dDrawMapper;
    private final DataSyncLogMapper dataSyncLogMapper;
    private final CacheManager cacheManager;
    private final SseEventService sseEventService;

    @Value("${lottery.fc3d.sync.enabled:true}")
    private boolean enabled;

    @Value("${lottery.fc3d.sync.cron:0 40 21 * * *}")
    private String cron;

    @Value("${lottery.fc3d.sync.zone:Asia/Shanghai}")
    private String zone;

    @Value("${lottery.fc3d.sync.recent-lines:400}")
    private int recentLines;

    @Value("${lottery.region:local}")
    private String region;

    public Fc3dSyncService(Fc3dHistoryFeedClient feedClient,
                           Fc3dDrawMapper fc3dDrawMapper,
                           DataSyncLogMapper dataSyncLogMapper,
                           CacheManager cacheManager,
                           SseEventService sseEventService) {
        this.feedClient = feedClient;
        this.fc3dDrawMapper = fc3dDrawMapper;
        this.dataSyncLogMapper = dataSyncLogMapper;
        this.cacheManager = cacheManager;
        this.sseEventService = sseEventService;
    }

    public Fc3dSyncStatusResponse getStatus() {
        DataSyncLog last = dataSyncLogMapper.selectOne(
                new LambdaQueryWrapper<DataSyncLog>()
                        .eq(DataSyncLog::getSource, feedClient.source())
                        .orderByDesc(DataSyncLog::getId)
                        .last("LIMIT 1")
        );
        Fc3dDrawEntity latest = fc3dDrawMapper.selectOne(
                new LambdaQueryWrapper<Fc3dDrawEntity>().orderByDesc(Fc3dDrawEntity::getIssue).last("LIMIT 1")
        );
        long total = fc3dDrawMapper.selectCount(null);
        if (last == null) {
            return new Fc3dSyncStatusResponse(
                    feedClient.source(), "NEVER", 0, 0, "No FC3D sync yet",
                    latest != null ? latest.getIssue() : null,
                    null, null, enabled, cron, zone, total
            );
        }
        return new Fc3dSyncStatusResponse(
                last.getSource(),
                last.getStatus(),
                last.getFetchedCount() != null ? last.getFetchedCount() : 0,
                last.getNewCount() != null ? last.getNewCount() : 0,
                last.getMessage(),
                latest != null ? latest.getIssue() : null,
                last.getStartedAt(),
                last.getFinishedAt(),
                enabled,
                cron,
                zone,
                total
        );
    }

    public Fc3dSyncStatusResponse syncNow() {
        return syncNow(false);
    }

    /**
     * @param fullHistory true = scan entire feed; false = only newest {@code recentLines}
     */
    public Fc3dSyncStatusResponse syncNow(boolean fullHistory) {
        String correlationId = CorrelationIdContext.getOrGenerate();
        DataSyncLog job = new DataSyncLog();
        job.setSource(feedClient.source());
        job.setStatus("RUNNING");
        job.setFetchedCount(0);
        job.setNewCount(0);
        job.setRegion(region);
        job.setCorrelationId(correlationId);
        job.setRetryCount(0);
        job.setStartedAt(LocalDateTime.now());
        dataSyncLogMapper.insert(job);

        try {
            List<Fc3dDrawEntity> fetched = fullHistory
                    ? feedClient.fetchAll()
                    : feedClient.fetch(recentLines);

            Set<String> existing = loadExistingIssues(fetched);
            int newCount = 0;
            String latestInserted = null;
            for (Fc3dDrawEntity item : fetched) {
                if (existing.contains(item.getIssue())) {
                    continue;
                }
                fc3dDrawMapper.insert(item);
                existing.add(item.getIssue());
                newCount++;
                if (latestInserted == null || item.getIssue().compareTo(latestInserted) > 0) {
                    latestInserted = item.getIssue();
                }
            }

            job.setFetchedCount(fetched.size());
            job.setNewCount(newCount);
            job.setStatus("SUCCESS");
            job.setMessage(newCount == 0
                    ? "No new FC3D draws"
                    : "Inserted " + newCount + " FC3D draws"
                    + (latestInserted != null ? ", latest=" + latestInserted : ""));
            job.setFinishedAt(LocalDateTime.now());
            dataSyncLogMapper.updateById(job);

            if (newCount > 0) {
                evictFc3dCaches();
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "fc3d.sync.completed");
            payload.put("source", feedClient.source());
            payload.put("fetchedCount", fetched.size());
            payload.put("newCount", newCount);
            payload.put("latestIssue", latestInserted);
            payload.put("correlationId", correlationId);
            sseEventService.broadcast("fc3d-sync", payload);

            log.info("FC3D sync completed: fetched={}, new={}", fetched.size(), newCount);
        } catch (FeedFetchException ex) {
            job.setStatus("FAILED");
            job.setMessage(ex.getMessage());
            job.setErrorType(ex.getErrorType());
            job.setHttpStatus(ex.getHttpStatus() > 0 ? ex.getHttpStatus() : null);
            job.setFinishedAt(LocalDateTime.now());
            dataSyncLogMapper.updateById(job);
            log.error("FC3D sync failed: {}", ex.getMessage());

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "fc3d.sync.failed");
            payload.put("message", ex.getMessage());
            payload.put("correlationId", correlationId);
            sseEventService.broadcast("fc3d-sync-failed", payload);
        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setMessage(ex.getMessage());
            job.setErrorType("INTERNAL");
            job.setFinishedAt(LocalDateTime.now());
            dataSyncLogMapper.updateById(job);
            log.error("FC3D sync unexpected error", ex);
        }

        return getStatus();
    }

    private Set<String> loadExistingIssues(List<Fc3dDrawEntity> fetched) {
        Set<String> existing = new HashSet<>();
        if (fetched.isEmpty()) {
            return existing;
        }
        String min = fetched.stream().map(Fc3dDrawEntity::getIssue).min(String::compareTo).orElse(null);
        String max = fetched.stream().map(Fc3dDrawEntity::getIssue).max(String::compareTo).orElse(null);
        if (min == null) {
            return existing;
        }
        List<Fc3dDrawEntity> inDb = fc3dDrawMapper.selectList(
                new LambdaQueryWrapper<Fc3dDrawEntity>()
                        .select(Fc3dDrawEntity::getIssue)
                        .between(Fc3dDrawEntity::getIssue, min, max)
        );
        for (Fc3dDrawEntity row : inDb) {
            existing.add(row.getIssue());
        }
        return existing;
    }

    private void evictFc3dCaches() {
        for (String name : List.of("fc3dAnalytics", "fc3dAnalyticsFrequency", "historyPage", "historyAll", "analytics")) {
            if (cacheManager.getCache(name) != null) {
                cacheManager.getCache(name).clear();
            }
        }
    }
}
