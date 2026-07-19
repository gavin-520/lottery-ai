#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example"
fi

docker compose up -d --build
echo ""
echo "Services starting..."
echo "  Frontend:  http://localhost:${FRONTEND_PORT:-5173}"
echo "  Backend:   http://localhost:${BACKEND_PORT:-8080}/actuator/health"
echo "  AI Service: http://localhost:${AI_SERVICE_PORT:-8000}/health"
echo "  Login:     admin / admin123"
