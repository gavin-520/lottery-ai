from fastapi import APIRouter
from pydantic import BaseModel
import random

router = APIRouter(prefix="/api/v1", tags=["predict"])


class PredictRequest(BaseModel):
    period: str | None = None


class PredictResponse(BaseModel):
    period: str
    red_balls: list[int]
    blue_ball: int
    model_name: str
    confidence: float
    note: str


@router.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest):
    red = sorted(random.sample(range(1, 34), 6))
    blue = random.randint(1, 16)
    return PredictResponse(
        period=request.period or "next",
        red_balls=red,
        blue_ball=blue,
        model_name="mock-v0",
        confidence=0.42,
        note="Sprint 0 mock prediction — replace with real model in Sprint 1",
    )
