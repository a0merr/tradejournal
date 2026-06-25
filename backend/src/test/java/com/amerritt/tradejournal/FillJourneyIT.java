package com.amerritt.tradejournal;

import com.amerritt.tradejournal.dto.AccountResponse;
import com.amerritt.tradejournal.dto.AuthResponse;
import com.amerritt.tradejournal.dto.FillResponse;
import com.amerritt.tradejournal.dto.PerformanceResponse;
import com.amerritt.tradejournal.dto.PositionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FillJourneyIT extends AbstractPostgresIT {

    @Autowired
    TestRestTemplate rest;

    @Test
    void protectedEndpointRejectsMissingToken() {
        ResponseEntity<String> resp = rest.getForEntity("/api/positions", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registerIngestAndReconstructPosition() {
        // register -> a default account is created and a JWT returned
        String email = "trader+" + System.nanoTime() + "@example.com";
        ResponseEntity<AuthResponse> reg = rest.postForEntity("/api/auth/register",
                Map.of("email", email, "password", "password123"), AuthResponse.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String token = reg.getBody().token();
        HttpHeaders auth = bearer(token);

        Long accountId = accounts(auth).get(0).id();

        // BUY 2 @ 100, then SELL 1 @ 120 on the same instrument
        ingest(auth, accountId, "BUY", new BigDecimal("2"), new BigDecimal("100"));
        ingest(auth, accountId, "SELL", new BigDecimal("1"), new BigDecimal("120"));

        // positions view: net = 2 - 1 = 1; avg over all fills = (2*100 + 1*120)/3
        List<PositionResponse> positions = exchangeList("/api/positions", auth);
        assertThat(positions).hasSize(1);
        PositionResponse p = positions.get(0);
        assertThat(p.netQuantity()).isEqualByComparingTo("1");
        assertThat(p.avgPrice()).isEqualByComparingTo(new BigDecimal("320").divide(new BigDecimal("3"), 8, java.math.RoundingMode.HALF_UP));
        assertThat(p.fillCount()).isEqualTo(2);

        // performance summary for the account
        ResponseEntity<PerformanceResponse> perf = rest.exchange(
                "/api/performance?accountId=" + accountId, HttpMethod.GET,
                new HttpEntity<>(auth), PerformanceResponse.class);
        assertThat(perf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(perf.getBody().totalFills()).isEqualTo(2);
        assertThat(perf.getBody().totalOrders()).isEqualTo(2);
        assertThat(perf.getBody().openPositions()).isEqualTo(1);
    }

    @Test
    void cannotIngestIntoAccountYouDoNotOwn() {
        HttpHeaders a = bearer(register());
        HttpHeaders b = bearer(register());
        Long aAccount = accounts(a).get(0).id();

        // user B tries to post a fill into user A's account -> 404 (ownership hidden)
        ResponseEntity<String> resp = rest.exchange("/api/fills", HttpMethod.POST,
                new HttpEntity<>(fillBody(aAccount, "BUY", new BigDecimal("1"), new BigDecimal("50")), b),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- helpers ---

    private String register() {
        String email = "u+" + System.nanoTime() + "@example.com";
        return rest.postForEntity("/api/auth/register",
                Map.of("email", email, "password", "password123"),
                AuthResponse.class).getBody().token();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private List<AccountResponse> accounts(HttpHeaders auth) {
        return rest.exchange("/api/accounts", HttpMethod.GET, new HttpEntity<>(auth),
                new ParameterizedTypeReference<List<AccountResponse>>() {
                }).getBody();
    }

    private FillResponse ingest(HttpHeaders auth, Long accountId, String side,
                                BigDecimal qty, BigDecimal price) {
        ResponseEntity<FillResponse> resp = rest.exchange("/api/fills", HttpMethod.POST,
                new HttpEntity<>(fillBody(accountId, side, qty, price), auth), FillResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    private Map<String, Object> fillBody(Long accountId, String side, BigDecimal qty, BigDecimal price) {
        return Map.of(
                "accountId", accountId,
                "symbol", "TEST-USD",
                "exchange", "TESTEX",
                "assetClass", "CRYPTO",
                "side", side,
                "type", "MARKET",
                "quantity", qty,
                "price", price,
                "fee", new BigDecimal("0.10"));
    }

    private List<PositionResponse> exchangeList(String path, HttpHeaders auth) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(auth),
                new ParameterizedTypeReference<List<PositionResponse>>() {
                }).getBody();
    }
}
