-- Pins the scale of the view's computed average.
--
-- `SUM(quantity * price) / SUM(quantity)` is numeric division, and Postgres picks the result
-- scale itself — for BUY 2 @ 100 plus SELL 1 @ 120 it returned 106.6666666666666667, sixteen
-- decimal places, in a domain where every stored price and quantity is NUMERIC(20,8). The tail
-- carries no information: it is an artifact of how the division was evaluated, and it would
-- move if the expression were ever rewritten.
--
-- Rounding to the column scale makes the API's output a function of the data rather than of
-- Postgres internals, so a client that formats or compares this value cannot be surprised by a
-- change nobody made on purpose.
--
-- A view's column types cannot be altered in place, so it is dropped and recreated.

DROP VIEW positions;

CREATE VIEW positions AS
SELECT
    o.account_id                                                              AS account_id,
    o.instrument_id                                                           AS instrument_id,
    i.symbol                                                                  AS symbol,
    i.exchange                                                                AS exchange,
    SUM(CASE WHEN o.side = 'BUY' THEN f.quantity ELSE -f.quantity END)        AS net_quantity,
    ROUND(SUM(f.quantity * f.price) / NULLIF(SUM(f.quantity), 0), 8)          AS avg_fill_price,
    SUM(f.fee)                                                                AS total_fees,
    COUNT(f.id)                                                               AS fill_count,
    MAX(f.filled_at)                                                          AS last_filled_at
FROM fills f
JOIN orders o      ON f.order_id = o.id
JOIN instruments i ON o.instrument_id = i.id
GROUP BY o.account_id, o.instrument_id, i.symbol, i.exchange;
