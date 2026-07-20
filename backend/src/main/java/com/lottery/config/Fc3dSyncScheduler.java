package com.lottery.config;

import com.lottery.service.Fc3dSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily FC3D draw sync.
 * Default: 21:40 Asia/Shanghai (after official draw ~21:15),
 * plus morning catch-up at 08:10.
 */
@Component
@ConditionalOnProperty(name = "lottery.fc3d.sync.enabled", havingValue = "true", matchIfMissing = true)
public class Fc3dSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(Fc3dSyncScheduler.class);

    private final Fc3dSyncService fc3dSyncService;

    public Fc3dSyncScheduler(Fc3dSyncService fc3dSyncService) {
        this.fc3dSyncService = fc3dSyncService;
    }

    @Scheduled(cron = "${lottery.fc3d.sync.cron:0 40 21 * * *}", zone = "${lottery.fc3d.sync.zone:Asia/Shanghai}")
    public void syncAfterDraw() {
        log.info("Running scheduled FC3D sync (after-draw)");
        fc3dSyncService.syncNow(false);
    }

    @Scheduled(cron = "${lottery.fc3d.sync.catchup-cron:0 10 8 * * *}", zone = "${lottery.fc3d.sync.zone:Asia/Shanghai}")
    public void syncMorningCatchup() {
        log.info("Running scheduled FC3D sync (morning catch-up)");
        fc3dSyncService.syncNow(false);
    }
}
