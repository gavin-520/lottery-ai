package com.lottery.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sprint 11-B §4: verifies the manual, human-confirmed apply/resolve contract for Ensemble
 * fusion weight overrides — never auto-applied, only ever set via an explicit {@link
 * Fc3dEnsembleWeightOverrideService#apply} call.
 */
class Fc3dEnsembleWeightOverrideServiceTest {

    @Test
    void resolve_returnsEmpty_beforeAnyApply() {
        Fc3dEnsembleWeightOverrideService service = new Fc3dEnsembleWeightOverrideService();
        assertTrue(service.resolve(List.of("v3", "v3-exp-001")).isEmpty());
    }

    @Test
    void resolve_returnsAppliedWeights_forExactVersionSet_orderIndependent() {
        Fc3dEnsembleWeightOverrideService service = new Fc3dEnsembleWeightOverrideService();
        service.apply(List.of("v3", "v3-exp-001"), Map.of("v3", 0.6, "v3-exp-001", 0.4));

        Optional<Map<String, Double>> resolved = service.resolve(List.of("v3-exp-001", "v3"));
        assertTrue(resolved.isPresent());
        assertEquals(0.6, resolved.get().get("v3"));
        assertEquals(0.4, resolved.get().get("v3-exp-001"));
    }

    @Test
    void resolve_returnsEmpty_whenVersionSetDoesNotMatchExactly() {
        Fc3dEnsembleWeightOverrideService service = new Fc3dEnsembleWeightOverrideService();
        service.apply(List.of("v3", "v3-exp-001"), Map.of("v3", 0.6, "v3-exp-001", 0.4));

        assertTrue(service.resolve(List.of("v3")).isEmpty(), "a different (subset) version set must not reuse the override");
        assertTrue(service.resolve(List.of("v3", "v3-exp-001", "frequency-only-baseline")).isEmpty(),
                "a different (superset) version set must not reuse the override");
    }

    @Test
    void clear_removesAnyAppliedOverride() {
        Fc3dEnsembleWeightOverrideService service = new Fc3dEnsembleWeightOverrideService();
        service.apply(List.of("v3", "v3-exp-001"), Map.of("v3", 0.6, "v3-exp-001", 0.4));
        service.clear();

        assertTrue(service.resolve(List.of("v3", "v3-exp-001")).isEmpty());
        assertTrue(service.current().isEmpty());
    }
}
