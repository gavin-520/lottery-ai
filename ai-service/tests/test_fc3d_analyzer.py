from app.services.fc3d_analyzer import DISCLAIMER, FORBIDDEN_PHRASES, Fc3dAnalyzer

ANALYTICS = {
    "frequency": {
        "hundreds": {"0": 10, "1": 30, "2": 5},
        "tens": {"0": 8, "1": 5, "2": 20},
        "units": {"0": 12, "1": 9, "2": 25},
    },
    "sum": {"average": 13.6, "distribution": {"13": 40, "14": 35}},
    "odd_even": {"pattern": "OOE", "odd_count": 2, "even_count": 1},
}

CANDIDATES = [
    {
        "number": "123",
        "score": 85,
        "reasons": ["百位热号", "和值处于历史高频区间", "奇偶结构接近历史常见形态"],
    },
    {
        "number": "456",
        "score": 40,
        "reasons": ["近几期已出现，降权处理"],
    },
    {
        "number": "111",
        "score": 20,
        "reasons": ["综合频率与形态的统计候选"],
    },
]

HISTORY = [
    {"issue": "2026189", "digit1": 1, "digit2": 2, "digit3": 3, "sum_value": 6, "odd_even_pattern": "OOE"},
    {"issue": "2026188", "digit1": 4, "digit2": 5, "digit3": 6, "sum_value": 15, "odd_even_pattern": "EOE"},
]


def build_analyzer() -> Fc3dAnalyzer:
    return Fc3dAnalyzer()


def test_analyze_returns_required_top_level_keys():
    result = build_analyzer().analyze(ANALYTICS, CANDIDATES, best="123", history=HISTORY)

    for key in ("lottery_type", "features", "candidate_analysis", "recommendation", "confidence"):
        assert key in result
    assert result["lottery_type"] == "FC3D"


def test_features_summarizes_hot_digits_and_sum_and_odd_even():
    result = build_analyzer().analyze(ANALYTICS, CANDIDATES, best="123", history=HISTORY)
    features = result["features"]

    assert features["hot_digits"]["hundreds"][0] == 1
    assert features["hot_digits"]["units"][0] == 2
    assert features["sum_average"] == 13.6
    assert features["dominant_odd_even"] == "OOE"
    assert len(features["notes"]) >= 1


def test_candidate_analysis_covers_every_candidate_with_comment():
    result = build_analyzer().analyze(ANALYTICS, CANDIDATES, best="123", history=HISTORY)
    analysis = result["candidate_analysis"]

    assert len(analysis) == len(CANDIDATES)
    numbers = {item["number"] for item in analysis}
    assert numbers == {"123", "456", "111"}
    for item in analysis:
        assert item["comment"]
        assert "aligned_signals" in item
        assert "risk_flags" in item


def test_recommendation_prefers_best_and_includes_disclaimer():
    result = build_analyzer().analyze(ANALYTICS, CANDIDATES, best="123", history=HISTORY)
    recommendation = result["recommendation"]

    assert recommendation["preferred"] == "123"
    assert recommendation["disclaimer"] == DISCLAIMER
    assert len(recommendation["rationale"]) >= 1


def test_confidence_is_bounded_between_0_4_and_0_85():
    result = build_analyzer().analyze(ANALYTICS, CANDIDATES, best="123", history=HISTORY)
    assert 0.4 <= result["confidence"] <= 0.85


def test_repeated_candidate_flagged_as_risk():
    result = build_analyzer().analyze(ANALYTICS, CANDIDATES, best="123", history=HISTORY)
    repeated = next(item for item in result["candidate_analysis"] if item["number"] == "456")
    assert repeated["risk_flags"], "expected a risk flag for a de-weighted repeated candidate"


def test_no_forbidden_win_guarantee_language_anywhere_in_output():
    result = build_analyzer().analyze(ANALYTICS, CANDIDATES, best="123", history=HISTORY)
    serialized = str(result)
    for phrase in FORBIDDEN_PHRASES:
        assert phrase not in serialized


def test_handles_empty_candidates_gracefully():
    result = build_analyzer().analyze(ANALYTICS, [], best=None, history=[])
    assert result["candidate_analysis"] == []
    assert result["recommendation"]["disclaimer"] == DISCLAIMER
    assert 0.4 <= result["confidence"] <= 0.85
