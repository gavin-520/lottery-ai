package com.lottery.event;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class AvroEventEncoder {

    private static final byte MAGIC_BYTE = 0;

    private final Schema syncSchema;
    private final Schema syncFailedSchema;
    private final Schema predictSchema;
    private final Schema slaBreachSchema;
    private final Schema dlqSchema;

    public AvroEventEncoder() {
        this.syncSchema = loadSchema("schemas/SyncCompletedEvent.avsc");
        this.syncFailedSchema = loadSchema("schemas/SyncFailedEvent.avsc");
        this.predictSchema = loadSchema("schemas/PredictCreatedEvent.avsc");
        this.slaBreachSchema = loadSchema("schemas/SlaBreachEvent.avsc");
        this.dlqSchema = loadSchema("schemas/DeadLetterEvent.avsc");
    }

    public byte[] encodeSyncEvent(Map<String, Object> payload, int schemaId) {
        GenericRecord record = new GenericData.Record(syncSchema);
        record.put("type", stringVal(payload.get("type")));
        record.put("source", stringVal(payload.get("source")));
        record.put("newCount", intVal(payload.get("newCount")));
        record.put("fetchedCount", intVal(payload.get("fetchedCount")));
        record.put("status", stringVal(payload.get("status")));
        record.put("message", stringVal(payload.get("message")));
        record.put("region", stringVal(payload.get("region")));
        record.put("correlationId", stringVal(payload.get("correlationId")));
        record.put("schemaVersion", stringVal(payload.get("schemaVersion")));
        record.put("timestamp", longVal(payload.get("timestamp")));
        return encodeWithSchemaId(record, syncSchema, schemaId);
    }

    public byte[] encodePredictEvent(Map<String, Object> payload, int schemaId) {
        GenericRecord record = new GenericData.Record(predictSchema);
        record.put("type", stringVal(payload.get("type")));
        record.put("period", stringVal(payload.get("period")));
        record.put("modelName", stringVal(payload.get("modelName")));
        record.put("confidence", doubleVal(payload.get("confidence")));
        record.put("region", stringVal(payload.get("region")));
        record.put("correlationId", stringVal(payload.get("correlationId")));
        record.put("schemaVersion", stringVal(payload.get("schemaVersion")));
        record.put("timestamp", longVal(payload.get("timestamp")));
        return encodeWithSchemaId(record, predictSchema, schemaId);
    }

    public byte[] encodeSyncFailedEvent(Map<String, Object> payload, int schemaId) {
        GenericRecord record = new GenericData.Record(syncFailedSchema);
        record.put("type", stringVal(payload.get("type")));
        record.put("source", stringVal(payload.get("source")));
        record.put("status", stringVal(payload.get("status")));
        record.put("message", stringVal(payload.get("message")));
        record.put("errorType", stringVal(payload.get("errorType")));
        record.put("httpStatus", intVal(payload.get("httpStatus")));
        record.put("region", stringVal(payload.get("region")));
        record.put("correlationId", stringVal(payload.get("correlationId")));
        record.put("schemaVersion", stringVal(payload.get("schemaVersion")));
        record.put("timestamp", longVal(payload.get("timestamp")));
        return encodeWithSchemaId(record, syncFailedSchema, schemaId);
    }

    public byte[] encodeSlaBreachEvent(Map<String, Object> payload, int schemaId) {
        GenericRecord record = new GenericData.Record(slaBreachSchema);
        record.put("type", stringVal(payload.get("type")));
        record.put("breachId", longVal(payload.get("breachId")));
        record.put("metric", stringVal(payload.get("metric")));
        record.put("thresholdValue", doubleVal(payload.get("thresholdValue")));
        record.put("actualValue", doubleVal(payload.get("actualValue")));
        record.put("severity", stringVal(payload.get("severity")));
        record.put("region", stringVal(payload.get("region")));
        record.put("correlationId", stringVal(payload.get("correlationId")));
        record.put("message", stringVal(payload.get("message")));
        record.put("schemaVersion", stringVal(payload.get("schemaVersion")));
        record.put("timestamp", longVal(payload.get("timestamp")));
        return encodeWithSchemaId(record, slaBreachSchema, schemaId);
    }

    public Schema slaBreachSchema() {
        return slaBreachSchema;
    }

    public Schema dlqSchema() {
        return dlqSchema;
    }

    public byte[] encodeDlqEvent(Map<String, Object> payload, int schemaId) {
        GenericRecord record = new GenericData.Record(dlqSchema);
        record.put("originalTopic", stringVal(payload.get("originalTopic")));
        record.put("eventType", stringVal(payload.get("eventType")));
        record.put("payloadJson", stringVal(payload.get("payloadJson")));
        record.put("errorMessage", stringVal(payload.get("errorMessage")));
        record.put("region", stringVal(payload.get("region")));
        record.put("correlationId", stringVal(payload.get("correlationId")));
        record.put("schemaVersion", stringVal(payload.get("schemaVersion")));
        record.put("timestamp", longVal(payload.get("timestamp")));
        return encodeWithSchemaId(record, dlqSchema, schemaId);
    }

    public Schema syncFailedSchema() {
        return syncFailedSchema;
    }

    public Schema syncSchema() {
        return syncSchema;
    }

    public Schema predictSchema() {
        return predictSchema;
    }

    private byte[] encodeWithSchemaId(GenericRecord record, Schema schema, int schemaId) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(MAGIC_BYTE);
            out.write(ByteBuffer.allocate(4).putInt(schemaId).array());
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            org.apache.avro.io.DatumWriter<GenericRecord> writer =
                    new org.apache.avro.generic.GenericDatumWriter<>(schema);
            writer.write(record, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Avro encode failed", ex);
        }
    }

    private Schema loadSchema(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new Schema.Parser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Missing Avro schema: " + path, ex);
        }
    }

    private String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intVal(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private long longVal(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return System.currentTimeMillis();
    }

    private double doubleVal(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }
}
