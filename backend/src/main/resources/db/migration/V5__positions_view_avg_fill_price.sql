-- Renames the view's `avg_price` to `avg_fill_price`, because that is what it computes.
--
-- The old name implied a cost basis. The expression averages BUY and SELL fills together, so
-- after BUY 2 @ 100 and SELL 1 @ 120 it reported 106.67 for a position whose remaining unit
-- was bought at 100. The number was never a basis and is now named for what it is: the
-- quantity-weighted average price across every fill, useful as an execution-quality figure.
--
-- Cost basis of the open position is FIFO, matching how realized PnL is computed, and is
-- derived in the application from the fill stream (PnlCalculator) rather than in SQL.
--
-- A view's columns cannot be renamed in place, so it is dropped and recreated.

DROP VIEW positions;

CREATE VIEW positions AS
SELECT
    o.account_id                                                              AS account_id,
    o.instrument_id                                                           AS instrument_id,
    i.symbol                                                                  AS symbol,
    i.exchange                                                                AS exchange,
    SUM(CASE WHEN o.side = 'BUY' THEN f.quantity ELSE -f.quantity END)        AS net_quantity,
    SUM(f.quantity * f.price) / NULLIF(SUM(f.quantity), 0)                    AS avg_fill_price,
    SUM(f.fee)                                                                AS total_fees,
    COUNT(f.id)                                                               AS fill_count,
    MAX(f.filled_at)                                                          AS last_filled_at
FROM fills f
JOIN orders o      ON f.order_id = o.id
JOIN instruments i ON o.instrument_id = i.id
GROUP BY o.account_id, o.instrument_id, i.symbol, i.exchange;
