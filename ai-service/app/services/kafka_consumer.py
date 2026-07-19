import os
import threading
from io import BytesIO
from pathlib import Path
from typing import Any

_consumer_thread: threading.Thread | None = None
_last_event: dict[str, Any] | None = None
_kafka_running = False
_degraded = False
_schema_version = os.getenv("KAFKA_SCHEMA_VERSION", "1.0")
_use_avro = os.getenv("KAFKA_USE_AVRO", "false").lower() == "true"
_sync_failed_topic = os.getenv("KAFKA_TOPIC_SYNC_FAILED", "lottery.sync.failed")
_sla_breach_topic = os.getenv("KAFKA_TOPIC_SLA_BREACH", "lottery.sla.breach")


def _schema_path(name: str) -> Path:
    local = Path(__file__).resolve().parents[2] / "schemas" / name
    if local.exists():
        return local
    return Path(__file__).resolve().parents[3] / "schemas" / name


def _load_schema(name: str):
    from avro.schema import parse

    return parse(_schema_path(name).read_text(encoding="utf-8"))


def _decode_avro(payload: bytes, schema) -> dict[str, Any]:
    from avro.io import BinaryDecoder, DatumReader

    if len(payload) < 5:
        return {"raw": payload.hex()}
    body = payload[5:]
    reader = DatumReader(schema)
    decoder = BinaryDecoder(BytesIO(body))
    record = reader.read(decoder)
    return dict(record)


def _schema_for_topic(topic: str) -> str:
    if "predict" in topic:
        return "PredictCreatedEvent.avsc"
    if "breach" in topic:
        return "SlaBreachEvent.avsc"
    if "failed" in topic:
        return "SyncFailedEvent.avsc"
    return "SyncCompletedEvent.avsc"


def _decode_message(payload: bytes, topic: str) -> Any:
    if not _use_avro:
        import json
        return json.loads(payload.decode("utf-8"))
    schema = _load_schema(_schema_for_topic(topic))
    return _decode_avro(payload, schema)


def _handle_event(topic: str, value: dict[str, Any]) -> None:
    global _degraded
    if _sync_failed_topic in topic or "failed" in topic:
        _degraded = True
    elif "sync.completed" in topic or topic.endswith("sync.completed"):
        _degraded = False


def _consume_loop() -> None:
    global _last_event, _kafka_running
    try:
        from kafka import KafkaConsumer

        bootstrap = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
        topics = [
            os.getenv("KAFKA_TOPIC_SYNC", "lottery.sync.completed"),
            os.getenv("KAFKA_TOPIC_PREDICT", "lottery.predict.created"),
            _sync_failed_topic,
            _sla_breach_topic,
        ]
        consumer = KafkaConsumer(
            *topics,
            bootstrap_servers=bootstrap.split(","),
            auto_offset_reset="latest",
            group_id="lottery-ai-service",
        )
        _kafka_running = True
        for message in consumer:
            raw = message.value or b""
            decoded = _decode_message(raw, message.topic)
            _handle_event(message.topic, decoded if isinstance(decoded, dict) else {})
            _last_event = {
                "topic": message.topic,
                "key": message.key.decode() if message.key else None,
                "value": decoded,
                "encoding": "avro" if _use_avro else "json",
                "schemaVersion": _schema_version,
            }
    except Exception:
        _kafka_running = False


def start_kafka_consumer() -> None:
    global _consumer_thread
    if os.getenv("KAFKA_ENABLED", "false").lower() != "true":
        return
    if _consumer_thread and _consumer_thread.is_alive():
        return
    _consumer_thread = threading.Thread(target=_consume_loop, daemon=True)
    _consumer_thread.start()


def is_degraded() -> bool:
    return _degraded


def kafka_status() -> dict[str, Any]:
    return {
        "enabled": os.getenv("KAFKA_ENABLED", "false").lower() == "true",
        "running": _kafka_running,
        "useAvro": _use_avro,
        "schemaVersion": _schema_version,
        "degraded": _degraded,
        "last_event": _last_event,
    }
