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

## Sprint 1 Scope

- Rule engine (hot/cold, missing, sum, span, odd/even) for SSQ red/blue balls
- Backend APIs: predict (rules/AI/hybrid), backtest, analytics frequency
- AI Service: XGBoost training pipeline with statistical fallback
- Frontend: predict page, backtest page, ECharts analytics
- Extended seed data for walk-forward backtest
- GitHub Actions CI pipeline

## Sprint 2 Scope

- RBAC: ADMIN / ANALYST / USER role-based access control
- Admin: user management + CSV data import pipeline
- AI Service: LightGBM model + XGBoost/LGBM ensemble + model compare API
- LangChain Agent: analysis endpoint (OpenAI optional, rule-based fallback)
- Frontend: AI assistant, model compare, admin panel

## Sprint 3 Scope

- Real-time data sync scheduler (mock / HTTP feed adapter)
- SSE event stream for live sync notifications
- LangChain multi-agent workflow (Analyst → Reviewer → Reporter)
- Kubernetes production manifests (k8s/)

## Sprint 4 Scope

- Kafka event bus (`lottery.sync.completed`, `lottery.predict.created`)
- Platform event audit log (`platform_event` table)
- External API feed adapter (`SYNC_FEED_TYPE=external`)
- AI Service Kafka consumer
- K8s HA: HPA + PodDisruptionBudget + Kafka

## Sprint 5 Scope

- Avro event serialization + Confluent Schema Registry
- Multi-region Kustomize overlays (`k8s/overlays/region-a`, `region-b`)
- External API SLA logging (retry, timeout, latency metrics)
- `lottery.sync.failed` Kafka topic for failed sync events
- Frontend: region badge, Avro indicator, API SLA dashboard

## Sprint 6 Scope

- `SyncFailedEvent` Avro schema + topic encoding fix
- SLA SLO thresholds + breach alerting (`sla_breach_log`)
- Sync ops: history, retry, region/correlation metadata
- Correlation tracing API (`GET /admin/trace/{correlationId}`)
- AI Service degraded mode on sync failures
- Frontend: SyncView, SLA breaches, event filters

## Sprint 7 Scope

- `SlaBreachEvent` Kafka topic + SSE push notifications
- Redis cache for history/analytics (invalidate on sync)
- Prometheus metrics (`/actuator/prometheus`)
- Sync retry by log ID with `parent_log_id` lineage
- Request-scoped `X-Correlation-Id` propagation
- AI ensemble blocked (503) in degraded mode
- Frontend: OpsView cross-region dashboard

## Sprint 8+ (Out of Scope)

- Managed multi-region Kafka (MirrorMaker 2) automation
- Real third-party lottery API contractual SLA
- Geo-DNS / global load balancer provisioning
