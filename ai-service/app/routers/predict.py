from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.services.agent import LotteryAgent
from app.services.features import HistoryRecord
from app.services.kafka_consumer import is_degraded
from app.services.models import EnsembleModel
from app.services.multi_agent import MultiAgentWorkflow
from app.services.predictor import ensure_history, format_predict, predict_default

router = APIRouter(prefix="/api/v1", tags=["predict"])
ensemble = EnsembleModel()
agent = LotteryAgent()
workflow = MultiAgentWorkflow()


class HistoryItem(BaseModel):
    period: str | None = None
    red_balls: str
    blue_ball: str | int


class PredictRequest(BaseModel):
    period: str | None = None
    history: list[HistoryItem] = Field(default_factory=list)


class PredictResponse(BaseModel):
    period: str
    red_balls: list[int]
    blue_ball: int
    model_name: str
    confidence: float
    note: str


class CompareResponse(BaseModel):
    models: list[PredictResponse]


class AgentRequest(BaseModel):
    question: str = ""
    total_periods: int = 0
    red_frequency: list[dict] = Field(default_factory=list)
    blue_frequency: list[dict] = Field(default_factory=list)
    history: list[HistoryItem] = Field(default_factory=list)


class AgentResponse(BaseModel):
    summary: str
    insights: list[str]
    recommendations: list[str]
    agent_name: str


class WorkflowStep(BaseModel):
    agent: str
    role: str
    output: str


class WorkflowResponse(BaseModel):
    final_report: str
    steps: list[WorkflowStep]
    workflow_name: str


def _ensure_not_degraded_for_ensemble() -> None:
    if is_degraded():
        raise HTTPException(
            status_code=503,
            detail="Ensemble unavailable: AI service in degraded mode after sync failure",
        )


@router.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest):
    history = ensure_history([item.model_dump() for item in request.history])
    result = predict_default(history, request.period)
    if is_degraded():
        result = dict(result)
        result["note"] = (result.get("note") or "") + " [degraded: statistical fallback]"
    return PredictResponse(**result)


@router.post("/predict/ensemble", response_model=PredictResponse)
def predict_ensemble(request: PredictRequest):
    _ensure_not_degraded_for_ensemble()
    history = ensure_history([item.model_dump() for item in request.history])
    reds, blue, conf = ensemble.predict(history)
    result = format_predict(request.period, reds, blue, conf, ensemble.name)
    return PredictResponse(**result)


@router.post("/models/compare", response_model=CompareResponse)
def compare_models(request: PredictRequest):
    _ensure_not_degraded_for_ensemble()
    history = ensure_history([item.model_dump() for item in request.history])
    models = ensemble.compare(history)
    return CompareResponse(models=[PredictResponse(**m, note="model compare") for m in models])


@router.post("/agent/analyze", response_model=AgentResponse)
def analyze(request: AgentRequest):
    history = [HistoryRecord.from_dict(item.model_dump()) for item in request.history]
    result = agent.analyze(
        request.question,
        request.total_periods,
        request.red_frequency,
        request.blue_frequency,
        history,
    )
    return AgentResponse(**result)


@router.post("/agent/workflow", response_model=WorkflowResponse)
def run_workflow(request: AgentRequest):
    history = [HistoryRecord.from_dict(item.model_dump()) for item in request.history]
    result = workflow.run(
        request.question,
        request.total_periods,
        request.red_frequency,
        request.blue_frequency,
        history,
    )
    return WorkflowResponse(**result)
