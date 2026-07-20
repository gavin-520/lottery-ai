from __future__ import annotations

from typing import Any

DISCLAIMER = "本结果仅供统计分析与研究参考，不构成中奖承诺或购彩建议。"

FORBIDDEN_PHRASES = ("保证中奖", "预测中奖号码", "必中", "包中")


class Fc3dAnalyzer:
    """Explains FC3D statistical candidates.

    This service does NOT generate new numbers. It only interprets analytics
    (frequency / missing / sum / odd-even) and existing candidates produced
    by the backend rule engine, and returns a structured, explainable report.
    """

    MODEL_NAME = "fc3d-analyze-v1"

    def analyze(
        self,
        analytics: dict[str, Any],
        candidates: list[dict[str, Any]],
        best: str | None,
        history: list[dict[str, Any]] | None = None,
        question: str | None = None,
    ) -> dict[str, Any]:
        history = history or []
        features = self._build_features(analytics, history)
        candidate_analysis = self._analyze_candidates(candidates, analytics)
        recommendation = self._build_recommendation(candidate_analysis, best, question)
        confidence = self._estimate_confidence(candidate_analysis)

        return {
            "lottery_type": "FC3D",
            "features": features,
            "candidate_analysis": candidate_analysis,
            "recommendation": recommendation,
            "confidence": confidence,
            "model_name": self.MODEL_NAME,
        }

    def _build_features(self, analytics: dict[str, Any], history: list[dict[str, Any]]) -> dict[str, Any]:
        frequency = analytics.get("frequency") or {}
        hundreds = frequency.get("hundreds") or {}
        tens = frequency.get("tens") or {}
        units = frequency.get("units") or {}

        sum_info = analytics.get("sum") or {}
        odd_even = analytics.get("odd_even") or {}

        notes = ["基于历史频率、遗漏、和值与奇偶形态的统计摘要"]
        if len(history) > 0:
            notes.append(f"参考近 {len(history)} 期历史走势")

        return {
            "hot_digits": {
                "hundreds": self._top_digits(hundreds),
                "tens": self._top_digits(tens),
                "units": self._top_digits(units),
            },
            "sum_average": sum_info.get("average"),
            "dominant_odd_even": odd_even.get("pattern"),
            "notes": notes,
        }

    def _analyze_candidates(
        self, candidates: list[dict[str, Any]], analytics: dict[str, Any]
    ) -> list[dict[str, Any]]:
        sum_info = analytics.get("sum") or {}
        average = sum_info.get("average")

        results: list[dict[str, Any]] = []
        for candidate in candidates:
            number = str(candidate.get("number", ""))
            score = candidate.get("score", 0)
            reasons = list(candidate.get("reasons") or [])

            aligned_signals = self._map_reasons_to_signals(reasons)
            risk_flags = self._risk_flags(number, reasons, average)
            comment = self._comment(number, score, aligned_signals, risk_flags)

            results.append(
                {
                    "number": number,
                    "score": score,
                    "aligned_signals": aligned_signals,
                    "risk_flags": risk_flags,
                    "comment": comment,
                }
            )
        return results

    def _build_recommendation(
        self,
        candidate_analysis: list[dict[str, Any]],
        best: str | None,
        question: str | None,
    ) -> dict[str, Any]:
        preferred = best
        rationale: list[str] = []

        if candidate_analysis:
            top = max(candidate_analysis, key=lambda c: c.get("score", 0))
            if not preferred:
                preferred = top.get("number")
            if top.get("number") == preferred:
                rationale.append("综合评分在候选集中相对领先")
                rationale.extend(f"信号契合：{s}" for s in top.get("aligned_signals", [])[:3])
                if top.get("risk_flags"):
                    rationale.append("需留意：" + "；".join(top.get("risk_flags", [])[:2]))

        if not rationale:
            rationale.append("候选样本不足，建议结合更多历史数据观察")

        if question:
            rationale.append(f"已结合问题「{question}」进行统计口径说明")

        return {
            "preferred": preferred,
            "rationale": rationale,
            "disclaimer": DISCLAIMER,
        }

    def _estimate_confidence(self, candidate_analysis: list[dict[str, Any]]) -> float:
        if not candidate_analysis:
            return 0.5
        best_score = max((c.get("score", 0) for c in candidate_analysis), default=0)
        confidence = 0.45 + (best_score / 100.0) * 0.35
        return round(min(0.85, max(0.4, confidence)), 2)

    def _top_digits(self, freq_map: dict[str, Any], limit: int = 3) -> list[int]:
        try:
            pairs = [(int(k), int(v)) for k, v in freq_map.items()]
        except (TypeError, ValueError):
            return []
        pairs.sort(key=lambda p: p[1], reverse=True)
        return [digit for digit, _ in pairs[:limit]]

    def _map_reasons_to_signals(self, reasons: list[str]) -> list[str]:
        signal_map = {
            "热号": "位频率",
            "遗漏": "遗漏值",
            "和值": "和值区间",
            "奇偶": "奇偶结构",
            "复用": "历史重复模式",
        }
        signals: list[str] = []
        for reason in reasons:
            for keyword, signal in signal_map.items():
                if keyword in reason and signal not in signals:
                    signals.append(signal)
        return signals

    def _risk_flags(self, number: str, reasons: list[str], average: Any) -> list[str]:
        flags: list[str] = []
        if any("降权" in r or "已出现" in r for r in reasons):
            flags.append("与近期开奖号码相似，统计上重复概率降低")
        if len(number) == 3 and len(set(number)) == 1:
            flags.append("三位数字相同（豹子号），历史出现概率较低")
        return flags

    def _comment(
        self, number: str, score: Any, aligned_signals: list[str], risk_flags: list[str]
    ) -> str:
        parts = [f"候选 {number} 综合评分 {score}"]
        if aligned_signals:
            parts.append("契合信号：" + "、".join(aligned_signals))
        if risk_flags:
            parts.append("风险提示：" + "；".join(risk_flags))
        parts.append("该结果为统计分析与概率模型解释，不构成中奖承诺")
        return "，".join(parts)


def assert_no_forbidden_language(text: str) -> None:
    """Defensive guard: raise if disallowed win-guarantee phrases appear."""
    for phrase in FORBIDDEN_PHRASES:
        if phrase in text:
            raise ValueError(f"forbidden phrase detected: {phrase}")
