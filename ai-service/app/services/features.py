from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import numpy as np


@dataclass
class HistoryRecord:
    period: str
    red_balls: list[int]
    blue_ball: int

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> HistoryRecord:
        reds = [int(x) for x in str(data["red_balls"]).replace(" ", "").split(",")]
        return cls(
            period=str(data.get("period", "")),
            red_balls=reds,
            blue_ball=int(data["blue_ball"]),
        )


class FeatureBuilder:
    RED_COUNT = 33
    BLUE_COUNT = 16

    @classmethod
    def build_matrix(cls, history: list[HistoryRecord]) -> np.ndarray:
        red_freq = np.zeros(cls.RED_COUNT)
        blue_freq = np.zeros(cls.BLUE_COUNT)
        red_miss = np.full(cls.RED_COUNT, len(history), dtype=float)
        blue_miss = np.full(cls.BLUE_COUNT, len(history), dtype=float)

        for idx, record in enumerate(history):
            for ball in record.red_balls:
                red_freq[ball - 1] += 1
                red_miss[ball - 1] = len(history) - idx - 1
            blue_freq[record.blue_ball - 1] += 1
            blue_miss[record.blue_ball - 1] = len(history) - idx - 1

        return np.concatenate([red_freq, blue_freq, red_miss, blue_miss])

    @classmethod
    def build_training(cls, history: list[HistoryRecord], window: int = 20):
        if len(history) <= window + 1:
            return None, None, None

        x_rows: list[np.ndarray] = []
        y_red_rows: list[list[int]] = []
        y_blue: list[int] = []

        for i in range(window, len(history)):
            window_history = history[i - window : i]
            target = history[i]
            x_rows.append(cls.build_matrix(window_history))
            y_red_rows.append(target.red_balls)
            y_blue.append(target.blue_ball)

        return np.vstack(x_rows), y_red_rows, np.array(y_blue)
