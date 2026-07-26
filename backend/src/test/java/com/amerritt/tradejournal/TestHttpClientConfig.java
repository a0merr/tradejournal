package com.amerritt.tradejournal;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * The HTTP client the integration tests drive the app with.
 *
 * <p>Two things are being fixed here. Apache HttpClient replaces the JDK's
 * {@code HttpURLConnection}, which cannot read a 401 response to a POST whose body it has
 * already streamed — it sees the challenge, decides to retry with credentials, and fails with
 * "cannot retry due to server authentication, in streaming mode". Most of the auth suite
 * asserts exactly that response, so with the default client most of the auth suite cannot run.
 *
 * <p>And every phase gets a timeout. A test client with no response timeout does not fail when
 * something goes wrong, it waits — a run that should take four minutes sat for twenty-four
 * before it was killed, and the log ended mid-request with no indication of which call was
 * outstanding. Bounded, a stall becomes a {@code SocketTimeoutException} naming the test.
 * Timeouts are generous enough that a slow CI runner is not mistaken for a hang.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestHttpClientConfig {

    @Bean
    RestTemplateBuilder restTemplateBuilder() {
        RequestConfig requestConfig = RequestConfig.custom()
                // Waiting for a pooled connection. Exhaustion means responses are being leaked;
                // blocking forever hides that, so it is an error instead.
                .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                // Waiting for the response after the request is on the wire.
                .setResponseTimeout(Timeout.ofSeconds(20))
                .build();

        CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                // A retried POST would re-ingest a fill. Tests assert on first responses.
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .build();

        return new RestTemplateBuilder()
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(client));
    }
}
