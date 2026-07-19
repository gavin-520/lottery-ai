from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers import predict

app = FastAPI(title="Lottery AI Service", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(predict.router)


@app.get("/health")
def health():
    return {"status": "OK", "service": "lottery-ai-service"}
