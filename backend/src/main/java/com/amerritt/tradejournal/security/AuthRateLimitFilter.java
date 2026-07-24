package com.amerritt.tradejournal.security;

import com.amerritt.tradejournal.exception.ProblemTypes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;

/**
 * Throttles the unauthenticated auth endpoints.
 *
 * <p>BCrypt makes each guess expensive but nothing stops an attacker making them in parallel
 * forever, and {@code /api/auth/refresh} is equally worth grinding. Rejected attempts get the
 * same RFC 7807 body as every other failure, plus {@code Retry-After}.
 *
 * <p>Keyed on client IP. That is coarse — one NAT egress shares a bucket — which is why the
 * budget is generous enough that a human never notices while a credential-stuffing run does.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/refresh");

    private final RateLimiter limiter;
    private final ProblemDetailWriter writer;

    public AuthRateLimitFilter(ProblemDetailWriter writer, Clock clock,
                               @Value("${tradejournal.rate-limit.auth-attempts:20}") int attempts,
                               @Value("${tradejournal.rate-limit.auth-window-minutes:5}") long windowMinutes) {
        this.writer = writer;
        this.limiter = new RateLimiter(attempts, Duration.ofMinutes(windowMinutes), clock);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String key = clientIp(request);
        if (limiter.tryConsume(key)) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfter = limiter.retryAfterSeconds(key);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        writer.write(request, response, HttpStatus.TOO_MANY_REQUESTS, ProblemTypes.RATE_LIMITED,
                "Too many requests",
                "Too many authentication attempts. Retry in " + retryAfter + "s.");
    }

    @Scheduled(fixedDelayString = "PT10M")
    void evictIdleBuckets() {
        limiter.evictIdle();
    }

    /**
     * Trusts {@code X-Forwarded-For} because the app runs behind a managed proxy that sets it.
     * Direct exposure would make this header attacker-controlled and the limit trivial to evade.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
