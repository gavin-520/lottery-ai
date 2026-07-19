"""Minimal mock external lottery API for Sprint 4/5 testing."""
from __future__ import annotations

import argparse
import json
import random
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

DATA = json.loads(Path(__file__).with_name("sample-external-feed.json").read_text(encoding="utf-8"))

LATENCY_MS = 0
ERROR_RATE = 0.0
FORCE_429 = False


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if LATENCY_MS > 0:
            time.sleep(LATENCY_MS / 1000.0)

        if FORCE_429:
            self.send_response(429)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"error":"rate limit"}')
            return

        if ERROR_RATE > 0 and random.random() < ERROR_RATE:
            self.send_response(503)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"error":"upstream unavailable"}')
            return

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(DATA).encode())

    def log_message(self, format, *args):
        return


def main() -> None:
    global LATENCY_MS, ERROR_RATE, FORCE_429
    parser = argparse.ArgumentParser(description="Mock external lottery API")
    parser.add_argument("--port", type=int, default=8090)
    parser.add_argument("--latency", type=int, default=0, help="Artificial latency in ms")
    parser.add_argument("--error-rate", type=float, default=0.0, help="503 error probability 0-1")
    parser.add_argument("--429", action="store_true", help="Always return HTTP 429")
    args = parser.parse_args()
    LATENCY_MS = args.latency
    ERROR_RATE = max(0.0, min(1.0, args.error_rate))
    FORCE_429 = args._429
    print(f"Mock external feed: http://localhost:{args.port}/")
    print(f"  latency={LATENCY_MS}ms error_rate={ERROR_RATE} force_429={FORCE_429}")
    HTTPServer(("0.0.0.0", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
