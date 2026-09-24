-- Support for the Idempotency-Key header on POST /api/orders (OH-212)
ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(100);

CREATE INDEX idx_orders_idempotency_key ON orders (idempotency_key);
