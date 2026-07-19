package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.dto.PlatformEventItem;
import com.lottery.dto.SlaBreachItem;
import com.lottery.dto.SlaLogItem;
import com.lottery.dto.SyncLogItem;
import com.lottery.dto.TraceResponse;
import com.lottery.entity.DataSyncLog;
import com.lottery.entity.ExternalApiSlaLog;
import com.lottery.entity.PlatformEvent;
import com.lottery.entity.SlaBreachLog;
import com.lottery.mapper.DataSyncLogMapper;
import com.lottery.mapper.ExternalApiSlaLogMapper;
import com.lottery.mapper.PlatformEventMapper;
import com.lottery.mapper.SlaBreachLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TraceService {

    private final PlatformEventMapper platformEventMapper;
    private final DataSyncLogMapper dataSyncLogMapper;
    private final ExternalApiSlaLogMapper slaLogMapper;
    private final SlaBreachLogMapper breachLogMapper;

    public TraceService(PlatformEventMapper platformEventMapper,
                        DataSyncLogMapper dataSyncLogMapper,
                        ExternalApiSlaLogMapper slaLogMapper,
                        SlaBreachLogMapper breachLogMapper) {
        this.platformEventMapper = platformEventMapper;
        this.dataSyncLogMapper = dataSyncLogMapper;
        this.slaLogMapper = slaLogMapper;
        this.breachLogMapper = breachLogMapper;
    }

    public TraceResponse trace(String correlationId) {
        List<PlatformEventItem> events = platformEventMapper.selectList(
                new LambdaQueryWrapper<PlatformEvent>().eq(PlatformEvent::getCorrelationId, correlationId)
                        .orderByDesc(PlatformEvent::getId)
        ).stream().map(e -> new PlatformEventItem(
                e.getId(), e.getEventType(), e.getTopic(), e.getPayload(),
                e.getSchemaVersion(), e.getRegion(), e.getCorrelationId(),
                e.getPublished() != null && e.getPublished() == 1, e.getCreatedAt()
        )).toList();

        List<SyncLogItem> syncLogs = dataSyncLogMapper.selectList(
                new LambdaQueryWrapper<DataSyncLog>().eq(DataSyncLog::getCorrelationId, correlationId)
                        .orderByDesc(DataSyncLog::getId)
        ).stream().map(this::toSyncItem).toList();

        List<SlaLogItem> slaLogs = slaLogMapper.selectList(
                new LambdaQueryWrapper<ExternalApiSlaLog>().eq(ExternalApiSlaLog::getCorrelationId, correlationId)
                        .orderByDesc(ExternalApiSlaLog::getId)
        ).stream().map(l -> new SlaLogItem(
                l.getId(), l.getProvider(), l.getEndpoint(),
                l.getLatencyMs() != null ? l.getLatencyMs() : 0,
                l.getHttpStatus(),
                l.getSuccess() != null && l.getSuccess() == 1,
                l.getErrorType(), l.getRegion(), l.getCreatedAt()
        )).toList();

        List<SlaBreachItem> breaches = breachLogMapper.selectList(
                new LambdaQueryWrapper<SlaBreachLog>().eq(SlaBreachLog::getCorrelationId, correlationId)
                        .orderByDesc(SlaBreachLog::getId)
        ).stream().map(b -> new SlaBreachItem(
                b.getId(), b.getMetric(),
                b.getThresholdValue() != null ? b.getThresholdValue() : 0,
                b.getActualValue() != null ? b.getActualValue() : 0,
                b.getSeverity(), b.getRegion(), b.getCorrelationId(),
                b.getMessage(), b.getCreatedAt()
        )).toList();

        return new TraceResponse(correlationId, events, syncLogs, slaLogs, breaches);
    }

    private SyncLogItem toSyncItem(DataSyncLog job) {
        return new SyncLogItem(
                job.getId(), job.getSource(), job.getStatus(),
                job.getFetchedCount(), job.getNewCount(), job.getMessage(),
                job.getRegion(), job.getCorrelationId(), job.getErrorType(),
                job.getHttpStatus(), job.getStartedAt(), job.getFinishedAt(),
                job.getParentLogId(), job.getRetryCount(), job.getNextRetryAt()
        );
    }
}
