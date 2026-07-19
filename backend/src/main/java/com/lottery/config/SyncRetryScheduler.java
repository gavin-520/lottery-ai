package com.lottery.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.entity.DataSyncLog;
import com.lottery.mapper.DataSyncLogMapper;
import com.lottery.service.DataSyncService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "lottery.sync.auto-retry-enabled", havingValue = "true", matchIfMissing = true)
public class SyncRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncRetryScheduler.class);

    private final DataSyncLogMapper dataSyncLogMapper;
    private final DataSyncService dataSyncService;
    private final Counter autoRetryCounter;

    @Value("${lottery.sync.max-auto-retries:3}")
    private int maxAutoRetries;

    @Value("${lottery.sync.backoff-base-seconds:60}")
    private int backoffBaseSeconds;

    public SyncRetryScheduler(DataSyncLogMapper dataSyncLogMapper,
                              DataSyncService dataSyncService,
                              MeterRegistry meterRegistry) {
        this.dataSyncLogMapper = dataSyncLogMapper;
        this.dataSyncService = dataSyncService;
        this.autoRetryCounter = meterRegistry.counter("lottery.sync.auto_retries");
    }

    @Scheduled(cron = "${lottery.sync.retry-cron:0 */5 * * * *}")
    public void autoRetryFailedSyncs() {
        List<DataSyncLog> eligible = dataSyncLogMapper.selectList(
                new LambdaQueryWrapper<DataSyncLog>()
                        .eq(DataSyncLog::getStatus, "FAILED")
                        .le(DataSyncLog::getNextRetryAt, LocalDateTime.now())
                        .lt(DataSyncLog::getRetryCount, maxAutoRetries)
                        .orderByAsc(DataSyncLog::getId)
                        .last("LIMIT 5")
        );
        for (DataSyncLog failed : eligible) {
            try {
                log.info("Auto-retrying failed sync log id={} attempt={}", failed.getId(), failed.getRetryCount() + 1);
                failed.setRetryCount(failed.getRetryCount() != null ? failed.getRetryCount() + 1 : 1);
                failed.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffBaseSeconds * failed.getRetryCount()));
                dataSyncLogMapper.updateById(failed);
                dataSyncService.retryByLogId(failed.getId());
                autoRetryCounter.increment();
            } catch (Exception ex) {
                log.warn("Auto-retry failed for log {}: {}", failed.getId(), ex.getMessage());
            }
        }
    }
}
