# Lottery AI Platform

企业级 AI 数据分析平台 — 彩票研究所

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 17, Spring Boot 3, MyBatis Plus, Spring Security, JWT |
| Frontend | Vue 3, Vite, TypeScript, Element Plus, Pinia |
| AI Service | Python 3.11+, FastAPI |
| Database | MySQL 8 |
| Cache | Redis 7 |
| Deployment | Docker Compose |

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

## 目录结构

```
lottery-ai/
├── backend/          # Spring Boot API (8080)
├── frontend/         # Vue SPA (5173)
├── ai-service/       # FastAPI (8000)
├── database/init/    # SQL 初始化
├── docker/           # Dockerfiles
├── docs/             # 架构文档
└── scripts/          # 启动脚本
```

详见 [docs/architecture.md](docs/architecture.md)
