package com.lottery.config;

import com.lottery.service.DataSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "lottery.sync.enabled", havingValue = "true", matchIfMissing = true)
public class DataSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataSyncScheduler.class);

    private final DataSyncService dataSyncService;

    public DataSyncScheduler(DataSyncService dataSyncService) {
        this.dataSyncService = dataSyncService;
    }

    @Scheduled(cron = "${lottery.sync.cron:0 */30 * * * *}")
    public void scheduledSync() {
        log.info("Running scheduled data sync");
        dataSyncService.syncNow();
    }
}
