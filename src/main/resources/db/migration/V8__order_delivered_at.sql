-- When an order was delivered; the refund window for delivered orders runs from here (BR-60).
ALTER TABLE orders ADD COLUMN delivered_at TIMESTAMP;

-- Orders delivered before this column existed: their last update is the best record of the delivery.
UPDATE orders
SET delivered_at = COALESCE(updated_at, created_at)
WHERE status = 'DELIVERED';
