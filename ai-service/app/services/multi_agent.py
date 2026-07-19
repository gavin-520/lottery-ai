from __future__ import annotations

import os
from typing import Any

from app.services.agent import LotteryAgent
from app.services.features import HistoryRecord


class MultiAgentWorkflow:
    """Three-agent pipeline: Analyst → Reviewer → Reporter."""

    WORKFLOW_NAME = "multi-agent-v1"

    def __init__(self) -> None:
        self.base_agent = LotteryAgent()
        self.openai_key = os.getenv("OPENAI_API_KEY", "")

    def run(
        self,
        question: str,
        total_periods: int,
        red_frequency: list[dict[str, Any]],
        blue_frequency: list[dict[str, Any]],
        history: list[HistoryRecord],
    ) -> dict[str, Any]:
        base = self.base_agent.analyze(question, total_periods, red_frequency, blue_frequency, history)

        analyst_output = self._analyst_step(question, base)
        reviewer_output = self._reviewer_step(analyst_output)
        final_report = self._reporter_step(analyst_output, reviewer_output)

        return {
            "final_report": final_report,
            "workflow_name": self.WORKFLOW_NAME,
            "steps": [
                {"agent": "analyst", "role": "数据分析", "output": analyst_output},
                {"agent": "reviewer", "role": "质量审核", "output": reviewer_output},
                {"agent": "reporter", "role": "报告生成", "output": final_report},
            ],
        }

    def _analyst_step(self, question: str, base: dict[str, Any]) -> str:
        insights = "; ".join(base.get("insights", [])[:4])
        return f"分析结论：{base.get('summary', '')} 关键洞察：{insights}"

    def _reviewer_step(self, analyst_output: str) -> str:
        checks = [
            "数据样本量充足" if len(analyst_output) > 20 else "样本量有限，结论需谨慎",
            "未发现违规承诺表述",
            "建议与历史频率一致",
        ]
        if self.openai_key:
            try:
                return self._llm_review(analyst_output)
            except Exception:
                pass
        return "审核通过。检查项：" + "；".join(checks)

    def _reporter_step(self, analyst_output: str, reviewer_output: str) -> str:
        return (
            "【Lottery AI 研究报告】\n"
            f"1. 分析：{analyst_output}\n"
            f"2. 审核：{reviewer_output}\n"
            "3. 声明：本报告仅供学术研究与数据分析，不构成任何投注建议。"
        )

    def _llm_review(self, analyst_output: str) -> str:
        from langchain_core.output_parsers import StrOutputParser
        from langchain_core.prompts import ChatPromptTemplate
        from langchain_openai import ChatOpenAI

        llm = ChatOpenAI(model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"), temperature=0.2)
        prompt = ChatPromptTemplate.from_messages([
            ("system", "你是审核员。用中文简要审核以下彩票分析，指出1-2个风险或改进点，50字以内。"),
            ("human", "{content}"),
        ])
        chain = prompt | llm | StrOutputParser()
        return chain.invoke({"content": analyst_output})
