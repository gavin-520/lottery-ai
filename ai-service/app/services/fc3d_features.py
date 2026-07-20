from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import numpy as np


@dataclass
class Fc3dRecord:
    issue: str
    digit1: int
    digit2: int
    digit3: int
    sum_value: int
    span_value: int
    odd_even_pattern: str

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> Fc3dRecord:
        d1 = int(data["digit1"])
        d2 = int(data["digit2"])
        d3 = int(data["digit3"])
        raw_sum = data.get("sum_value")
        raw_span = data.get("span_value")
        sum_value = int(raw_sum if raw_sum is not None else d1 + d2 + d3)
        span_value = int(raw_span if raw_span is not None else max(d1, d2, d3) - min(d1, d2, d3))
        pattern = str(data.get("odd_even_pattern") or "".join("O" if x % 2 else "E" for x in (d1, d2, d3)))
        return cls(
            issue=str(data.get("issue", "")),
            digit1=d1,
            digit2=d2,
            digit3=d3,
            sum_value=sum_value,
            span_value=span_value,
            odd_even_pattern=pattern,
        )


class Fc3dFeatureBuilder:
    """FC3D feature engineering — parallel to SSQ FeatureBuilder."""

    DIGIT_RANGE = 10

    @classmethod
    def digit_frequency(cls, history: list[Fc3dRecord]) -> np.ndarray:
        """Global digit frequency across all positions (0–9)."""
        freq = np.zeros(cls.DIGIT_RANGE)
        for record in history:
            for digit in (record.digit1, record.digit2, record.digit3):
                if 0 <= digit <= 9:
                    freq[digit] += 1
        if len(history) > 0:
            freq = freq / (len(history) * 3)
        return freq

    @classmethod
    def sum_feature(cls, history: list[Fc3dRecord]) -> np.ndarray:
        """Sum statistics: mean, std, last, min, max (normalized)."""
        if not history:
            return np.zeros(5)
        sums = np.array([r.sum_value for r in history], dtype=float)
        return np.array(
            [
                sums.mean() / 27.0,
                sums.std() / 27.0 if len(sums) > 1 else 0.0,
                sums[-1] / 27.0,
                sums.min() / 27.0,
                sums.max() / 27.0,
            ]
        )

    @classmethod
    def span_feature(cls, history: list[Fc3dRecord]) -> np.ndarray:
        """Span statistics: mean, std, last, min, max (normalized)."""
        if not history:
            return np.zeros(5)
        spans = np.array([r.span_value for r in history], dtype=float)
        return np.array(
            [
                spans.mean() / 9.0,
                spans.std() / 9.0 if len(spans) > 1 else 0.0,
                spans[-1] / 9.0,
                spans.min() / 9.0,
                spans.max() / 9.0,
            ]
        )

    @classmethod
    def position_feature(cls, history: list[Fc3dRecord]) -> np.ndarray:
        """Per-position digit frequency (3 × 10)."""
        pos = np.zeros((3, cls.DIGIT_RANGE))
        if not history:
            return pos.flatten()
        for record in history:
            pos[0, record.digit1] += 1
            pos[1, record.digit2] += 1
            pos[2, record.digit3] += 1
        pos = pos / len(history)
        return pos.flatten()

    @classmethod
    def build_vector(cls, history: list[Fc3dRecord]) -> np.ndarray:
        return np.concatenate(
            [
                cls.digit_frequency(history),
                cls.sum_feature(history),
                cls.span_feature(history),
                cls.position_feature(history),
            ]
        )

    @classmethod
    def features_dict(cls, history: list[Fc3dRecord]) -> dict[str, list[float]]:
        return {
            "digit_frequency": cls.digit_frequency(history).tolist(),
            "sum_feature": cls.sum_feature(history).tolist(),
            "span_feature": cls.span_feature(history).tolist(),
            "position_feature": cls.position_feature(history).tolist(),
        }


def predict_fc3d_statistical(history: list[Fc3dRecord], issue: str | None = None) -> dict[str, Any]:
    """Frequency-based FC3D prediction using position features."""
    if not history:
        d1, d2, d3 = 1, 2, 3
    else:
        pos = Fc3dFeatureBuilder.position_feature(history).reshape(3, 10)
        d1 = int(np.argmax(pos[0]))
        d2 = int(np.argmax(pos[1]))
        d3 = int(np.argmax(pos[2]))

    return {
        "lottery_type": "FC3D",
        "issue": issue or "next",
        "digit1": d1,
        "digit2": d2,
        "digit3": d3,
        "sum_value": d1 + d2 + d3,
        "span_value": max(d1, d2, d3) - min(d1, d2, d3),
        "odd_even_pattern": "".join("O" if x % 2 else "E" for x in (d1, d2, d3)),
        "model_name": "fc3d-statistical-v1",
        "confidence": 0.58,
        "note": "fc3d position frequency",
        "features": Fc3dFeatureBuilder.features_dict(history),
    }
