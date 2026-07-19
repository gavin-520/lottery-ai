# High Availability Deployment

## Components

| Component | HA Strategy |
|-----------|-------------|
| Backend | 2+ replicas, HPA (2-6), PDB minAvailable=1 |
| Frontend | 2+ replicas, HPA (2-5), PDB minAvailable=1 |
| Kafka | KRaft single-node (dev); use Strimzi for prod |
| MySQL | Single PVC (dev); use RDS/Cloud SQL for prod |

## Apply HA Manifests

```bash
kubectl apply -k k8s/
kubectl get hpa -n lottery-ai
kubectl get pdb -n lottery-ai
```

## External API Feed Test

```bash
# Terminal 1: mock external API
python scripts/mock-external-api.py

# Terminal 2: configure backend
SYNC_FEED_TYPE=external
SYNC_EXTERNAL_URL=http://host.docker.internal:8090
docker compose up -d --build backend
```

Expected JSON format: see `scripts/sample-external-feed.json`
