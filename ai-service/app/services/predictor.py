from __future__ import annotations

from typing import Any

from app.services.features import HistoryRecord
from app.services.models import EnsembleModel, statistical_predict

ensemble = EnsembleModel()


def ensure_history(items: list[dict[str, Any]]) -> list[HistoryRecord]:
    return [HistoryRecord.from_dict(item) for item in items]


def format_predict(period: str | None, reds: list[int], blue: int, conf: float, model: str) -> dict[str, Any]:
    return {
        "period": period or "next",
        "red_balls": reds,
        "blue_ball": blue,
        "model_name": model,
        "confidence": round(conf, 4),
        "note": "Sprint 2 prediction",
    }


def predict_default(history: list[HistoryRecord], period: str | None = None) -> dict[str, Any]:
    if not history:
        reds, blue, conf = statistical_predict([])
        return format_predict(period, reds, blue, conf, "statistical-v1")
    reds, blue, conf = ensemble.predict(history)
    return format_predict(period, reds, blue, conf, ensemble.name)
