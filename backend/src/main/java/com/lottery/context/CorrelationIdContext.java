package com.lottery.context;

import java.util.UUID;

public final class CorrelationIdContext {

    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();

    private CorrelationIdContext() {
    }

    public static void set(String correlationId) {
        CORRELATION_ID.set(correlationId);
    }

    public static String get() {
        return CORRELATION_ID.get();
    }

    public static void clear() {
        CORRELATION_ID.remove();
    }

    public static String getOrGenerate() {
        String id = get();
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
            set(id);
        }
        return id;
    }
}
