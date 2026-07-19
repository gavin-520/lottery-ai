# Multi-Region Active-Active Deployment (Sprint 5)

## Overview

Sprint 5 adds Kustomize overlays for deploying the platform to multiple regions with region-tagged events and shared Kafka topic conventions.

## Regions

| Overlay | Region ID | Suffix |
|---------|-----------|--------|
| `k8s/overlays/region-a` | `ap-east-1` | `-ap` |
| `k8s/overlays/region-b` | `eu-west-1` | `-eu` |

Each overlay sets:

- `LOTTERY_REGION` — included in Kafka events and SLA logs
- `KAFKA_USE_AVRO=true` — Avro serialization via Schema Registry
- `KAFKA_SCHEMA_REGISTRY_URL` — regional schema registry service

## Deploy

```bash
# Region A (Asia Pacific)
kubectl apply -k k8s/overlays/region-a

# Region B (Europe)
kubectl apply -k k8s/overlays/region-b
```

## Active-Active Strategy

1. **Idempotent sync** — `lottery_history.period` unique constraint prevents duplicate inserts across regions
2. **Region-tagged events** — all Kafka payloads include `region` and `correlationId`
3. **Cross-region Kafka** — use MirrorMaker 2 or managed multi-region Kafka to replicate topics
4. **Read-local writes** — each region syncs from external API independently; conflicts resolved by period uniqueness

## Schema Registry

Avro schemas live in `schemas/`:

- `SyncCompletedEvent.avsc`
- `PredictCreatedEvent.avsc`

Enable Avro:

```env
KAFKA_USE_AVRO=true
KAFKA_SCHEMA_REGISTRY_URL=http://schema-registry:8081
```

## External API SLA

Configure resilient external feed:

```env
SYNC_FEED_TYPE=external
SYNC_EXTERNAL_URL=http://host.docker.internal:8090
SYNC_EXTERNAL_MAX_RETRIES=3
SYNC_EXTERNAL_TIMEOUT_MS=5000
```

Test with fault injection:

```bash
python scripts/mock-external-api.py --latency 800 --error-rate 0.2
python scripts/mock-external-api.py --429
```

View SLA metrics at **Admin → API SLA** or `GET /api/v1/admin/sla/summary`.
