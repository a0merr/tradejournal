package com.amerritt.tradejournal;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the seeded demo account's stored hash matches the password the docs publish.
 *
 * <p>{@link DemoSeedIT} proves the same thing end to end; this one is here for the error
 * message. A failed login in an integration test says "expected 200, got 401", which sends you
 * looking at the auth code. This says the hash does not match the password — which is the
 * actual defect, and was: the value in place had been copied in as "the well-known sample hash
 * for the literal password" and matched no password at all, so the demo credentials in
 * DEPLOY.md had never once worked.
 */
class DemoSeedTest {

    private static final String SEED = "db/seed/R__dev_seed.sql";
    private static final String DEMO_EMAIL = "demo@tradejournal.dev";
    private static final String DEMO_PASSWORD = "password";

    /** A bcrypt hash, in any of the three prefixes Spring's encoder accepts. */
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    @Test
    void seededDemoUserAcceptsTheDocumentedPassword() {
        String sql = read();
        assertThat(sql).contains(DEMO_EMAIL);

        Matcher matcher = BCRYPT.matcher(sql);
        assertThat(matcher.find())
                .as("the seed must contain a bcrypt hash for %s", DEMO_EMAIL)
                .isTrue();

        assertThat(new BCryptPasswordEncoder().matches(DEMO_PASSWORD, matcher.group()))
                .as("the seeded hash must match the password the README and DEPLOY.md publish")
                .isTrue();
    }

    private static String read() {
        try (InputStream in = DemoSeedTest.class.getClassLoader().getResourceAsStream(SEED)) {
            assertThat(in).as("classpath resource %s", SEED).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Could not read " + SEED, ex);
        }
    }
}
