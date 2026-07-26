-- Repeatable dev/demo seed. Idempotent: guarded so re-runs don't duplicate.
-- Demo login -> email: demo@tradejournal.dev  password: password
--
-- The hash below is verified by DemoSeedTest, which fails the build if it stops matching the
-- password this file documents. That test exists because the previous value was a "well-known
-- sample hash" copied from elsewhere that turned out to encode nothing we could name — the
-- documented demo login had never worked, and nothing in the suite noticed, because the seed
-- only loads under the dev and demo profiles and the tests register their own users.

INSERT INTO users (email, password_hash)
SELECT 'demo@tradejournal.dev', '$2b$10$4pscYtBskZ5Dy8L2Szvqc.ZOg33bAPwz6VkcKKWJevptsCKxLMtzK'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'demo@tradejournal.dev');

INSERT INTO accounts (user_id, broker, base_currency)
SELECT u.id, 'DemoBroker', 'USD'
FROM users u
WHERE u.email = 'demo@tradejournal.dev'
  AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.user_id = u.id);

INSERT INTO instruments (symbol, exchange, asset_class)
SELECT v.symbol, v.exchange, v.asset_class
FROM (VALUES
    ('BTC-USD', 'COINBASE', 'CRYPTO'),
    ('ETH-USD', 'COINBASE', 'CRYPTO'),
    ('AAPL',    'NASDAQ',   'EQUITY')
) AS v(symbol, exchange, asset_class)
WHERE NOT EXISTS (
    SELECT 1 FROM instruments i WHERE i.symbol = v.symbol AND i.exchange = v.exchange
);

-- Orders + fills only seeded once (keyed off there being no orders yet for the demo account).
WITH acct AS (
    SELECT a.id FROM accounts a
    JOIN users u ON a.user_id = u.id
    WHERE u.email = 'demo@tradejournal.dev'
    LIMIT 1
),
ins AS (
    INSERT INTO orders (account_id, instrument_id, side, type, quantity, status)
    SELECT acct.id, i.id, v.side, v.type, v.qty, 'FILLED'
    FROM acct
    CROSS JOIN (VALUES
        ('BTC-USD', 'COINBASE', 'BUY',  'MARKET', 0.50000000),
        ('BTC-USD', 'COINBASE', 'SELL', 'LIMIT',  0.20000000),
        ('ETH-USD', 'COINBASE', 'BUY',  'MARKET', 4.00000000),
        ('AAPL',    'NASDAQ',   'BUY',  'LIMIT',  10.00000000)
    ) AS v(symbol, exchange, side, type, qty)
    JOIN instruments i ON i.symbol = v.symbol AND i.exchange = v.exchange
    WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.account_id = acct.id)
    RETURNING id, quantity
)
INSERT INTO fills (order_id, quantity, price, fee, filled_at)
SELECT ins.id, ins.quantity,
       CASE WHEN row_number() OVER (ORDER BY ins.id) = 1 THEN 61000.00
            WHEN row_number() OVER (ORDER BY ins.id) = 2 THEN 63500.00
            WHEN row_number() OVER (ORDER BY ins.id) = 3 THEN 2450.00
            ELSE 189.50 END,
       1.25,
       now() - (row_number() OVER (ORDER BY ins.id) || ' hours')::interval
FROM ins;
