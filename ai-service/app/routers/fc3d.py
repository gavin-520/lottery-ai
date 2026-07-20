from typing import Any

from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.services.fc3d_analyzer import Fc3dAnalyzer
from app.services.fc3d_features import Fc3dFeatureBuilder, Fc3dRecord, predict_fc3d_statistical

router = APIRouter(prefix="/api/v1/fc3d", tags=["fc3d"])
analyzer = Fc3dAnalyzer()


class Fc3dHistoryItem(BaseModel):
    issue: str | None = None
    digit1: int
    digit2: int
    digit3: int
    sum_value: int | None = None
    span_value: int | None = None
    odd_even_pattern: str | None = None


class Fc3dPredictRequest(BaseModel):
    issue: str | None = None
    history: list[Fc3dHistoryItem] = Field(default_factory=list)


class Fc3dPredictResponse(BaseModel):
    lottery_type: str = "FC3D"
    issue: str
    digit1: int
    digit2: int
    digit3: int
    sum_value: int
    span_value: int
    odd_even_pattern: str
    model_name: str
    confidence: float
    note: str = ""


class Fc3dFeaturesResponse(BaseModel):
    lottery_type: str = "FC3D"
    digit_frequency: list[float]
    sum_feature: list[float]
    span_feature: list[float]
    position_feature: list[float]


class Fc3dCandidateItem(BaseModel):
    number: str
    score: int
    reasons: list[str] = Field(default_factory=list)


class Fc3dAnalyzeRequest(BaseModel):
    issue: str | None = None
    question: str | None = None
    analytics: dict[str, Any] = Field(default_factory=dict)
    candidates: list[Fc3dCandidateItem] = Field(default_factory=list)
    best: str | None = None
    history: list[Fc3dHistoryItem] = Field(default_factory=list)


class Fc3dCandidateAnalysisItem(BaseModel):
    number: str
    score: int
    aligned_signals: list[str] = Field(default_factory=list)
    risk_flags: list[str] = Field(default_factory=list)
    comment: str


class Fc3dRecommendation(BaseModel):
    preferred: str | None = None
    rationale: list[str] = Field(default_factory=list)
    disclaimer: str


class Fc3dFeaturesSummary(BaseModel):
    hot_digits: dict[str, list[int]]
    sum_average: float | None = None
    dominant_odd_even: str | None = None
    notes: list[str] = Field(default_factory=list)


class Fc3dAnalyzeResponse(BaseModel):
    lottery_type: str = "FC3D"
    features: Fc3dFeaturesSummary
    candidate_analysis: list[Fc3dCandidateAnalysisItem]
    recommendation: Fc3dRecommendation
    confidence: float
    model_name: str


@router.post("/predict", response_model=Fc3dPredictResponse)
def predict_fc3d(request: Fc3dPredictRequest):
    history = [Fc3dRecord.from_dict(item.model_dump()) for item in request.history]
    result = predict_fc3d_statistical(history, request.issue)
    return Fc3dPredictResponse(
        lottery_type=result["lottery_type"],
        issue=result["issue"],
        digit1=result["digit1"],
        digit2=result["digit2"],
        digit3=result["digit3"],
        sum_value=result["sum_value"],
        span_value=result["span_value"],
        odd_even_pattern=result["odd_even_pattern"],
        model_name=result["model_name"],
        confidence=result["confidence"],
        note=result.get("note", ""),
    )


@router.post("/features", response_model=Fc3dFeaturesResponse)
def fc3d_features(request: Fc3dPredictRequest):
    history = [Fc3dRecord.from_dict(item.model_dump()) for item in request.history]
    features = Fc3dFeatureBuilder.features_dict(history)
    return Fc3dFeaturesResponse(
        digit_frequency=features["digit_frequency"],
        sum_feature=features["sum_feature"],
        span_feature=features["span_feature"],
        position_feature=features["position_feature"],
    )


@router.post("/analyze", response_model=Fc3dAnalyzeResponse)
def analyze_fc3d(request: Fc3dAnalyzeRequest):
    """Explain existing statistical candidates. Does NOT generate new numbers."""
    result = analyzer.analyze(
        analytics=request.analytics,
        candidates=[c.model_dump() for c in request.candidates],
        best=request.best,
        history=[h.model_dump() for h in request.history],
        question=request.question,
    )
    return Fc3dAnalyzeResponse(**result)
