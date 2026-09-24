-- Default password for all seeded accounts: Password123!
INSERT INTO users (email, password_hash, role, enabled, created_at) VALUES
    ('admin@orderhub.io',  '$2b$10$QcJvzumBk4.ngVTseQ69eOGtR/YA8ZOLi2WeRSMs/8qUznHzXWOT2', 'ADMIN',    TRUE,  '2026-01-10 09:00:00'),
    ('staff@orderhub.io',  '$2b$10$QcJvzumBk4.ngVTseQ69eOGtR/YA8ZOLi2WeRSMs/8qUznHzXWOT2', 'STAFF',    TRUE,  '2026-01-10 09:05:00'),
    ('alice@example.com',  '$2b$10$QcJvzumBk4.ngVTseQ69eOGtR/YA8ZOLi2WeRSMs/8qUznHzXWOT2', 'CUSTOMER', TRUE,  '2026-02-01 12:00:00'),
    ('bob@example.com',    '$2b$10$QcJvzumBk4.ngVTseQ69eOGtR/YA8ZOLi2WeRSMs/8qUznHzXWOT2', 'CUSTOMER', TRUE,  '2026-02-14 16:30:00'),
    ('carol@example.com',  '$2b$10$QcJvzumBk4.ngVTseQ69eOGtR/YA8ZOLi2WeRSMs/8qUznHzXWOT2', 'CUSTOMER', TRUE,  '2026-03-03 08:45:00'),
    ('dave@example.com',   '$2b$10$QcJvzumBk4.ngVTseQ69eOGtR/YA8ZOLi2WeRSMs/8qUznHzXWOT2', 'CUSTOMER', FALSE, '2026-04-20 19:10:00');

INSERT INTO customers (user_id, name, email, tier, created_at) VALUES
    ((SELECT id FROM users WHERE email = 'alice@example.com'), 'Alice Byrne',    'alice@example.com', 'GOLD',     '2026-02-01 12:00:00'),
    ((SELECT id FROM users WHERE email = 'bob@example.com'),   'Bob Kavanagh',   'bob@example.com',   'STANDARD', '2026-02-14 16:30:00'),
    ((SELECT id FROM users WHERE email = 'carol@example.com'), 'Carol Doyle',    'carol@example.com', 'STANDARD', '2026-03-03 08:45:00'),
    ((SELECT id FROM users WHERE email = 'dave@example.com'),  'Dave O''Connor', 'dave@example.com',  'STANDARD', '2026-04-20 19:10:00');

INSERT INTO products (sku, name, description, category, price, active, created_at) VALUES
    ('ELE-1001', 'Wireless Mouse',               'Ergonomic 2.4GHz wireless mouse',           'ELECTRONICS', 24.99,  TRUE,  '2026-01-15 10:00:00'),
    ('ELE-1002', 'Mechanical Keyboard',          'Hot-swappable, brown switches',             'ELECTRONICS', 89.00,  TRUE,  '2026-01-15 10:00:00'),
    ('ELE-1003', '27" 4K Monitor',               'IPS panel, USB-C power delivery',           'ELECTRONICS', 249.00, TRUE,  '2026-01-15 10:00:00'),
    ('ELE-1004', 'USB-C Hub',                    '7-in-1 hub with HDMI and card reader',      'ELECTRONICS', 39.90,  TRUE,  '2026-01-15 10:00:00'),
    ('ELE-1005', 'Noise Cancelling Headphones',  'Over-ear, 30h battery',                     'ELECTRONICS', 199.99, TRUE,  '2026-01-15 10:00:00'),
    ('OFF-2001', 'A4 Paper (500 sheets)',        '80gsm premium white',                       'OFFICE',      5.49,   TRUE,  '2026-01-16 10:00:00'),
    ('OFF-2002', 'Gel Pens (12 pack)',           'Assorted colours',                          'OFFICE',      7.99,   TRUE,  '2026-01-16 10:00:00'),
    ('OFF-2003', 'Ergonomic Office Chair',       'Adjustable lumbar support',                 'OFFICE',      329.00, TRUE,  '2026-01-16 10:00:00'),
    ('OFF-2004', 'Desk Organiser',               'Bamboo, 5 compartments',                    'OFFICE',      19.99,  TRUE,  '2026-01-16 10:00:00'),
    ('OFF-2005', 'Standing Desk',                'Electric, dual motor, 140x70cm',            'OFFICE',      549.00, TRUE,  '2026-01-16 10:00:00'),
    ('KIT-3001', 'Espresso Machine',             '15 bar pump, milk frother',                 'KITCHEN',     399.00, TRUE,  '2026-01-17 10:00:00'),
    ('KIT-3002', 'Chef''s Knife',                '20cm, German steel',                        'KITCHEN',     59.90,  TRUE,  '2026-01-17 10:00:00'),
    ('KIT-3003', 'Cast Iron Pan',                '28cm, pre-seasoned',                        'KITCHEN',     45.00,  TRUE,  '2026-01-17 10:00:00'),
    ('KIT-3004', 'Coffee Beans 1kg',             'Single origin, medium roast',               'KITCHEN',     22.50,  TRUE,  '2026-01-17 10:00:00'),
    ('KIT-3005', 'Electric Kettle',              '1.7L, temperature control',                 'KITCHEN',     34.99,  TRUE,  '2026-01-17 10:00:00'),
    ('OUT-4001', 'Camping Tent (2 person)',      'Waterproof, 2.1kg',                         'OUTDOOR',     129.00, TRUE,  '2026-01-18 10:00:00'),
    ('OUT-4002', 'Hiking Backpack 40L',          'Rain cover included',                       'OUTDOOR',     89.95,  TRUE,  '2026-01-18 10:00:00'),
    ('OUT-4003', 'Water Bottle 750ml',           'Insulated stainless steel',                 'OUTDOOR',     14.99,  TRUE,  '2026-01-18 10:00:00'),
    ('OUT-4004', 'Head Torch',                   '400 lumen, rechargeable',                   'OUTDOOR',     24.00,  TRUE,  '2026-01-18 10:00:00'),
    ('OUT-4005', 'Sleeping Bag',                 'Three season, discontinued model',          'OUTDOOR',     74.50,  FALSE, '2026-01-18 10:00:00');

INSERT INTO inventory_items (product_id, quantity_on_hand, quantity_reserved, updated_at)
SELECT p.id, v.on_hand, v.reserved, '2026-09-20 08:00:00'
FROM (VALUES
    ('ELE-1001', 120, 0),
    ('ELE-1002', 45,  0),
    ('ELE-1003', 12,  0),
    ('ELE-1004', 60,  0),
    ('ELE-1005', 3,   0),
    ('OFF-2001', 500, 0),
    ('OFF-2002', 200, 0),
    ('OFF-2003', 8,   0),
    ('OFF-2004', 75,  1),
    ('OFF-2005', 2,   0),
    ('KIT-3001', 6,   0),
    ('KIT-3002', 30,  1),
    ('KIT-3003', 25,  0),
    ('KIT-3004', 90,  0),
    ('KIT-3005', 0,   0),
    ('OUT-4001', 15,  0),
    ('OUT-4002', 20,  0),
    ('OUT-4003', 150, 0),
    ('OUT-4004', 40,  0),
    ('OUT-4005', 4,   0)
) AS v(sku, on_hand, reserved)
JOIN products p ON p.sku = v.sku;

INSERT INTO discounts (code, description, type, value, min_order_amount, valid_from, valid_until, max_uses, used_count, active) VALUES
    ('WELCOME10',  'Welcome offer 2025',              'PERCENTAGE',   10.00, NULL,   '2025-01-01 00:00:00', '2025-12-31 23:59:59', 1000, 842, TRUE),
    ('WELCOME10',  'Welcome offer 2026',              'PERCENTAGE',   10.00, NULL,   '2026-01-01 00:00:00', NULL,                  1000, 57,  TRUE),
    ('SPRING5',    'EUR 5 off orders over EUR 50',    'FIXED_AMOUNT', 5.00,  50.00,  '2026-03-01 00:00:00', '2026-12-31 23:59:59', NULL, 12,  TRUE),
    ('BIGSPENDER', '15% off orders over EUR 500',     'PERCENTAGE',   15.00, 500.00, '2026-01-01 00:00:00', NULL,                  NULL, 3,   TRUE),
    ('FLAT20',     'Service recovery voucher',        'FIXED_AMOUNT', 20.00, NULL,   NULL,                  NULL,                  NULL, 4,   TRUE),
    ('LIMITED20',  'Influencer campaign - 3 uses',    'PERCENTAGE',   20.00, NULL,   '2026-09-01 00:00:00', '2026-12-31 23:59:59', 3,    3,   TRUE),
    ('AUTUMN25',   'Autumn sale',                     'PERCENTAGE',   25.00, 30.00,  '2026-10-01 00:00:00', '2026-10-31 23:59:59', 500,  0,   TRUE);

INSERT INTO orders (order_number, customer_id, status, subtotal, discount_amount, total, discount_code, created_at, updated_at) VALUES
    ('ORD-20260902-00001', (SELECT id FROM customers WHERE email = 'alice@example.com'), 'DELIVERED', 138.98, 0.00, 138.98, NULL,      '2026-09-02 10:15:00', '2026-09-06 11:00:00'),
    ('ORD-20260905-00001', (SELECT id FROM customers WHERE email = 'bob@example.com'),   'PAID',      399.00, 0.00, 399.00, NULL,      '2026-09-05 14:02:00', '2026-09-05 14:05:00'),
    ('ORD-20260910-00001', (SELECT id FROM customers WHERE email = 'carol@example.com'), 'PENDING',   59.90,  0.00, 59.90,  NULL,      '2026-09-10 09:30:00', NULL),
    ('ORD-20260912-00001', (SELECT id FROM customers WHERE email = 'alice@example.com'), 'SHIPPED',   158.98, 5.00, 153.98, 'SPRING5', '2026-09-12 18:45:00', '2026-09-14 09:20:00'),
    ('ORD-20260915-00001', (SELECT id FROM customers WHERE email = 'bob@example.com'),   'CANCELLED', 249.00, 0.00, 249.00, NULL,      '2026-09-15 11:11:00', '2026-09-15 11:40:00'),
    ('ORD-20260918-00001', (SELECT id FROM customers WHERE email = 'carol@example.com'), 'REFUNDED',  45.00,  0.00, 45.00,  NULL,      '2026-09-18 16:20:00', '2026-09-21 10:00:00'),
    ('ORD-20260920-00001', (SELECT id FROM customers WHERE email = 'carol@example.com'), 'PAID',      45.00,  0.00, 45.00,  NULL,      '2026-09-20 23:40:00', '2026-09-20 23:42:00'),
    ('ORD-20260923-00001', (SELECT id FROM customers WHERE email = 'alice@example.com'), 'PENDING',   19.99,  0.00, 19.99,  NULL,      '2026-09-23 22:10:00', NULL);

INSERT INTO order_items (order_id, product_id, quantity, unit_price, line_total)
SELECT o.id, p.id, v.qty, v.price, v.qty * v.price
FROM (VALUES
    ('ORD-20260902-00001', 'ELE-1002', 1, 89.00),
    ('ORD-20260902-00001', 'ELE-1001', 2, 24.99),
    ('ORD-20260905-00001', 'KIT-3001', 1, 399.00),
    ('ORD-20260910-00001', 'KIT-3002', 1, 59.90),
    ('ORD-20260912-00001', 'OUT-4001', 1, 129.00),
    ('ORD-20260912-00001', 'OUT-4003', 2, 14.99),
    ('ORD-20260915-00001', 'ELE-1003', 1, 249.00),
    ('ORD-20260918-00001', 'KIT-3003', 1, 45.00),
    ('ORD-20260920-00001', 'KIT-3004', 2, 22.50),
    ('ORD-20260923-00001', 'OFF-2004', 1, 19.99)
) AS v(order_number, sku, qty, price)
JOIN orders o ON o.order_number = v.order_number
JOIN products p ON p.sku = v.sku;

INSERT INTO payments (order_id, amount, status, provider_reference, created_at)
SELECT o.id, v.amount, 'SUCCEEDED', v.ref, v.created_at::timestamp
FROM (VALUES
    ('ORD-20260902-00001', 138.98, 'psp_7f3a91c2', '2026-09-02 10:17:00'),
    ('ORD-20260905-00001', 399.00, 'psp_1b77e0d4', '2026-09-05 14:05:00'),
    ('ORD-20260912-00001', 153.98, 'psp_c0de4411', '2026-09-12 18:47:00'),
    ('ORD-20260918-00001', 45.00,  'psp_9912ab03', '2026-09-18 16:22:00'),
    ('ORD-20260920-00001', 45.00,  'psp_52e1f7aa', '2026-09-20 23:42:00')
) AS v(order_number, amount, ref, created_at)
JOIN orders o ON o.order_number = v.order_number;

INSERT INTO reservations (order_id, product_id, quantity, status, expires_at, created_at)
SELECT o.id, p.id, v.qty, v.status, v.expires_at::timestamp, v.created_at::timestamp
FROM (VALUES
    ('ORD-20260905-00001', 'KIT-3001', 1, 'CONVERTED', '2026-09-05 14:17:00', '2026-09-05 14:02:00'),
    ('ORD-20260910-00001', 'KIT-3002', 1, 'ACTIVE',    '2026-09-10 09:45:00', '2026-09-10 09:30:00'),
    ('ORD-20260915-00001', 'ELE-1003', 1, 'RELEASED',  '2026-09-15 11:26:00', '2026-09-15 11:11:00'),
    ('ORD-20260920-00001', 'KIT-3004', 2, 'CONVERTED', '2026-09-20 23:55:00', '2026-09-20 23:40:00'),
    ('ORD-20260923-00001', 'OFF-2004', 1, 'ACTIVE',    '2026-09-23 22:25:00', '2026-09-23 22:10:00')
) AS v(order_number, sku, qty, status, expires_at, created_at)
JOIN orders o ON o.order_number = v.order_number
JOIN products p ON p.sku = v.sku;

INSERT INTO audit_events (entity_type, entity_id, action, actor, details, created_at)
SELECT 'ORDER', o.id, v.action, v.actor, v.details, v.created_at::timestamp
FROM (VALUES
    ('ORD-20260902-00001', 'CREATED',        'alice@example.com', 'status=PENDING',   '2026-09-02 10:15:00'),
    ('ORD-20260902-00001', 'PAID',           'alice@example.com', 'status=PAID',      '2026-09-02 10:17:00'),
    ('ORD-20260902-00001', 'STATUS_CHANGED', 'staff@orderhub.io', 'status=SHIPPED',   '2026-09-03 15:00:00'),
    ('ORD-20260902-00001', 'STATUS_CHANGED', 'staff@orderhub.io', 'status=DELIVERED', '2026-09-06 11:00:00'),
    ('ORD-20260915-00001', 'CREATED',        'bob@example.com',   'status=PENDING',   '2026-09-15 11:11:00'),
    ('ORD-20260915-00001', 'CANCELLED',      'bob@example.com',   'status=CANCELLED', '2026-09-15 11:40:00')
) AS v(order_number, action, actor, details, created_at)
JOIN orders o ON o.order_number = v.order_number;
