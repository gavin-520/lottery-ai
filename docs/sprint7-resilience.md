# Sprint 7 — Operational Resilience & Alerting

## Overview

Sprint 7 closes operational loops opened in Sprint 6: SLA breaches become real-time events, sync retries are traceable, Redis caching reduces DB load, and Prometheus metrics expose platform health.

## Features

### SlaBreachEvent Kafka Topic

| Env | Default |
|-----|---------|
| `KAFKA_TOPIC_SLA_BREACH` | `lottery.sla.breach` |

Avro schema: `schemas/SlaBreachEvent.avsc`. Published when an SLO breach is recorded.

### SSE Breach Notifications

Event name: `sla-breach`. Pushed to Dashboard and SLA page when breach occurs.

### AI Degraded Predict Mode

When `lottery.sync.failed` is consumed:
- `/predict` continues with statistical fallback + degraded note
- `/predict/ensemble` and `/models/compare` return **503**

### Redis Cache

| Cache | Key | Invalidated on |
|-------|-----|----------------|
| `historyPage` | page-size | sync success |
| `historyAll` | — | sync success |
| `analytics` | — | sync success |

### Prometheus Metrics

Endpoint: `GET /actuator/prometheus`

| Metric | Description |
|--------|-------------|
| `lottery.sync.success` | Successful sync counter |
| `lottery.sync.failed` | Failed sync counter |
| `lottery.sync.duration` | Sync duration timer |
| `lottery.sla.breaches` | SLA breach counter |

### Sync Retry by Log ID

```
POST /api/v1/admin/sync/retry/{logId}
```

Only retries logs in `FAILED` status. New log stores `parent_log_id` linking to original failure.

### Correlation ID Propagation

Filter reads/generates `X-Correlation-Id` header, propagates to:
- Sync jobs
- Predict Kafka events
- AI service HTTP calls

### Ops Dashboard

`GET /api/v1/admin/ops/overview` — cross-region SLA + breach/sync failure counts.

Frontend: `/ops` (Admin only).

## Database Migration

```bash
python scripts/init-db-local.py
# Or: mysql < database/init/009_sprint7.sql
```

## Test Flow

```bash
# Terminal 1: fault injection
python scripts/mock-external-api.py --429

# Terminal 2: set SYNC_FEED_TYPE=external, trigger sync
# Expected: sla-breach SSE, Kafka event, AI degraded, ensemble 503

# Retry failed log
curl -X POST http://localhost:8080/api/v1/admin/sync/retry/1 -H "Authorization: Bearer $TOKEN"
```
