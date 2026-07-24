-- Refresh tokens for rotating-token auth.
--
-- Access JWTs are short-lived and stateless (no revocation). Sessions are made revocable by
-- pairing them with an opaque refresh token that IS server state. Only the SHA-256 of the token
-- is stored, so a database leak does not hand out live sessions.
--
-- family_id chains every rotation of one login. Presenting an already-rotated token means the
-- token leaked, so the whole family is revoked rather than just that row.

CREATE TABLE refresh_tokens (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 64 hex characters of SHA-256. VARCHAR, not CHAR: `ddl-auto: validate` compares the
    -- mapped String against varchar, and bpchar would fail the check at startup.
    token_hash  VARCHAR(64) NOT NULL,
    family_id   UUID        NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

-- family-wide revocation on reuse detection
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
-- "log me out everywhere" and per-user cleanup
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
-- expired-row sweep
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
