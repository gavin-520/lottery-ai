package com.lottery.service;

import com.lottery.dto.Fc3dCombinationResponse;
import com.lottery.dto.Fc3dEnsembleBacktestResult;
import com.lottery.dto.Fc3dEnsembleCandidate;
import com.lottery.dto.Fc3dEnsembleMemberInput;
import com.lottery.dto.Fc3dFrequencyResponse;
import com.lottery.dto.Fc3dMissingResponse;
import com.lottery.dto.Fc3dModelEvaluationResult;
import com.lottery.dto.Fc3dModelInfo;
import com.lottery.dto.Fc3dSumAnalysisResponse;
import com.lottery.entity.Fc3dDrawEntity;
import com.lottery.rule.fc3d.Fc3dCombinationGenerator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 11-A §6: walk-forward comparison of the fused ensemble Top10/20/50 against the single
 * production model's own Top10/20/50, over the IDENTICAL evaluation window.
 *
 * <p>Reuses {@link Fc3dModelEvaluationService#evaluateModel} unchanged (same {@code
 * history.subList(0, i)} walk-forward cutoff already proven not to leak future data in Sprint
 * 10-C/10-E) — only the ranking function passed to it differs: for "ensemble" every train slice
 * is fanned out to each participating model's OWN generator, fused via {@link Fc3dEnsembleEngine},
 * then re-ranked; for the single-model baseline it is the plain {@code combinationRanking}
 * already used elsewhere.</p>
 */
@Service
public class Fc3dEnsembleBacktestService {

    private static final int DEFAULT_MIN_HISTORY = 30;
    private static final int DEFAULT_EVAL_PERIODS = 200;

    private final Fc3dPredictService fc3dPredictService;
    private final Fc3dAnalyticsService fc3dAnalyticsService;
    private final Fc3dModelRegistryService fc3dModelRegistryService;
    private final Fc3dModelEvaluationService fc3dModelEvaluationService;
    private final Fc3dEnsemblePredictService fc3dEnsemblePredictService;
    private final Fc3dEnsembleEngine fc3dEnsembleEngine;

    public Fc3dEnsembleBacktestService(Fc3dPredictService fc3dPredictService,
                                       Fc3dAnalyticsService fc3dAnalyticsService,
                                       Fc3dModelRegistryService fc3dModelRegistryService,
                                       Fc3dModelEvaluationService fc3dModelEvaluationService,
                                       Fc3dEnsemblePredictService fc3dEnsemblePredictService,
                                       Fc3dEnsembleEngine fc3dEnsembleEngine) {
        this.fc3dPredictService = fc3dPredictService;
        this.fc3dAnalyticsService = fc3dAnalyticsService;
        this.fc3dModelRegistryService = fc3dModelRegistryService;
        this.fc3dModelEvaluationService = fc3dModelEvaluationService;
        this.fc3dEnsemblePredictService = fc3dEnsemblePredictService;
        this.fc3dEnsembleEngine = fc3dEnsembleEngine;
    }

    public Fc3dEnsembleBacktestResult evaluate(List<String> modelVersions, int minHistory, int evalPeriods) {
        int window = minHistory <= 0 ? DEFAULT_MIN_HISTORY : minHistory;
        int periods = evalPeriods <= 0 ? DEFAULT_EVAL_PERIODS : evalPeriods;

        List<String> versions = fc3dEnsemblePredictService.resolveVersions(modelVersions);
        if (versions.isEmpty()) {
            return new Fc3dEnsembleBacktestResult(List.of(), null, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        List<Fc3dDrawEntity> history = fc3dPredictService.listHistoryAsc();
        Map<String, Double> weights = fc3dEnsemblePredictService.resolveWeights(versions);

        Map<String, Fc3dCombinationGenerator> generators = new LinkedHashMap<>();
        for (String version : versions) {
            generators.put(version, new Fc3dCombinationGenerator(fc3dModelRegistryService.resolveConfig(version)));
        }

        String singleModelVersion = fc3dModelRegistryService.getActiveModel()
                .map(Fc3dModelInfo::getVersion)
                .orElse(versions.get(0));
        Fc3dCombinationGenerator singleGenerator = generators.computeIfAbsent(singleModelVersion,
                v -> new Fc3dCombinationGenerator(fc3dModelRegistryService.resolveConfig(v)));

        Fc3dModelEvaluationResult ensembleEval = fc3dModelEvaluationService.evaluateModel(
                "ensemble", history, window, periods,
                train -> ensembleRanking(train, versions, generators, weights));
        Fc3dModelEvaluationResult singleEval = fc3dModelEvaluationService.evaluateModel(
                singleModelVersion, history, window, periods,
                train -> fc3dModelEvaluationService.combinationRanking(singleGenerator, train));

        double improvement = round4(ensembleEval.getTop50HitRate() - singleEval.getTop50HitRate());

        return new Fc3dEnsembleBacktestResult(
                versions, singleModelVersion, ensembleEval.getEvaluatedPeriods(),
                singleEval.getTop10HitRate(), singleEval.getTop20HitRate(), singleEval.getTop50HitRate(),
                ensembleEval.getTop10HitRate(), ensembleEval.getTop20HitRate(), ensembleEval.getTop50HitRate(),
                improvement);
    }

    /** Fans a SINGLE train slice out to every participating model's own generator, then fuses — never mixes train slices across models. */
    private List<String> ensembleRanking(List<Fc3dDrawEntity> train, List<String> versions,
                                         Map<String, Fc3dCombinationGenerator> generators,
                                         Map<String, Double> weights) {
        Fc3dFrequencyResponse frequency = fc3dAnalyticsService.getPositionFrequency(train);
        Fc3dMissingResponse missing = fc3dAnalyticsService.calculateMissing(train);
        Fc3dSumAnalysisResponse sumAnalysis = fc3dAnalyticsService.calculateSumAnalysis(train, 30);

        List<Fc3dEnsembleMemberInput> members = new ArrayList<>(versions.size());
        for (String version : versions) {
            Fc3dCombinationResponse response = generators.get(version).generate(train, frequency, missing, sumAnalysis);
            members.add(new Fc3dEnsembleMemberInput(version, response.getCandidates()));
        }
        List<Fc3dEnsembleCandidate> fused = fc3dEnsembleEngine.fuseAll(members, weights);
        return fused.stream().map(Fc3dEnsembleCandidate::getNumber).toList();
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
