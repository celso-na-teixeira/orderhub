CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE customers (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       REFERENCES users (id),
    name            VARCHAR(150) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    tier            VARCHAR(20)  NOT NULL DEFAULT 'STANDARD',
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    sku             VARCHAR(64)    NOT NULL UNIQUE,
    name            VARCHAR(200)   NOT NULL,
    description     TEXT,
    category        VARCHAR(64),
    price           NUMERIC(12, 2) NOT NULL,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP
);

CREATE TABLE inventory_items (
    id                  BIGSERIAL PRIMARY KEY,
    product_id          BIGINT    NOT NULL UNIQUE REFERENCES products (id),
    quantity_on_hand    INTEGER   NOT NULL,
    quantity_reserved   INTEGER   NOT NULL DEFAULT 0,
    version             BIGINT    NOT NULL DEFAULT 0,
    updated_at          TIMESTAMP
);

CREATE TABLE orders (
    id                  BIGSERIAL PRIMARY KEY,
    order_number        VARCHAR(40)    NOT NULL UNIQUE,
    customer_id         BIGINT         NOT NULL REFERENCES customers (id),
    status              VARCHAR(20)    NOT NULL,
    subtotal            NUMERIC(12, 2) NOT NULL,
    discount_amount     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total               NUMERIC(12, 2) NOT NULL,
    discount_code       VARCHAR(40),
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP
);

CREATE TABLE order_items (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT         NOT NULL REFERENCES orders (id),
    product_id      BIGINT         NOT NULL REFERENCES products (id),
    quantity        INTEGER        NOT NULL,
    unit_price      NUMERIC(12, 2) NOT NULL,
    line_total      NUMERIC(12, 2) NOT NULL
);

CREATE TABLE reservations (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT,
    product_id      BIGINT      NOT NULL,
    quantity        INTEGER     NOT NULL,
    status          VARCHAR(20) NOT NULL,
    expires_at      TIMESTAMP   NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT           NOT NULL REFERENCES orders (id),
    amount              DOUBLE PRECISION NOT NULL,
    status              VARCHAR(20)      NOT NULL,
    provider_reference  VARCHAR(100),
    created_at          TIMESTAMP        NOT NULL DEFAULT now()
);

CREATE TABLE discounts (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(40)    NOT NULL,
    description         VARCHAR(255),
    type                VARCHAR(20)    NOT NULL,
    value               NUMERIC(10, 2) NOT NULL,
    min_order_amount    NUMERIC(12, 2),
    valid_from          TIMESTAMP,
    valid_until         TIMESTAMP,
    max_uses            INTEGER,
    used_count          INTEGER        NOT NULL DEFAULT 0,
    active              BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE audit_events (
    id              BIGSERIAL PRIMARY KEY,
    entity_type     VARCHAR(40)  NOT NULL,
    entity_id       BIGINT       NOT NULL,
    action          VARCHAR(40)  NOT NULL,
    actor           VARCHAR(255),
    details         TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_category ON products (category);
CREATE INDEX idx_reservations_status ON reservations (status);
