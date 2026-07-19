from __future__ import annotations

import os
from typing import Any

from app.services.features import HistoryRecord


class LotteryAgent:
    """LangChain-powered analyst with rule-based fallback."""

    def __init__(self) -> None:
        self.openai_key = os.getenv("OPENAI_API_KEY", "")
        self.model_name = "rule-agent-v1"

    def analyze(
        self,
        question: str,
        total_periods: int,
        red_frequency: list[dict[str, Any]],
        blue_frequency: list[dict[str, Any]],
        history: list[HistoryRecord],
    ) -> dict[str, Any]:
        if self.openai_key:
            try:
                return self._analyze_with_langchain(question, total_periods, red_frequency, blue_frequency)
            except Exception:
                pass
        return self._analyze_rule_based(question, total_periods, red_frequency, blue_frequency, history)

    def _analyze_rule_based(
        self,
        question: str,
        total_periods: int,
        red_frequency: list[dict[str, Any]],
        blue_frequency: list[dict[str, Any]],
        history: list[HistoryRecord],
    ) -> dict[str, Any]:
        top_red = sorted(red_frequency, key=lambda x: x.get("count", 0), reverse=True)[:5]
        cold_red = sorted(red_frequency, key=lambda x: x.get("count", 0))[:5]
        top_blue = sorted(blue_frequency, key=lambda x: x.get("count", 0), reverse=True)[:3]

        insights = [
            f"热号红球 Top5: {', '.join(str(i['ball']) for i in top_red)}",
            f"冷号红球 Top5: {', '.join(str(i['ball']) for i in cold_red)}",
            f"热号蓝球 Top3: {', '.join(str(i['ball']) for i in top_blue)}",
            f"近期共 {len(history)} 期参与趋势分析",
        ]

        if history:
            last = history[-1]
            insights.append(f"最近一期 {last.period}: 红球 {last.red_balls}, 蓝球 {last.blue_ball}")

        recommendations = [
            "结合热号与遗漏值进行交叉验证",
            "关注奇偶比与和值区间的历史分布",
            "模型预测仅供研究参考，请理性分析",
        ]

        summary = (
            f"基于 {total_periods} 期数据的统计分析。"
            + (f" 您的问题：{question}" if question else "")
        )

        return {
            "summary": summary,
            "insights": insights,
            "recommendations": recommendations,
            "agent_name": self.model_name,
        }

    def _analyze_with_langchain(
        self,
        question: str,
        total_periods: int,
        red_frequency: list[dict[str, Any]],
        blue_frequency: list[dict[str, Any]],
    ) -> dict[str, Any]:
        from langchain_core.output_parsers import StrOutputParser
        from langchain_core.prompts import ChatPromptTemplate
        from langchain_openai import ChatOpenAI

        llm = ChatOpenAI(model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"), temperature=0.3)
        prompt = ChatPromptTemplate.from_messages([
            ("system", "你是彩票数据分析专家。基于频率数据给出简洁中文分析，3-5条洞察，2-3条建议。不要承诺中奖。"),
            ("human", "历史期数: {total_periods}\n红球频率Top10: {red_top}\n蓝球频率Top5: {blue_top}\n用户问题: {question}"),
        ])
        chain = prompt | llm | StrOutputParser()

        red_top = sorted(red_frequency, key=lambda x: x.get("count", 0), reverse=True)[:10]
        blue_top = sorted(blue_frequency, key=lambda x: x.get("count", 0), reverse=True)[:5]

        text = chain.invoke({
            "total_periods": total_periods,
            "red_top": red_top,
            "blue_top": blue_top,
            "question": question or "请给出整体趋势分析",
        })

        lines = [line.strip("- •\t ") for line in text.split("\n") if line.strip()]
        insights = [l for l in lines if l][:5]
        recommendations = insights[-2:] if len(insights) >= 2 else ["建议结合多模型对比结果综合判断"]

        return {
            "summary": lines[0] if lines else text[:200],
            "insights": insights,
            "recommendations": recommendations,
            "agent_name": "langchain-openai-v1",
        }
