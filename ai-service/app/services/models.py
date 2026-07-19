from __future__ import annotations

from typing import Any, Protocol

import numpy as np

from app.services.features import FeatureBuilder, HistoryRecord


class BallModel(Protocol):
    name: str

    def train(self, history: list[HistoryRecord]) -> bool: ...

    def predict(self, history: list[HistoryRecord]) -> tuple[list[int], int, float]: ...


def statistical_predict(history: list[HistoryRecord]) -> tuple[list[int], int, float]:
    red_freq = np.zeros(33)
    blue_freq = np.zeros(16)
    for record in history:
        for ball in record.red_balls:
            red_freq[ball - 1] += 1
        blue_freq[record.blue_ball - 1] += 1
    red_balls = sorted((np.argsort(red_freq)[-6:] + 1).tolist())
    blue_ball = int(np.argmax(blue_freq) + 1)
    return red_balls, blue_ball, 0.45


class XGBModel:
    name = "xgboost-v1"

    def __init__(self) -> None:
        self.red_models: list[Any] = []
        self.blue_model: Any | None = None
        self.ready = False

    def train(self, history: list[HistoryRecord]) -> bool:
        from xgboost import XGBClassifier

        result = FeatureBuilder.build_training(history)
        if result[0] is None:
            self.ready = False
            return False

        x, y_red_rows, y_blue = result
        self.red_models = []
        for ball in range(1, 34):
            y = np.array([1 if ball in reds else 0 for reds in y_red_rows])
            model = XGBClassifier(
                n_estimators=40, max_depth=3, learning_rate=0.1,
                objective="binary:logistic", eval_metric="logloss", verbosity=0,
            )
            model.fit(x, y)
            self.red_models.append(model)

        self.blue_model = XGBClassifier(
            n_estimators=40, max_depth=3, learning_rate=0.1,
            objective="multi:softprob", num_class=16, eval_metric="mlogloss", verbosity=0,
        )
        self.blue_model.fit(x, y_blue - 1)
        self.ready = True
        return True

    def predict(self, history: list[HistoryRecord]) -> tuple[list[int], int, float]:
        if not self.ready or not self.red_models or self.blue_model is None:
            return statistical_predict(history)
        features = FeatureBuilder.build_matrix(history[-20:]).reshape(1, -1)
        red_probs = np.array([m.predict_proba(features)[0][1] for m in self.red_models])
        red_balls = sorted((np.argsort(red_probs)[-6:] + 1).tolist())
        blue_probs = self.blue_model.predict_proba(features)[0]
        blue_ball = int(np.argmax(blue_probs) + 1)
        confidence = float((red_probs[np.array(red_balls) - 1].mean() + blue_probs.max()) / 2)
        return red_balls, blue_ball, confidence


class LGBModel:
    name = "lightgbm-v1"

    def __init__(self) -> None:
        self.red_models: list[Any] = []
        self.blue_model: Any | None = None
        self.ready = False

    def train(self, history: list[HistoryRecord]) -> bool:
        from lightgbm import LGBMClassifier

        result = FeatureBuilder.build_training(history)
        if result[0] is None:
            self.ready = False
            return False

        x, y_red_rows, y_blue = result
        self.red_models = []
        for ball in range(1, 34):
            y = np.array([1 if ball in reds else 0 for reds in y_red_rows])
            model = LGBMClassifier(
                n_estimators=40, max_depth=3, learning_rate=0.1,
                objective="binary", verbose=-1,
            )
            model.fit(x, y)
            self.red_models.append(model)

        self.blue_model = LGBMClassifier(
            n_estimators=40, max_depth=3, learning_rate=0.1,
            objective="multiclass", num_class=16, verbose=-1,
        )
        self.blue_model.fit(x, y_blue - 1)
        self.ready = True
        return True

    def predict(self, history: list[HistoryRecord]) -> tuple[list[int], int, float]:
        if not self.ready or not self.red_models or self.blue_model is None:
            return statistical_predict(history)
        features = FeatureBuilder.build_matrix(history[-20:]).reshape(1, -1)
        red_probs = np.array([m.predict_proba(features)[0][1] for m in self.red_models])
        red_balls = sorted((np.argsort(red_probs)[-6:] + 1).tolist())
        blue_probs = self.blue_model.predict_proba(features)[0]
        blue_ball = int(np.argmax(blue_probs) + 1)
        confidence = float((red_probs[np.array(red_balls) - 1].mean() + blue_probs.max()) / 2)
        return red_balls, blue_ball, confidence


class EnsembleModel:
    name = "ensemble-v1"
    MIN_HISTORY = 25

    def __init__(self) -> None:
        self.xgb = XGBModel()
        self.lgb = LGBModel()

    def train(self, history: list[HistoryRecord]) -> bool:
        if len(history) < self.MIN_HISTORY:
            return False
        ok_x = self.xgb.train(history)
        ok_l = self.lgb.train(history)
        return ok_x or ok_l

    def predict(self, history: list[HistoryRecord]) -> tuple[list[int], int, float]:
        if len(history) < self.MIN_HISTORY:
            return statistical_predict(history)

        self.train(history)
        x_red, x_blue, x_conf = self.xgb.predict(history)
        l_red, l_blue, l_conf = self.lgb.predict(history)

        red_scores: dict[int, float] = {}
        for ball in set(x_red + l_red):
            score = 0.0
            if ball in x_red:
                score += 0.5
            if ball in l_red:
                score += 0.5
            red_scores[ball] = score
        red_balls = sorted(k for k, _ in sorted(red_scores.items(), key=lambda x: x[1], reverse=True)[:6])

        blue_ball = x_blue if x_conf >= l_conf else l_blue
        confidence = (x_conf + l_conf) / 2
        return red_balls, blue_ball, confidence

    def compare(self, history: list[HistoryRecord]) -> list[dict[str, Any]]:
        self.train(history)
        models: list[BallModel] = [self.xgb, self.lgb]
        results = []
        for model in models:
            reds, blue, conf = model.predict(history)
            results.append({
                "model_name": model.name,
                "red_balls": reds,
                "blue_ball": blue,
                "confidence": round(conf, 4),
            })
        reds, blue, conf = self.predict(history)
        results.append({
            "model_name": self.name,
            "red_balls": reds,
            "blue_ball": blue,
            "confidence": round(conf, 4),
        })
        return results
