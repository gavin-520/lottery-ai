package com.lottery.service;

import com.lottery.dto.SlaSummaryResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.lottery.mapper.SlaBreachLogMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SlaBreachServiceTest {

    @Mock
    private SlaBreachLogMapper breachLogMapper;

    @Mock
    private PlatformEventService platformEventService;

    @Mock
    private SseEventService sseEventService;

    @Mock
    private WebhookNotificationService webhookNotificationService;

    private SlaBreachService slaBreachService;

    @BeforeEach
    void setUp() {
        slaBreachService = new SlaBreachService(
                breachLogMapper,
                platformEventService,
                sseEventService,
                webhookNotificationService,
                new SimpleMeterRegistry());
        ReflectionTestUtils.setField(slaBreachService, "alertEnabled", true);
        ReflectionTestUtils.setField(slaBreachService, "minSuccessRate", 95.0);
        ReflectionTestUtils.setField(slaBreachService, "maxP95Ms", 800.0);
        ReflectionTestUtils.setField(slaBreachService, "region", "local");
    }

    @Test
    void recordsBreachWhenSuccessRateBelowThreshold() {
        slaBreachService.evaluateSummary(
                new SlaSummaryResponse(10, 8, 2, 80.0, 100, 200, "local"),
                "test-correlation"
        );
        verify(breachLogMapper).insert(any());
        verify(platformEventService).publishBreachEvent(any(), any());
        verify(sseEventService).broadcast(any(), any());
    }

    @Test
    void skipsBreachWhenNoCalls() {
        slaBreachService.evaluateSummary(
                new SlaSummaryResponse(0, 0, 0, 100, 0, 0, "local"),
                "test-correlation"
        );
        verify(breachLogMapper, never()).insert(any());
    }
}

