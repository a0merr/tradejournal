package com.amerritt.tradejournal;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * The HTTP client the integration tests drive the app with.
 *
 * <p>Apache HttpClient replaces the JDK's {@code HttpURLConnection}, which cannot read a 401
 * response to a POST whose body it has already streamed — it sees the challenge, decides to
 * retry with credentials, and fails with "cannot retry due to server authentication, in
 * streaming mode". Most of the auth suite asserts exactly that response.
 *
 * <p>Automatic retries are off, and that is not a preference. HttpClient's default retry
 * strategy re-sends on 429 and <em>honours the {@code Retry-After} header</em>.
 * {@code AuthRateLimitIT} configures a 60-minute window, so the server answered its deliberate
 * 429 with {@code Retry-After: 3600} — entirely correctly — and the client went to sleep for an
 * hour. The suite did not deadlock and the server was never at fault; the test client took the
 * API at its word. Response timeouts do not help here, because a client sleeping between
 * attempts is not waiting on a socket.
 *
 * <p>Timeouts are set anyway. HttpClient's default response timeout is infinite, and an
 * integration test that waits forever is worse than one that fails: a stuck run held a CI
 * runner for twenty-four minutes and its log ended mid-request, naming nothing.
 *
 * <p>This is applied by {@link AbstractPostgresIT} through an injected setter rather than by
 * publishing a {@code RestTemplateBuilder} bean, which does not work:
 * {@code TestRestTemplateContextCustomizer} builds {@code TestRestTemplate} from a private
 * static builder of its own and never consults the context. A builder bean is accepted without
 * complaint and silently ignored — which is how the first attempt at this produced a suite that
 * still hung, with configuration that looked present.
 */
final class TestHttpClientConfig {

    private TestHttpClientConfig() {
    }

    static ClientHttpRequestFactory requestFactory() {
        RequestConfig requestConfig = RequestConfig.custom()
                // Waiting for a pooled connection. Exhaustion means responses are being leaked;
                // blocking forever hides that, so make it an error.
                .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                // Waiting for the response once the request is on the wire. This is the one that
                // matters: without it a server that never answers stalls the whole suite.
                .setResponseTimeout(Timeout.ofSeconds(20))
                .build();

        CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                // See above: the default strategy sleeps for Retry-After on a 429, which is an
                // hour here. Also, a silently retried POST is the wrong shape for tests that
                // assert on a first response.
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .build();

        return new HttpComponentsClientHttpRequestFactory(client);
    }
}
