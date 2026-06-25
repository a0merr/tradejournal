-- tradejournal core schema
-- Money is stored as NUMERIC, never floating point. Positions are derived (see V2), not stored.

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE accounts (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    broker        VARCHAR(100) NOT NULL,
    base_currency VARCHAR(3)   NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_accounts_user_id ON accounts (user_id);

CREATE TABLE instruments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    symbol      VARCHAR(32)  NOT NULL,
    exchange    VARCHAR(32)  NOT NULL,
    asset_class VARCHAR(32)  NOT NULL,
    CONSTRAINT uq_instruments_symbol_exchange UNIQUE (symbol, exchange)
);

CREATE TABLE orders (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id    BIGINT        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    instrument_id BIGINT        NOT NULL REFERENCES instruments (id),
    side          VARCHAR(4)    NOT NULL CHECK (side IN ('BUY', 'SELL')),
    type          VARCHAR(8)    NOT NULL CHECK (type IN ('MARKET', 'LIMIT')),
    quantity      NUMERIC(20,8) NOT NULL CHECK (quantity > 0),
    status        VARCHAR(12)   NOT NULL CHECK (status IN ('NEW', 'PARTIAL', 'FILLED', 'CANCELLED')),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
-- open-orders view and per-account scans
CREATE INDEX idx_orders_account_status ON orders (account_id, status);
CREATE INDEX idx_orders_instrument_id  ON orders (instrument_id);

CREATE TABLE fills (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    quantity   NUMERIC(20,8) NOT NULL CHECK (quantity > 0),
    price      NUMERIC(20,8) NOT NULL CHECK (price >= 0),
    fee        NUMERIC(20,8) NOT NULL DEFAULT 0 CHECK (fee >= 0),
    filled_at  TIMESTAMPTZ   NOT NULL
);
-- journal queries by order, time-range scans
CREATE INDEX idx_fills_order_id  ON fills (order_id);
CREATE INDEX idx_fills_filled_at ON fills (filled_at);
