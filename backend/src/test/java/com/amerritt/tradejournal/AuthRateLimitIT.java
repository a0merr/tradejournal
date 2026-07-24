package com.amerritt.tradejournal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Throttling of the unauthenticated auth endpoints, with the budget lowered to something a
 * test can exhaust. Its own dynamic property, which outranks the profile default the rest of
 * the suite runs with.
 */
class AuthRateLimitIT extends AbstractPostgresIT {

    private static final int ATTEMPTS = 3;

    @DynamicPropertySource
    static void rateLimitProps(DynamicPropertyRegistry registry) {
        registry.add("tradejournal.rate-limit.auth-attempts", () -> ATTEMPTS);
        registry.add("tradejournal.rate-limit.auth-window-minutes", () -> 60);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void repeatedLoginAttemptsAreThrottledWithAProblemDetail() throws Exception {
        // Wrong password every time: what a credential-stuffing run looks like.
        ResponseEntity<String> last = null;
        for (int i = 0; i < ATTEMPTS + 1; i++) {
            last = login("nobody@example.com", "wrong-password-" + i);
        }

        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(last.getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(last.getHeaders().getContentType())
                .isNotNull()
                .satisfies(type -> assertThat(type.toString())
                        .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE));

        JsonNode problem = objectMapper.readTree(last.getBody());
        assertThat(problem.get("type").asText()).isEqualTo("urn:tradejournal:problem:rate-limited");
        assertThat(problem.get("status").asInt()).isEqualTo(429);
        assertThat(problem.get("instance").asText()).isEqualTo("/api/auth/login");
    }

    @Test
    void throttlingDoesNotTouchOtherEndpoints() throws Exception {
        // Spend the whole auth budget, then confirm the throttle is scoped to the auth paths:
        // a protected endpoint is still rejected for the missing token, not for the rate.
        for (int i = 0; i <= ATTEMPTS; i++) {
            login("nobody@example.com", "wrong");
        }

        ResponseEntity<String> resp = rest.exchange("/api/positions", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode problem = objectMapper.readTree(resp.getBody());
        assertThat(problem.get("type").asText()).isEqualTo("urn:tradejournal:problem:unauthenticated");
    }

    private ResponseEntity<String> login(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", password), headers),
                String.class);
    }
}
