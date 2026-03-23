package com.smartfarm.server.config;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP 기반 인메모리 슬라이딩 윈도우 Rate Limiter
 * 별도 외부 의존성 없이 단순하게 구현합니다.
 */
public class DeviceRegisterRateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final ConcurrentHashMap<String, RequestBucket> buckets = new ConcurrentHashMap<>();

    public DeviceRegisterRateLimiter(int maxRequests, Duration window) {
        this.maxRequests = maxRequests;
        this.window      = window;
    }

    /**
     * @param ip 클라이언트 IP
     * @return true — 허용, false — 차단
     */
    public boolean tryAcquire(String ip) {
        Instant now = Instant.now();
        RequestBucket bucket = buckets.compute(ip, (k, existing) -> {
            if (existing == null || existing.windowStart.plus(window).isBefore(now)) {
                return new RequestBucket(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        return bucket.count.get() <= maxRequests;
    }

    private static class RequestBucket {
        final Instant windowStart;
        final AtomicInteger count;

        RequestBucket(Instant windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count       = count;
        }
    }
}
