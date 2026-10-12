-- An idempotency key identifies one order per customer (BR-24).
DROP INDEX idx_orders_idempotency_key;

CREATE UNIQUE INDEX uq_orders_customer_idempotency_key
    ON orders (customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
