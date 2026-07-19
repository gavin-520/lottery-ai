# Lottery AI Platform

企业级 AI 数据分析平台 — 彩票研究所

## Version

当前开发基线：**0.1.0-SNAPSHOT**（v1.0 开发中，尚未发布）

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 17, Spring Boot 3, MyBatis Plus, Spring Security, JWT |
| Frontend | Vue 3, Vite, TypeScript, Element Plus, Pinia, ECharts |
| AI Service | Python 3.11+, FastAPI, XGBoost, LightGBM, LangChain |
| Database | MySQL 8 |
| Cache | Redis 7 |
| Message Queue | Apache Kafka 3.9 + Confluent Schema Registry (Avro 可选) |
| Observability | Spring Actuator, Prometheus, Correlation ID |
| Deployment | Docker Compose · Kubernetes (Kustomize 多区域) |

## Quick Start

### 1. 环境准备

- Docker & Docker Compose
- （本地开发可选）JDK 17、Node 20、Python 3.11

### 2. 一键启动

```bash
cp .env.example .env
docker compose up -d --build
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
.\scripts\start.ps1
```

### 3. 访问地址

| 服务 | URL |
|------|-----|
| Frontend | http://localhost:5173 |
| Backend Health | http://localhost:8080/actuator/health |
| AI Service Health | http://localhost:8000/health |
| Backend API | http://localhost:8080/api/v1/ |

**默认账号：** `admin` / `admin123`

### 4. 本地开发（不用 Docker 跑应用）

```bash
# 启动中间件
docker compose up -d mysql redis

# Backend
cd backend && mvn spring-boot:run

# Frontend
cd frontend && npm install && npm run dev

# AI Service
cd ai-service && pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

## Sprint 0 验收清单

- [x] `docker compose up -d` 启动全栈
- [x] 前端可访问，登录获取 JWT
- [x] `GET /api/v1/history` 返回历史数据
- [x] AI Service `/health` 与 mock `/api/v1/predict`
- [x] 配置走 `.env`，无硬编码密码

## Sprint 1 功能

| 模块 | 功能 |
|------|------|
| 规则引擎 | 冷热号、遗漏、和值、跨度、奇偶比 |
| 预测 | `GET /api/v1/predict/rules` · `/predict/ai` · `POST /api/v1/predict` |
| 回测 | `POST /api/v1/backtest/run` |
| 分析 | `GET /api/v1/analytics/frequency` |
| AI | XGBoost 多标签红球 + 蓝球分类 |
| 前端 | 数据分析(ECharts) · 智能预测 · 回测 |

> 若数据库已初始化，需手动执行 `database/init/003_seed_extended.sql` 或重建 MySQL volume 以获取回测所需历史数据（≥50 期）。

## Sprint 2 功能

| 模块 | 功能 |
|------|------|
| RBAC | ADMIN / ANALYST / USER 三级权限 |
| 系统管理 | 用户创建、CSV 数据导入 |
| AI 助手 | LangChain 分析（可选 OpenAI） |
| 模型对比 | XGBoost vs LightGBM vs Ensemble |
| 默认账号 | admin/admin123 · analyst/analyst123 · user/user123 |

### 角色权限

| 角色 | 权限 |
|------|------|
| ADMIN | 全部功能 + 用户管理 + 数据导入 |
| ANALYST | 预测、回测、分析、AI 助手、模型对比 |
| USER | 历史查询、数据分析 |

## Sprint 3 功能

| 模块 | 功能 |
|------|------|
| 数据同步 | 定时 Mock Feed / HTTP Feed，Admin 手动触发 |
| 实时推送 | SSE `/api/v1/events/stream` |
| 多 Agent | Analyst → Reviewer → Reporter 工作流 |
| K8s | `k8s/` 生产部署 manifests |

详见 [docs/k8s-deploy.md](docs/k8s-deploy.md)

### 同步配置 (.env)

```env
SYNC_ENABLED=true
SYNC_CRON=0 */30 * * * *
SYNC_FEED_TYPE=mock          # mock | http
SYNC_FEED_URL=               # HTTP feed JSON URL (when feed-type=http)
```

## Sprint 4 功能

| 模块 | 功能 |
|------|------|
| Kafka | 同步/预测事件发布，AI Service 消费 |
| 事件审计 | `platform_event` 表 + `/api/v1/admin/events` |
| External Feed | `SYNC_FEED_TYPE=external` + API Key 认证 |
| K8s HA | HPA、PDB、Kafka 部署 |

详见 [docs/ha-deploy.md](docs/ha-deploy.md)

### Kafka Topics

- `lottery.sync.completed` — 数据同步完成
- `lottery.predict.created` — 预测记录创建

### 本地测试 External API

```bash
python scripts/mock-external-api.py   # http://localhost:8090
# 设置 SYNC_FEED_TYPE=external, SYNC_EXTERNAL_URL=http://host.docker.internal:8090
```

## Sprint 5 功能

| 模块 | 功能 |
|------|------|
| Avro | Schema Registry + Avro 事件序列化 (`KAFKA_USE_AVRO=true`) |
| 多区域 | Kustomize overlays `region-a` / `region-b`，事件带 `region` 标签 |
| API SLA | 外部 API 重试/超时/延迟监控 + Admin SLA 面板 |
| 失败事件 | `lottery.sync.failed` topic + 同步失败状态 |

详见 [docs/multi-region-deploy.md](docs/multi-region-deploy.md)

### Avro 配置

```env
KAFKA_USE_AVRO=true
KAFKA_SCHEMA_REGISTRY_URL=http://schema-registry:8081
LOTTERY_REGION=ap-east-1
```

### SLA 测试 Mock API

```bash
python scripts/mock-external-api.py --latency 500 --error-rate 0.3
python scripts/mock-external-api.py --429
```

## Sprint 6 功能

| 模块 | 功能 |
|------|------|
| Avro 完善 | `SyncFailedEvent.avsc` 独立 schema |
| SLA 告警 | SLO 阈值 breach + Admin 告警列表 |
| 同步运维 | 历史记录、重试、FAILED 状态追踪 |
| 链路追踪 | Correlation ID 跨事件/同步/SLA 查询 |
| AI 降级 | sync.failed 触发 degraded 状态 |

详见 [docs/sprint6-observability.md](docs/sprint6-observability.md)

## Sprint 7 功能

| 模块 | 功能 |
|------|------|
| SLA 告警闭环 | `lottery.sla.breach` Kafka + SSE 实时推送 |
| Redis 缓存 | 历史/分析查询缓存，同步成功后失效 |
| Prometheus | sync/SLA 指标暴露 `/actuator/prometheus` |
| 精准重试 | `POST /admin/sync/retry/{logId}` + parent_log_id |
| 链路 ID | `X-Correlation-Id` 全链路传播 |
| AI 降级 | ensemble/compare 503，predict 规则降级 |
| 运维大盘 | `/ops` 跨区域 SLA + 失败统计 |

详见 [docs/sprint7-resilience.md](docs/sprint7-resilience.md)

### Sprint 7 配置

```env
KAFKA_TOPIC_SLA_BREACH=lottery.sla.breach
```

### SLA SLO 配置

```env
SLA_ALERT_ENABLED=true
SLA_MIN_SUCCESS_RATE=95
SLA_MAX_P95_MS=800
```

## 目录结构

```
lottery-ai/
├── backend/          # Spring Boot API (8080)
├── frontend/         # Vue SPA (5173)
├── ai-service/       # FastAPI ML 服务 (8000)
├── database/init/    # SQL 初始化 (001–010)
├── docker/           # Dockerfiles
├── k8s/              # Kubernetes manifests + region overlays
├── schemas/          # Avro 事件 schema
├── docs/             # 架构与部署文档
├── scripts/          # 启动与 Mock 工具
└── .github/workflows/# CI 流水线
```

详见 [docs/architecture.md](docs/architecture.md)
