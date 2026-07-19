"""Initialize local MySQL for lottery-ai (no Docker)."""
from __future__ import annotations

import pathlib
import re
import sys

import pymysql

ROOT = pathlib.Path(__file__).resolve().parents[1]
INIT_DIR = ROOT / "database" / "init"


def load_env() -> dict[str, str]:
    env: dict[str, str] = {}
    env_file = ROOT / ".env"
    if not env_file.exists():
        return env
    for line in env_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        env[key.strip()] = value.strip()
    return env


def split_statements(sql: str) -> list[str]:
    statements: list[str] = []
    buffer: list[str] = []
    for line in sql.splitlines():
        stripped = line.strip()
        if stripped.startswith("--"):
            continue
        buffer.append(line)
        if stripped.endswith(";"):
            stmt = "\n".join(buffer).strip()
            if stmt:
                statements.append(stmt)
            buffer = []
    if buffer:
        stmt = "\n".join(buffer).strip()
        if stmt:
            statements.append(stmt)
    return statements


def main() -> int:
    env = load_env()
    host = "127.0.0.1"
    port = int(env.get("MYSQL_PORT", "3306"))
    user = env.get("MYSQL_USER", "root")
    password = env.get("MYSQL_PASSWORD", "lottery_pass")
    database = env.get("MYSQL_DATABASE", "lottery_ai")

    conn = pymysql.connect(
        host=host,
        port=port,
        user=user,
        password=password,
        charset="utf8mb4",
        autocommit=True,
    )
    cur = conn.cursor()
    cur.execute(f"CREATE DATABASE IF NOT EXISTS `{database}` DEFAULT CHARACTER SET utf8mb4")
    cur.execute(f"USE `{database}`")

    for path in sorted(INIT_DIR.glob("*.sql")):
        print(f"Running {path.name}...")
        for stmt in split_statements(path.read_text(encoding="utf-8")):
            try:
                cur.execute(stmt)
            except pymysql.err.OperationalError as exc:
                code = exc.args[0]
                if code in (1050, 1061, 1062):
                    print(f"  skip duplicate: {exc.args[1]}")
                else:
                    raise

    cur.close()
    conn.close()
    print(f"Database `{database}` initialized.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
