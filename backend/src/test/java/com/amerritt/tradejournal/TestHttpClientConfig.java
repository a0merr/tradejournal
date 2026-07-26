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
 * <p>Every phase is then bounded. HttpClient's default response timeout is infinite, and an
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
                // A retried POST would re-ingest a fill, which is the one thing this API exists
                // to prevent. Tests assert on first responses.
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .build();

        return new HttpComponentsClientHttpRequestFactory(client);
    }
}
