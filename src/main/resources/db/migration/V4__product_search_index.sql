-- Speed up product listing on the storefront (OH-248)
CREATE INDEX idx_products_active ON products (active);
CREATE INDEX idx_products_name ON products (name);
