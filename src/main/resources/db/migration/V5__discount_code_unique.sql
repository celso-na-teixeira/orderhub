-- Discount codes are unique, ignoring case (BR-50).
-- Where a code already exists more than once, the newest row keeps it; older rows are renamed and deactivated.
UPDATE discounts d
SET code   = d.code || '-OLD-' || d.id,
    active = FALSE
WHERE EXISTS (SELECT 1 FROM discounts n WHERE lower(n.code) = lower(d.code) AND n.id > d.id);

CREATE UNIQUE INDEX uq_discounts_code_lower ON discounts (lower(code));
