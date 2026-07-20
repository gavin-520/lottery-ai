#!/usr/bin/env python3
"""Import full FC3D history into fc3d_draw_record.

Primary source: http://data.17500.cn/3d_desc.txt (2002 ~ latest)
Fallback / cross-check: GitHub CWL official mirror (2013+)
"""

from __future__ import annotations

import csv
import json
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT / "data"
JSON_PATH = DATA_DIR / "fc3d_history_official.json"
CSV_PATH = DATA_DIR / "fc3d_history_official.csv"
SQL_PATH = DATA_DIR / "fc3d_history_import.sql"

SOURCE_17500 = "http://data.17500.cn/3d_desc.txt"
GITHUB_URL = (
    "https://raw.githubusercontent.com/Aioneas/fucai3d-research/"
    "main/data/all/history_official_all_full.json"
)


def oe(digit: int) -> str:
    return "O" if digit % 2 else "E"


def make_row(issue: str, d1: int, d2: int, d3: int, draw_date: str | None) -> dict:
    return {
        "issue": issue,
        "digit1": d1,
        "digit2": d2,
        "digit3": d3,
        "sum_value": d1 + d2 + d3,
        "span_value": max(d1, d2, d3) - min(d1, d2, d3),
        "odd_even_pattern": oe(d1) + oe(d2) + oe(d3),
        "draw_date": draw_date if draw_date and len(draw_date) >= 10 else None,
    }


def http_get_bytes(url: str) -> bytes:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) LotteryAI/1.0",
            "Accept": "*/*",
        },
    )
    with urllib.request.urlopen(req, timeout=180) as resp:
        return resp.read()


def fetch_17500() -> dict[str, dict]:
    print(f"Downloading {SOURCE_17500}")
    text = http_get_bytes(SOURCE_17500).decode("utf-8", errors="ignore")
    rows: dict[str, dict] = {}
    for line in text.splitlines():
        parts = line.strip().split()
        if len(parts) < 5:
            continue
        issue, date, d1, d2, d3 = parts[0], parts[1], parts[2], parts[3], parts[4]
        if not issue.isdigit():
            continue
        try:
            rows[issue] = make_row(issue, int(d1), int(d2), int(d3), date)
        except ValueError:
            continue
    print(f"17500 rows: {len(rows)}")
    return rows


def fetch_github_fill(existing: dict[str, dict]) -> None:
    try:
        print(f"Downloading {GITHUB_URL}")
        data = json.loads(http_get_bytes(GITHUB_URL).decode("utf-8"))
        added = 0
        for item in data:
            issue = str(item.get("issue") or "").strip()
            if not issue or issue in existing:
                continue
            digits = item.get("digits")
            if not digits:
                continue
            date = str(item.get("date") or "")[:10]
            existing[issue] = make_row(
                issue, int(digits[0]), int(digits[1]), int(digits[2]), date
            )
            added += 1
        print(f"GitHub filled missing rows: {added}")
    except Exception as exc:
        print(f"GitHub fill skipped: {exc}")


def write_artifacts(rows: list[dict]) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with JSON_PATH.open("w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False)

    with CSV_PATH.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=[
                "issue",
                "digit1",
                "digit2",
                "digit3",
                "sum_value",
                "span_value",
                "odd_even_pattern",
                "draw_date",
            ],
        )
        writer.writeheader()
        writer.writerows(rows)

    with SQL_PATH.open("w", encoding="utf-8") as f:
        f.write("SET NAMES utf8mb4;\n")
        f.write("START TRANSACTION;\n")
        f.write("DELETE FROM fc3d_draw_record;\n")
        batch: list[str] = []
        for i, row in enumerate(rows, 1):
            date_sql = f"'{row['draw_date']}'" if row["draw_date"] else "NULL"
            batch.append(
                "('{issue}',{d1},{d2},{d3},{s},{sp},'{oe}',{dt},'FC3D')".format(
                    issue=row["issue"].replace("'", ""),
                    d1=row["digit1"],
                    d2=row["digit2"],
                    d3=row["digit3"],
                    s=row["sum_value"],
                    sp=row["span_value"],
                    oe=row["odd_even_pattern"],
                    dt=date_sql,
                )
            )
            if len(batch) >= 300 or i == len(rows):
                f.write(
                    "INSERT INTO fc3d_draw_record "
                    "(issue,digit1,digit2,digit3,sum_value,span_value,odd_even_pattern,draw_date,lottery_type) VALUES\n"
                )
                f.write(",\n".join(batch))
                f.write(";\n")
                batch.clear()
        f.write("COMMIT;\n")
        f.write(
            "SELECT COUNT(*) AS total, MIN(issue) AS min_issue, MAX(issue) AS max_issue, "
            "MIN(draw_date) AS min_date, MAX(draw_date) AS max_date FROM fc3d_draw_record;\n"
        )
    print(f"Wrote {CSV_PATH}")
    print(f"Wrote {SQL_PATH}")


def import_via_docker() -> None:
    print("Importing via docker mysql ...")
    subprocess.run(
        ["docker", "cp", str(SQL_PATH), "lottery-mysql:/tmp/fc3d_history_import.sql"],
        check=True,
    )
    result = subprocess.run(
        [
            "docker",
            "exec",
            "lottery-mysql",
            "mysql",
            "-ulottery",
            "-plottery_pass",
            "--default-character-set=utf8mb4",
            "lottery_ai",
            "-e",
            "source /tmp/fc3d_history_import.sql",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    if result.stdout:
        print(result.stdout.strip())


def main() -> int:
    by_issue = fetch_17500()
    if not by_issue:
        print("Primary source empty", file=sys.stderr)
        return 1
    fetch_github_fill(by_issue)
    rows = sorted(by_issue.values(), key=lambda r: r["issue"])
    print(f"Total unique rows: {len(rows)}")
    print(f"Range: {rows[0]['issue']} ({rows[0]['draw_date']}) ~ {rows[-1]['issue']} ({rows[-1]['draw_date']})")
    write_artifacts(rows)
    import_via_docker()
    try:
        subprocess.run(["docker", "exec", "lottery-redis", "redis-cli", "FLUSHALL"], check=False)
    except Exception:
        pass
    print("Done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
