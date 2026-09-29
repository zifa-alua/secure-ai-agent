package com.example.agent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Stats {

    private static final Map<String, AtomicLong> toolCalls = new ConcurrentHashMap<>();
    private static final AtomicLong errors = new AtomicLong();

    public static void countTool(String tool) {
        toolCalls.computeIfAbsent(tool, k -> new AtomicLong()).incrementAndGet();
    }

    public static void countError() {
        errors.incrementAndGet();
    }

    /** Снимок статистики в виде JSON. */
    public static String snapshotJson() {
        StringBuilder sb = new StringBuilder("{\"tool_calls\":{");
        boolean first = true;
        for (Map.Entry<String, AtomicLong> e : toolCalls.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":").append(e.getValue().get());
            first = false;
        }
        sb.append("},\"errors\":").append(errors.get()).append("}");
        return sb.toString();
    }
}
