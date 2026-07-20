package com.lottery.service;

import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingItem;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dOddEvenResponse;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.util.Fc3dBallUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class Fc3dAnalyticsServiceTest {

    @Mock
    private Fc3dPredictService fc3dPredictService;

    private Fc3dAnalyticsService analyticsService;
    private List<Fc3dDrawEntity> history100;

    @BeforeEach
    void setUp() {
        analyticsService = new Fc3dAnalyticsService(fc3dPredictService);
        history100 = buildHistory(100);
        lenient().when(fc3dPredictService.listHistoryAsc()).thenReturn(history100);
    }

    @Test
    void positionFrequency_countsHundredsTensUnits_forLast100() {
        Fc3dFrequencyResponse freq = analyticsService.getPositionFrequency(history100);

        assertEquals(100, freq.getTotalPeriods());
        // digit1 cycles 0..9 → each hundreds digit appears 10 times
        assertEquals(10, freq.getHundreds().get("0"));
        assertEquals(10, freq.getHundreds().get("5"));
        assertEquals(10, freq.getTens().get("1"));
        assertEquals(10, freq.getUnits().get("9"));
        // compatibility lists still populated
        assertEquals(10, freq.getPos1Frequency().size());
        assertEquals(10, freq.getPos1Frequency().get(0).getCount());
    }

    @Test
    void calculateMissing_isCorrectForControlledHistory() {
        // Ascending history: last appearance of hundreds=3 was at index 3 → missing = 6 when size=10
        List<Fc3dDrawEntity> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(draw("202600" + i, i % 10, (i + 1) % 10, (i + 2) % 10));
        }
        // Override so hundreds digit 7 never appears
        history.get(0).setDigit1(0);
        history.get(1).setDigit1(1);
        history.get(2).setDigit1(2);
        history.get(3).setDigit1(3);
        history.get(4).setDigit1(4);
        history.get(5).setDigit1(5);
        history.get(6).setDigit1(6);
        history.get(7).setDigit1(8); // skip 7
        history.get(8).setDigit1(8);
        history.get(9).setDigit1(9);

        Fc3dMissingResponse missing = analyticsService.calculateMissing(history);
        Fc3dMissingItem sevenHundreds = missing.getItems().stream()
                .filter(i -> "hundreds".equals(i.getPosition()) && i.getNumber() == 7)
                .findFirst()
                .orElseThrow();
        assertEquals(10, sevenHundreds.getMissing()); // never appeared

        Fc3dMissingItem threeHundreds = missing.getItems().stream()
                .filter(i -> "hundreds".equals(i.getPosition()) && i.getNumber() == 3)
                .findFirst()
                .orElseThrow();
        // last at index 3, end=10 → missing = 6
        assertEquals(6, threeHundreds.getMissing());
    }

    @Test
    void calculateSumAnalysis_averageAndTrend() {
        List<Fc3dDrawEntity> history = new ArrayList<>();
        // sums: 0+0+0=0, 1+1+1=3, 2+2+2=6 → average = 3.0
        history.add(draw("1", 0, 0, 0));
        history.add(draw("2", 1, 1, 1));
        history.add(draw("3", 2, 2, 2));

        Fc3dSumAnalysisResponse sum = analyticsService.calculateSumAnalysis(history, 2);
        assertEquals(3, sum.getTotalPeriods());
        assertEquals(3.0, sum.getAverage(), 0.0001);
        assertEquals(1, sum.getDistribution().get("0"));
        assertEquals(1, sum.getDistribution().get("3"));
        assertEquals(1, sum.getDistribution().get("6"));
        assertEquals(2, sum.getRecentTrend().size());
        assertEquals("2", sum.getRecentTrend().get(0).getIssue());
        assertEquals(3, sum.getRecentTrend().get(0).getSum());
        assertEquals(6, sum.getRecentTrend().get(1).getSum());
    }

    @Test
    void calculateOddEvenAnalysis_latestPattern() {
        List<Fc3dDrawEntity> history = List.of(
                draw("1", 1, 2, 3),
                draw("2", 1, 1, 2) // OOE → odd=2 even=1
        );
        Fc3dOddEvenResponse oe = analyticsService.calculateOddEvenAnalysis(history);
        assertEquals(2, oe.getOddCount());
        assertEquals(1, oe.getEvenCount());
        assertEquals("OOE", oe.getPattern());
        assertEquals("2", oe.getIssue());
    }

    @Test
    void last100_pipeline_viaMockedService() {
        Fc3dFrequencyResponse freq = analyticsService.getPositionFrequency();
        Fc3dMissingResponse missing = analyticsService.calculateMissing();
        Fc3dSumAnalysisResponse sum = analyticsService.calculateSumAnalysis();

        assertEquals(100, freq.getTotalPeriods());
        assertEquals(30, missing.getItems().size());
        assertTrue(sum.getAverage() > 0);
        assertFalse(sum.getRecentTrend().isEmpty());
        assertEquals(10, freq.getHundreds().size());
    }

    private List<Fc3dDrawEntity> buildHistory(int n) {
        List<Fc3dDrawEntity> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int d1 = i % 10;
            int d2 = (i + 1) % 10;
            int d3 = (i + 2) % 10;
            list.add(draw(String.format("%07d", i + 1), d1, d2, d3));
        }
        return list;
    }

    private Fc3dDrawEntity draw(String issue, int d1, int d2, int d3) {
        Fc3dDrawEntity e = new Fc3dDrawEntity();
        e.setIssue(issue);
        e.setDigit1(d1);
        e.setDigit2(d2);
        e.setDigit3(d3);
        e.setSumValue(Fc3dBallUtils.sum(d1, d2, d3));
        e.setSpanValue(Fc3dBallUtils.span(d1, d2, d3));
        e.setOddEvenPattern(Fc3dBallUtils.oddEvenPattern(d1, d2, d3));
        e.setLotteryType("FC3D");
        return e;
    }
}
