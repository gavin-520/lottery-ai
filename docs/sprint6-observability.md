# Sprint 6 — Observability & Resilience

## Overview

Sprint 6 completes the observability loop started in Sprint 4/5: failed syncs are actionable, SLA breaches are recorded, and correlation IDs tie events together.

## Features

### SyncFailedEvent Avro Schema

Dedicated schema for `lottery.sync.failed` topic (previously encoded as SyncCompletedEvent).

### SLA SLO Alerting

| Env | Default | Description |
|-----|---------|-------------|
| `SLA_ALERT_ENABLED` | true | Record breaches |
| `SLA_MIN_SUCCESS_RATE` | 95 | Min success % |
| `SLA_MAX_P95_MS` | 800 | Max P95 latency |

Breaches stored in `sla_breach_log`.

### Sync Operations

| API | Description |
|-----|-------------|
| `GET /api/v1/admin/sync/logs` | Paginated sync history |
| `POST /api/v1/admin/sync/retry` | Retry sync |
| `GET /api/v1/admin/sync/regions` | Cross-region stats |

### Correlation Tracing

`GET /api/v1/admin/trace/{correlationId}` returns:
- Platform events
- Sync logs
- SLA logs
- SLA breaches

### AI Service Degraded Mode

When `lottery.sync.failed` is consumed, `/health` reports `kafka.degraded: true`.

## Test Flow

```bash
# Terminal 1: fault-injecting mock API
python scripts/mock-external-api.py --429

# Terminal 2: set external feed + trigger sync via Admin UI or API
# Expected: sync FAILED, sla_breach_log entry, sync.failed Kafka event
```

## Database Migration

```bash
python scripts/init-db-local.py
# Or manually: mysql < database/init/008_sprint6.sql
```
