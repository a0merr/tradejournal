-- Idempotent ingestion.
--
-- A bot that retries after a timeout would otherwise book the same execution twice, and no
-- later fix recovers the history: realized PnL is silently wrong from then on. The broker's
-- own execution id is the natural dedup key.
--
-- The key lives on `orders` rather than `fills` because ingestion creates exactly one order
-- per execution, and `orders` already carries `account_id` — which the constraint needs, since
-- a broker execution id is unique within an account, not globally. Scoping it per account also
-- stops one user's ingest from colliding with another's.
--
-- NULL is allowed and repeats freely: it means "this caller did not supply an id", and in
-- PostgreSQL NULLs do not collide in a unique index.

ALTER TABLE orders ADD COLUMN external_id VARCHAR(64);

CREATE UNIQUE INDEX uq_orders_account_external_id
    ON orders (account_id, external_id)
    WHERE external_id IS NOT NULL;
