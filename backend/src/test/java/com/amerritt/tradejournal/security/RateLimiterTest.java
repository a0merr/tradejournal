package com.amerritt.tradejournal.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Token-bucket behaviour, driven by a clock the test controls rather than by sleeping. */
class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void allowsUpToCapacityThenDenies() {
        RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryConsume("ip")).isTrue();
        assertThat(limiter.tryConsume("ip")).isTrue();
        assertThat(limiter.tryConsume("ip")).isTrue();
        assertThat(limiter.tryConsume("ip")).isFalse();
    }

    @Test
    void refillsContinuously() {
        RateLimiter limiter = new RateLimiter(60, Duration.ofMinutes(1), clock);
        for (int i = 0; i < 60; i++) {
            limiter.tryConsume("ip");
        }
        assertThat(limiter.tryConsume("ip")).isFalse();

        // One second into a 60-per-minute budget is worth exactly one token.
        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryConsume("ip")).isTrue();
        assertThat(limiter.tryConsume("ip")).isFalse();
    }

    @Test
    void doesNotRefillBeyondCapacity() {
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1), clock);
        clock.advance(Duration.ofHours(5));

        assertThat(limiter.tryConsume("ip")).isTrue();
        assertThat(limiter.tryConsume("ip")).isTrue();
        assertThat(limiter.tryConsume("ip")).isFalse();
    }

    @Test
    void retryAfterCountsDownAndReachesZero() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(10), clock);
        limiter.tryConsume("ip");

        assertThat(limiter.retryAfterSeconds("ip")).isEqualTo(10);
        clock.advance(Duration.ofSeconds(4));
        assertThat(limiter.retryAfterSeconds("ip")).isEqualTo(6);
        clock.advance(Duration.ofSeconds(6));
        assertThat(limiter.retryAfterSeconds("ip")).isZero();
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryConsume("a")).isTrue();
        assertThat(limiter.tryConsume("a")).isFalse();
        // One noisy client must not lock everyone else out.
        assertThat(limiter.tryConsume("b")).isTrue();
    }

    @Test
    void evictionDropsRecoveredKeysAndKeepsThrottledOnes() {
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1), clock);
        limiter.tryConsume("recovered");
        limiter.tryConsume("throttled");
        limiter.tryConsume("throttled");

        clock.advance(Duration.ofSeconds(31)); // ~1 token back: enough for one, not the other
        limiter.evictIdle();

        // The evicted key starts fresh at full capacity; the retained one keeps its deficit.
        assertThat(limiter.retryAfterSeconds("recovered")).isZero();
        assertThat(limiter.tryConsume("throttled")).isTrue();
        assertThat(limiter.tryConsume("throttled")).isFalse();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public long millis() {
            return now.toEpochMilli();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
