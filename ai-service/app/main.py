from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers import predict
from app.services.kafka_consumer import kafka_status, start_kafka_consumer


@asynccontextmanager
async def lifespan(app: FastAPI):
    start_kafka_consumer()
    yield


app = FastAPI(title="Lottery AI Service", version="0.1.0-SNAPSHOT", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(predict.router)


@app.get("/health")
def health():
    ks = kafka_status()
    return {
        "status": "OK",
        "service": "lottery-ai-service",
        "kafka": ks,
    }
