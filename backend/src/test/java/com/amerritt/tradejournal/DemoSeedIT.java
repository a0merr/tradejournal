package com.amerritt.tradejournal;

import com.amerritt.tradejournal.dto.AuthResponse;
import com.amerritt.tradejournal.dto.PositionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demo account is the first thing anyone touches, and until this existed it was the only
 * user path nothing exercised: the seed runs under the {@code dev} profile, which these tests
 * activate, but every other test registers a throwaway user of its own. So a seed that produced
 * an unusable login passed the entire suite and failed in front of a reviewer.
 *
 * <p>Deliberately end-to-end over HTTP rather than against the repository — the claim being
 * tested is "the credentials in DEPLOY.md sign you in", not "a row exists".
 */
class DemoSeedIT extends AbstractPostgresIT {

    private static final String DEMO_EMAIL = "demo@tradejournal.dev";
    private static final String DEMO_PASSWORD = "password";

    @Autowired
    TestRestTemplate rest;

    @Test
    void theDocumentedDemoCredentialsSignIn() {
        ResponseEntity<AuthResponse> resp = login(DEMO_PASSWORD);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().token()).isNotBlank();
        assertThat(resp.getBody().refreshToken()).isNotBlank();
    }

    @Test
    void theSeededAccountHasPositionsToLookAt() {
        // An empty dashboard is a broken demo even when every endpoint returns 200.
        HttpHeaders auth = new HttpHeaders();
        auth.setBearerAuth(login(DEMO_PASSWORD).getBody().token());

        List<PositionResponse> positions = rest.exchange("/api/positions", HttpMethod.GET,
                new HttpEntity<>(auth), new ParameterizedTypeReference<List<PositionResponse>>() {
                }).getBody();

        assertThat(positions).isNotEmpty();
        assertThat(positions).allSatisfy(p -> assertThat(p.fillCount()).isPositive());
    }

    @Test
    void theWrongPasswordIsStillRejected() {
        // Guards against "fixing" a failing seed by making the encoder permissive.
        assertThat(login("not-the-demo-password").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<AuthResponse> login(String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", DEMO_EMAIL, "password", password), headers),
                AuthResponse.class);
    }
}
