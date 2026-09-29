package com.example.agent;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {

    private final int maxRequests;
    private final long windowSeconds;

    private static final class Window {
        int count;
        Instant resetAt;
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, long windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    public boolean allow(String key) {
        Instant now = Instant.now();
        Window w = windows.computeIfAbsent(key, k -> {
            Window nw = new Window();
            nw.resetAt = now.plusSeconds(windowSeconds);
            return nw;
        });
        synchronized (w) {
            if (now.isAfter(w.resetAt)) {
                w.count = 0;
                w.resetAt = now.plusSeconds(windowSeconds);
            }
            if (w.count >= maxRequests) return false;
            w.count++;
            return true;
        }
    }
}
