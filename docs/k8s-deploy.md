# Kubernetes Deployment

## Prerequisites

- kubectl configured
- NGINX Ingress Controller
- Docker images built and available to cluster

## Build Images

```powershell
docker compose build
docker tag lottery-ai-backend lottery-backend:latest
docker tag lottery-ai-ai-service lottery-ai-service:latest
docker tag lottery-ai-frontend lottery-frontend:latest
```

## Deploy

```powershell
# 1. Create secrets (edit values first)
Copy-Item k8s/secrets.example.yaml k8s/secrets.yaml
kubectl apply -f k8s/secrets.yaml

# 2. Apply manifests
kubectl apply -k k8s/

# 3. Verify
kubectl get pods -n lottery-ai
```

## Access

Add to hosts file: `127.0.0.1 lottery-ai.local`

Open: http://lottery-ai.local

## Notes

- MySQL init SQL requires mounting `database/init` as ConfigMap `lottery-mysql-init` before production use
- Scale backend: `kubectl scale deployment backend -n lottery-ai --replicas=3`
