package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.dto.OpsOverviewResponse;
import com.lottery.entity.DataSyncLog;
import com.lottery.entity.SlaBreachLog;
import com.lottery.mapper.DataSyncLogMapper;
import com.lottery.mapper.SlaBreachLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OpsService {

    private final DataSyncService dataSyncService;
    private final SlaBreachLogMapper breachLogMapper;
    private final DataSyncLogMapper dataSyncLogMapper;

    public OpsService(DataSyncService dataSyncService,
                      SlaBreachLogMapper breachLogMapper,
                      DataSyncLogMapper dataSyncLogMapper) {
        this.dataSyncService = dataSyncService;
        this.breachLogMapper = breachLogMapper;
        this.dataSyncLogMapper = dataSyncLogMapper;
    }

    public OpsOverviewResponse overview(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        long breaches = breachLogMapper.selectCount(
                new LambdaQueryWrapper<SlaBreachLog>().ge(SlaBreachLog::getCreatedAt, since)
        );
        long failedSyncs = dataSyncLogMapper.selectCount(
                new LambdaQueryWrapper<DataSyncLog>()
                        .eq(DataSyncLog::getStatus, "FAILED")
                        .ge(DataSyncLog::getStartedAt, since)
        );
        return new OpsOverviewResponse(
                dataSyncService.regionStats(hours),
                breaches,
                failedSyncs,
                true
        );
    }
}
