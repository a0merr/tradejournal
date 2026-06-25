-- positions: derived from fills, never stored as a mutable number.
-- Net signed quantity (BUY +, SELL -), quantity-weighted average fill price, and total fees,
-- aggregated per (account, instrument).

CREATE VIEW positions AS
SELECT
    o.account_id                                                              AS account_id,
    o.instrument_id                                                           AS instrument_id,
    i.symbol                                                                  AS symbol,
    i.exchange                                                                AS exchange,
    SUM(CASE WHEN o.side = 'BUY' THEN f.quantity ELSE -f.quantity END)        AS net_quantity,
    SUM(f.quantity * f.price) / NULLIF(SUM(f.quantity), 0)                    AS avg_price,
    SUM(f.fee)                                                                AS total_fees,
    COUNT(f.id)                                                               AS fill_count,
    MAX(f.filled_at)                                                          AS last_filled_at
FROM fills f
JOIN orders o      ON f.order_id = o.id
JOIN instruments i ON o.instrument_id = i.id
GROUP BY o.account_id, o.instrument_id, i.symbol, i.exchange;
