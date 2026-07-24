package com.amerritt.tradejournal;

import com.amerritt.tradejournal.dto.AuthResponse;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Refresh-token rotation, reuse detection, logout, and the RFC 7807 shape of auth failures. */
class AuthRotationIT extends AbstractPostgresIT {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void registerReturnsBothTokens() {
        AuthResponse auth = register();
        assertThat(auth.token()).isNotBlank();
        assertThat(auth.refreshToken()).isNotBlank();
        assertThat(auth.tokenType()).isEqualTo("Bearer");
        assertThat(auth.refreshExpiresInMinutes()).isGreaterThan(auth.expiresInMinutes());
    }

    @Test
    void refreshRotatesBothTokensAndRetiresTheOldRefreshToken() {
        AuthResponse first = register();

        ResponseEntity<AuthResponse> refreshed = refresh(first.refreshToken(), AuthResponse.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody().refreshToken()).isNotEqualTo(first.refreshToken());

        // the rotated-in token authenticates a protected endpoint
        ResponseEntity<String> positions = rest.exchange("/api/positions", HttpMethod.GET,
                new HttpEntity<>(bearer(refreshed.getBody().token())), String.class);
        assertThat(positions.getStatusCode()).isEqualTo(HttpStatus.OK);

        // ...and the token it replaced is dead
        assertThat(refresh(first.refreshToken(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reusingASpentTokenRevokesTheWholeFamily() {
        AuthResponse first = register();
        AuthResponse second = refresh(first.refreshToken(), AuthResponse.class).getBody();

        // replaying the spent token is treated as theft, not as a client bug
        ResponseEntity<String> replay = refresh(first.refreshToken(), String.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // so the legitimate holder's current token is revoked too — both parties must re-login
        assertThat(refresh(second.refreshToken(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesTheSession() {
        AuthResponse auth = register();

        ResponseEntity<Void> logout = rest.exchange("/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(Map.of("refreshToken", auth.refreshToken()), json()), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(refresh(auth.refreshToken(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownRefreshTokenIsRejectedAsProblemDetail() throws Exception {
        ResponseEntity<String> resp = refresh("not-a-real-token", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(contentType(resp)).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        JsonNode problem = objectMapper.readTree(resp.getBody());
        assertThat(problem.get("type").asText())
                .isEqualTo("urn:tradejournal:problem:invalid-refresh-token");
        assertThat(problem.get("status").asInt()).isEqualTo(401);
        assertThat(problem.get("title").asText()).isEqualTo("Refresh token rejected");
        assertThat(problem.get("instance").asText()).isEqualTo("/api/auth/refresh");
        assertThat(problem.get("timestamp").asText()).isNotBlank();
    }

    @Test
    void missingTokenIsRejectedByTheFilterChainAsProblemDetail() throws Exception {
        ResponseEntity<String> resp = rest.getForEntity("/api/positions", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(contentType(resp)).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(resp.getHeaders().getFirst("WWW-Authenticate")).startsWith("Bearer");

        JsonNode problem = objectMapper.readTree(resp.getBody());
        assertThat(problem.get("type").asText()).isEqualTo("urn:tradejournal:problem:unauthenticated");
        assertThat(problem.get("instance").asText()).isEqualTo("/api/positions");
    }

    @Test
    void validationFailureCarriesFieldLevelErrors() throws Exception {
        ResponseEntity<String> resp = rest.exchange("/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "not-an-email", "password", "x"), json()),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode problem = objectMapper.readTree(resp.getBody());
        assertThat(problem.get("type").asText()).isEqualTo("urn:tradejournal:problem:validation-failed");
        assertThat(problem.get("errors").size()).isPositive();
        assertThat(problem.get("errors").get(0).get("field").asText()).isNotBlank();
    }

    // --- helpers ---

    private String contentType(ResponseEntity<?> resp) {
        MediaType type = resp.getHeaders().getContentType();
        assertThat(type).isNotNull();
        return type.toString();
    }

    private AuthResponse register() {
        String email = "rot+" + System.nanoTime() + "@example.com";
        ResponseEntity<AuthResponse> resp = rest.postForEntity("/api/auth/register",
                Map.of("email", email, "password", "password123"), AuthResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    private <T> ResponseEntity<T> refresh(String refreshToken, Class<T> responseType) {
        return rest.exchange("/api/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(Map.of("refreshToken", refreshToken), json()), responseType);
    }

    private HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = json();
        headers.setBearerAuth(token);
        return headers;
    }
}
