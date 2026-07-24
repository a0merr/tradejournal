package com.amerritt.tradejournal.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-capacity token bucket per key, refilling continuously.
 *
 * <p>Continuous refill rather than a fixed window on purpose: a window that resets on the hour
 * lets an attacker spend a full budget on either side of the boundary, doubling the real rate
 * at exactly the moment it matters.
 *
 * <p><b>Per instance.</b> Behind more than one replica the effective limit multiplies by the
 * replica count, which is a weaker guarantee but never a wrong one. Moving the buckets to Redis
 * is the fix when this runs on more than one node.
 */
public class RateLimiter {

    private final int capacity;
    private final double refillPerMilli;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int capacity, Duration refillPeriod, Clock clock) {
        this.capacity = capacity;
        this.refillPerMilli = (double) capacity / refillPeriod.toMillis();
        this.clock = clock;
    }

    /** @return true if a token was available and consumed */
    public boolean tryConsume(String key) {
        return buckets.computeIfAbsent(key, k -> new Bucket()).tryConsume();
    }

    /** Whole seconds until the next token, for the {@code Retry-After} header. */
    public long retryAfterSeconds(String key) {
        Bucket bucket = buckets.get(key);
        return bucket == null ? 0 : bucket.retryAfterSeconds();
    }

    /** Drops buckets that have refilled to full, so idle keys do not accumulate forever. */
    public void evictIdle() {
        buckets.entrySet().removeIf(entry -> entry.getValue().isFull());
    }

    private final class Bucket {

        private double tokens = capacity;
        private long lastRefillMillis = clock.millis();

        synchronized boolean tryConsume() {
            refill();
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }

        synchronized long retryAfterSeconds() {
            refill();
            if (tokens >= 1.0) {
                return 0;
            }
            double deficit = 1.0 - tokens;
            return (long) Math.ceil(deficit / refillPerMilli / 1000.0);
        }

        synchronized boolean isFull() {
            refill();
            return tokens >= capacity;
        }

        private void refill() {
            long now = clock.millis();
            long elapsed = now - lastRefillMillis;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed * refillPerMilli);
                lastRefillMillis = now;
            }
        }
    }
}
