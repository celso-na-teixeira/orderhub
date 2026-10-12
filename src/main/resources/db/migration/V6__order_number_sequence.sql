-- Order numbers come from a database sequence so they stay unique across restarts and instances (BR-23).
-- The sequence starts after the highest number already issued.
CREATE SEQUENCE order_number_seq;

SELECT setval('order_number_seq',
              COALESCE((SELECT MAX(CAST(substring(order_number FROM '([0-9]+)$') AS BIGINT)) FROM orders), 0) + 1,
              false);
