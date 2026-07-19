# Lottery AI Platform — Architecture

## Overview

Enterprise-grade AI data analytics platform for lottery research. Monorepo with three services and shared infrastructure.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 17, Spring Boot 3, MyBatis Plus, Spring Security, JWT |
| Frontend | Vue 3, Vite, TypeScript, Element Plus, Pinia |
| AI Service | Python 3.11+, FastAPI, LangChain, XGBoost, LightGBM |
| Database | MySQL 8 |
| Cache | Redis 7 |
| Deployment | Docker Compose |

## Directory Structure

```
lottery-ai/
├── backend/          # Spring Boot API (port 8080)
├── frontend/         # Vue SPA (port 5173)
├── ai-service/       # FastAPI ML service (port 8000)
├── database/init/    # SQL migration scripts
├── docker/           # Dockerfiles
├── docs/             # Documentation
└── scripts/          # Utility scripts
```

## Service Communication

```
Browser → Frontend (5173)
              ↓ REST + JWT
         Backend (8080) → MySQL (3306)
              ↓              Redis (6379)
         AI Service (8000)
```

## Sprint 0 Scope

- Monorepo skeleton with Docker Compose
- Backend: login, history query, health check
- Frontend: login, dashboard, history list
- AI Service: health check, mock predict endpoint
- Database: core tables + seed data

## Sprint 1+ (Out of Scope)

- Rule engine / backtest logic
- Real XGBoost training
- LangChain agents
- Full RBAC
- ECharts visualization
- CI/CD pipeline
