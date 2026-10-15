# OrderHub

OrderHub is the backend for a small B2C retailer. It handles the catalogue, stock and discount codes, and runs orders from checkout through payment, shipping and refunds. Customers use it through the storefront and mobile app. Warehouse staff and administrators use it through the back-office UI.

Version 1.4 is the current internal release. Several teams have worked on the service, and the backlog items referenced below (`OH-xxx`) are still open.

---

## Contents

1. [Domain overview](#domain-overview)
2. [Architecture](#architecture)
3. [Running the project](#running-the-project)
4. [Authentication](#authentication)
5. [API overview](#api-overview)
6. [Business rules](#business-rules)
7. [Shipped features](#shipped-features)
8. [Error handling contract](#error-handling-contract)
9. [Testing](#testing)
10. [Operations](#operations)

---

## Domain overview

| Concept | Description |
|---|---|
| **User** | A login account. Its role is `ADMIN`, `STAFF` or `CUSTOMER`. |
| **Customer** | The commercial profile (name, contact email, tier) linked to a `CUSTOMER` user. |
| **Product** | A sellable item identified by a unique SKU such as `ELE-1001`. |
| **Inventory item** | The stock record for one product: `quantityOnHand` (physically in the warehouse) and `quantityReserved` (held for unpaid orders). *Available* stock is `onHand - reserved`. |
| **Order / Order item** | A customer's purchase and its lines. Each line records the unit price that applied when the order was placed. |
| **Reservation** | A time-limited hold on stock for one line of a pending order. |
| **Payment** | A charge attempt made through the payment service provider (PSP). |
| **Discount** | A code that customers can enter at checkout, either a percentage or a fixed amount. |
| **Audit event** | An append-only record of significant changes to orders. |

### Order lifecycle

```
            pay                ship              deliver
PENDING ──────────▶ PAID ──────────▶ SHIPPED ──────────▶ DELIVERED
   │                  │                                      │
   │ cancel           │ cancel (refund issued)               │ refund (≤ 30 days)
   ▼                  ▼                                      ▼
CANCELLED          CANCELLED                              REFUNDED
                      │
                      └── refund ──▶ REFUNDED   (PAID orders only)
```

`CANCELLED` and `REFUNDED` are terminal states.

---

## Architecture

The application is a single Spring Boot service (a modular monolith) that stores its data in PostgreSQL.

```
HTTP ─▶ Controllers ─▶ Services ─▶ Repositories (Spring Data JPA) ─▶ PostgreSQL
            │              │
         Mappers /      Domain entities, PaymentGateway (PSP adapter),
         DTOs           AuditService, ReservationService (scheduled clean-up)
```

```
com.orderhub
├── config        OpenAPI, request-id filter, scheduling, typed properties
├── controller    REST endpoints
├── domain        JPA entities and enums
├── dto           request/response records
├── exception     exception types and the global handler
├── mapper        entity ⇄ DTO mapping
├── repository    Spring Data repositories
├── security      JWT, filter chain, user details
├── service       business logic
├── util          helpers (money, order numbers)
└── validation    custom Bean Validation constraints
```

Flyway manages the database schema (`src/main/resources/db/migration`). `V2__seed_data.sql` loads realistic demo data.

---

## Running the project

### Prerequisites

* JDK 21
* Maven 3.9+
* Docker with Docker Compose (for PostgreSQL and the Testcontainers tests)

### 1. Start PostgreSQL

```bash
docker compose up -d postgres
```

This starts PostgreSQL 16 on `localhost:5432` with database, user and password all set to `orderhub`.

### 2. Run the application with Maven

```bash
mvn spring-boot:run
```

The API is served on <http://localhost:8080>. Flyway migrates the schema and loads the seed data the first time the service starts.

To start again from the seed data:

```bash
docker compose down -v && docker compose up -d postgres
```

### 3. Or run everything in Docker

```bash
docker compose --profile full up --build
```

This builds the image and runs it with the `prod` Spring profile.

### Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/orderhub` | JDBC URL |
| `DB_USERNAME` | `orderhub` | DB user |
| `DB_PASSWORD` | `orderhub` | DB password |
| `JWT_SECRET` | dev value in `application.yml` | HMAC key for signing tokens (≥ 32 bytes) |
| `JWT_EXPIRATION_MINUTES` | `60` | Token lifetime |
| `SPRING_PROFILES_ACTIVE` | *(none)* | Use `prod` for production-like settings |

### Swagger / OpenAPI

* Swagger UI: <http://localhost:8080/swagger-ui.html>
* OpenAPI JSON: <http://localhost:8080/v3/api-docs>

To authorise in Swagger UI, click **Authorize** and paste the token returned by `/api/auth/login`.

### Seeded accounts

All accounts use the password `Password123!`.

| Email | Role | Notes |
|---|---|---|
| `admin@orderhub.io` | ADMIN | |
| `staff@orderhub.io` | STAFF | Warehouse / customer support |
| `alice@example.com` | CUSTOMER | GOLD tier; has DELIVERED, SHIPPED and PENDING orders |
| `bob@example.com` | CUSTOMER | Has PAID and CANCELLED orders |
| `carol@example.com` | CUSTOMER | Has PENDING, PAID and REFUNDED orders |
| `dave@example.com` | CUSTOMER | **Disabled** account |

### Quick smoke test with curl

```bash
TOKEN=$(curl -s localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Password123!"}' | jq -r .token)

curl -s localhost:8080/api/orders -H "Authorization: Bearer $TOKEN" | jq
```

### Seeing the SQL

Uncomment the `org.hibernate.SQL` and `org.hibernate.orm.jdbc.bind` lines in `application.yml` to log SQL statements and their bound parameters. Uncomment the transaction manager loggers to see where transactions begin and commit.

---

## Authentication

* `POST /api/auth/register` creates a **CUSTOMER** account and its customer profile, and returns a JWT.
* `POST /api/auth/login` returns a JWT for valid credentials.
* Send the token as `Authorization: Bearer <token>`.
* Tokens expire after `app.jwt.expiration-minutes`. **Expired, malformed or tampered tokens must be rejected with `401`.**
* Disabled users must not be able to log in or use existing tokens.
* A failed login returns the same `401` response whether the email or the password was wrong. This prevents attackers from discovering which emails have accounts.

### Roles

| Role | Can do |
|---|---|
| `CUSTOMER` | Browse products, place and pay for **their own** orders, cancel **their own** pending orders, view and update **their own** profile |
| `STAFF` | Everything read-only, adjust inventory, move orders through fulfilment (ship / deliver), issue refunds |
| `ADMIN` | Everything, including product and discount management and reports |

---

## API overview

All endpoints are under `/api`. Unless noted otherwise, bodies are JSON.

### Auth
| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/auth/register` | public | Register a customer |
| POST | `/auth/login` | public | Obtain a token |

### Products
| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/products` | public | Search the catalogue (see below) |
| GET | `/products/{id}` | public | Product details including current available stock |
| POST | `/products` | ADMIN | Create a product and its inventory record. Returns **201** with a `Location` header |
| PATCH | `/products/{id}` | ADMIN | **Partial** update: only the fields present in the body change |
| DELETE | `/products/{id}` | ADMIN | Deactivate (soft delete). Returns **204** |

**Search parameters** for `GET /products`:

* `name`: case-insensitive "contains" match
* `category`: exact match
* `minPrice`, `maxPrice`: inclusive price range
* `page` (zero-based, default `0`), `size` (default `20`, **max `100`**)

Filters can be combined, and any filter can be omitted. Only active products are returned.

### Inventory
| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/inventory/{productId}` | STAFF, ADMIN | Stock levels |
| PUT | `/inventory/{productId}` | STAFF, ADMIN | Set the on-hand quantity after a stock count. `reason` is required |
| GET | `/inventory/low-stock?threshold=5` | STAFF, ADMIN | Products whose available stock is at or below the threshold |

### Orders
| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/orders` | CUSTOMER | Place an order. Supports the `Idempotency-Key` header. Returns **201** |
| GET | `/orders` | any | List orders. Customers only ever see their own. Staff and admins can filter by `customerId` and `status`. Paging is zero-based |
| GET | `/orders/{id}` | owner, STAFF, ADMIN | Order details |
| PUT | `/orders/{id}/items` | owner | Replace the order's lines (PENDING orders only) |
| POST | `/orders/{id}/cancel` | owner, STAFF, ADMIN | Cancel an order |
| PATCH | `/orders/{id}/status` | STAFF, ADMIN | Fulfilment transitions (`SHIPPED`, `DELIVERED`) |
| POST | `/orders/{id}/refund` | STAFF, ADMIN | Refund an order |
| DELETE | `/orders/{id}` | ADMIN | Hard-delete a **test** order (used by QA to clean up) |
| GET | `/orders/{id}/history` | owner, STAFF, ADMIN | Audit trail, oldest first |

### Payments
| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/orders/{orderId}/payments` | owner | Pay for an order. Returns **201** |
| GET | `/orders/{orderId}/payments` | owner, STAFF, ADMIN | Payment attempts |

For testing, card token `tok_visa` succeeds and any token starting with `tok_decline` is declined.

### Discounts
| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/discounts` | ADMIN | List all discount codes |
| GET | `/discounts/validate?code=X&subtotal=Y` | any authenticated | Preview a code at checkout |
| POST | `/discounts` | ADMIN | Create a code |
| PATCH | `/discounts/{id}` | ADMIN | Update a code |

### Customers & users
| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/customers` | STAFF, ADMIN | List customers |
| GET | `/customers/me` | CUSTOMER | Own profile |
| GET | `/customers/{id}` | owner, STAFF, ADMIN | Customer profile |
| PUT | `/customers/{id}` | owner, ADMIN | Replace name and contact email. Only ADMIN may change `tier` |
| GET | `/users/me` | any | Own account |
| PATCH | `/users/me` | any | Change login email and/or password |

### Reports (ADMIN)
| Method | Path | Description |
|---|---|---|
| GET | `/reports/sales-summary?from=2026-09-01&to=2026-09-30` | Order count, revenue, and revenue by category for the date range |
| GET | `/reports/top-products?from=…&to=…&limit=10` | Best-selling products |
| GET | `/reports/low-stock?threshold=5` | Same as the inventory endpoint |

---

## Business rules

These rules describe how the system is **meant** to behave. If the code disagrees, the rule is the reference.

### Catalogue
* **BR-01** Only admins can create, update or deactivate products.
* **BR-02** SKUs are unique and match `AAA-9999` (3 capital letters, dash, 4–6 digits).
* **BR-03** Prices are positive amounts in EUR with two decimal places.
* **BR-04** Deactivated products stay visible on historical orders but cannot be ordered.
* **BR-05** A price change applies to orders placed **after** the change. Existing orders keep the price they were placed at.

### Inventory
* **BR-10** Neither `quantityOnHand` nor `quantityReserved` can ever be negative.
* **BR-11** Available stock (`onHand - reserved`) can never be negative, so a product cannot be oversold, even under concurrent checkouts.
* **BR-12** A stock adjustment cannot set `onHand` below the quantity currently reserved.
* **BR-13** Placing an order **reserves** stock. Paying **commits** it (on hand and reserved both decrease). Cancelling a **pending** order releases the reservation. Cancelling a **paid** order returns the committed stock to on hand.

### Orders
* **BR-20** An order has at least one line, and every line has a quantity ≥ 1. There are at most 50 lines.
* **BR-21** Each product appears on at most one line. Multiple lines for the same product are merged.
* **BR-22** `subtotal = Σ(unitPrice × quantity)`, `total = subtotal − discount`. All amounts are rounded to 2 decimal places (HALF_UP), and the total is never negative.
* **BR-23** Order numbers (`ORD-yyyyMMdd-nnnnn`) are unique, including across restarts and across multiple application instances.
* **BR-24** If a client repeats `POST /orders` with the same `Idempotency-Key`, it receives the original order and no second order is created. Keys are scoped to the customer.
* **BR-25** Items can only be changed while an order is `PENDING`. Changing items re-prices the order and adjusts the reservations. A discount code that is already on the order is not counted as used a second time.
* **BR-26** Allowed status transitions are exactly those shown in the lifecycle diagram. Anything else is rejected with **409**.
* **BR-27** Orders cannot be modified or cancelled after they have shipped.
* **BR-28** Customers can see and act on **only their own** orders.

### Reservations
* **BR-30** A reservation lasts **15 minutes**.
* **BR-31** When a reservation expires, its stock is released and the pending order is cancelled automatically.
* **BR-32** An order whose reservations have expired cannot be paid.
* **BR-33** Reservation times are evaluated in UTC, whatever the server's time zone.

### Payments
* **BR-40** Only `PENDING` orders can be paid. Paying a `CANCELLED` order is rejected.
* **BR-41** The payment amount must equal the order total (`59.9` and `59.90` are the same amount).
* **BR-42** An order is charged at most once, even if the client double-clicks or retries after a timeout.
* **BR-43** A successful charge, the stock commitment and the status change to `PAID` happen together. If one of them fails, none of them take effect (the charge itself is compensated manually by finance).

### Discounts
* **BR-50** Discount codes are unique (case-insensitive).
* **BR-51** A code is valid only if it is active, within `validFrom`/`validUntil` (inclusive), below `maxUses`, and the subtotal meets `minOrderAmount`.
* **BR-52** A code with `maxUses = 3` can be used exactly 3 times.
* **BR-53** Percentage discounts are calculated on the subtotal and rounded HALF_UP to cents. A fixed-amount discount never reduces the total below zero.
* **BR-54** Only one discount code can be applied per order.

### Refunds
* **BR-60** `PAID` orders can be refunded in full. `DELIVERED` orders can be refunded within 30 days of delivery.
* **BR-61** A refund creates a `REFUNDED` payment record for the refunded amount.
* **BR-62** Refunding a `PAID` (not yet shipped) order returns its stock. Refunding a `DELIVERED` order does not, because returned goods go through inspection first.

### Customers & accounts
* **BR-70** Emails are unique, case-insensitively.
* **BR-71** Changing the password or login email requires the current password.
* **BR-72** Customers cannot change their own tier.
* **BR-73** API responses never include password hashes or other credentials.

### Reporting
* **BR-80** Revenue is the sum of totals of orders in `PAID`, `SHIPPED` or `DELIVERED` status. The date range is inclusive at both ends and uses the order creation date.
* **BR-81** Revenue by category sums line totals per product category.

---

## Shipped features

All tickets from the original backlog are done as of 2026-10-14.

| Ticket | Feature | Notes |
|---|---|---|
| OH-231 | Refunds (BR-60 – BR-62) | PAID refunds restock, DELIVERED refunds don't (goods go through inspection), 30-day window enforced on DELIVERED |
| OH-240 | Product search: price range + case-insensitive name | Case-insensitive name match and independent min/max price filters |
| OH-244 | Top products report | Ranked by revenue, counting only PAID/SHIPPED/DELIVERED orders (BR-80) |
| OH-251 | Order history (audit trail) endpoint | `GET /orders/{id}/history`, scoped to the order's own customer |
| OH-255 | Automatic reservation expiry (BR-31) | Releases stock and cancels the order, not just the reservation row; scheduling was previously never enabled |
| OH-262 | Edit order items (BR-25) | Stock reservations reconciled by quantity delta; restricted to the order's own customer |
| OH-270 | Self-service profile update (BR-71) | Changing email or password now requires the current password |

---

## Error handling contract

Every error response has the same shape:

```json
{
  "timestamp": "2026-09-24T10:15:30Z",
  "status": 409,
  "error": "Conflict",
  "message": "Order 42 cannot move from SHIPPED to CANCELLED",
  "path": "/api/orders/42/cancel"
}
```

| Situation | Status |
|---|---|
| Malformed JSON, failed validation, bad parameter type | 400 |
| Missing, invalid or expired token | 401 |
| Authenticated but not allowed | 403 |
| Resource not found | 404 |
| Invalid state transition, insufficient stock, duplicate resource | 409 |
| Other business-rule violation (e.g. invalid discount code) | 422 |
| Unexpected failure | 500. The message must not reveal internal details |

---

## Testing

```bash
mvn test                       # everything (Testcontainers tests are skipped if Docker is not running)
mvn test -Dtest='*ServiceTest' # unit tests only
```

* Unit tests use JUnit 5, Mockito and AssertJ.
* The integration tests (`integration/`, `repository/`) start PostgreSQL through Testcontainers and run the real Flyway migrations and seed data.

---

## Operations

* **Management port.** Actuator runs on port `8081` (`MANAGEMENT_PORT`), separate from the API on `8080`. Keep it off the load balancer; only probes and the metrics scraper inside the cluster should reach it. It exposes:
  * `GET /actuator/health/liveness` and `GET /actuator/health/readiness` for the Kubernetes probes. Readiness also checks the database.
  * `GET /actuator/prometheus` for metrics scraping.
  * `GET /actuator/info`.

  No other endpoints are exposed. Health responses never include component details.
* **Logs.** Locally, logs are plain text. With the `prod` profile, each line is a JSON object in [Elastic Common Schema](https://www.elastic.co/guide/en/ecs/current/index.html) format, and carries `service.name`, `service.version`, `service.environment` (`DEPLOY_ENV`) and `service.node.name` (the pod's `HOSTNAME`).
* **Correlation.** Each log line written during a request carries:
  * `traceId` and `spanId` from the W3C `traceparent` header, or newly generated ones.
  * `requestId` from `X-Request-Id`, which every response also returns. A caller-supplied request id is kept only if it has at most 64 characters and contains only letters, digits, `.`, `_` and `-`.

  Scheduled jobs get their own trace ids.
* **Traces.** Traces cover the HTTP request, every JDBC query and the PSP charge (`orderhub.psp.charge`). They are exported over OTLP/HTTP only when `MANAGEMENT_OTLP_TRACING_ENDPOINT` is set (for example `http://otel-collector:4318/v1/traces`). Production samples 10% of traces (`TRACING_SAMPLING_PROBABILITY`). Bound SQL parameters are never recorded.
* **Metrics.** Besides the standard HTTP, JVM, Hikari and JDBC metrics, every metric carries `application="orderhub"`. The service also publishes these:

  | Metric | Meaning |
  |---|---|
  | `orderhub_orders_placed_total` | Orders created. Idempotent replays are not counted. |
  | `orderhub_orders_expired_total` | Pending orders cancelled because their reservation expired. |
  | `orderhub_orders_expiry_failures_total` | Expiry attempts that failed and will be retried. A rate that stays above zero means some orders are stuck. |
  | `orderhub_orders_refunded_total` | Refunds issued. |
  | `orderhub_psp_charge_seconds{outcome}` | PSP latency and call count, by `approved`, `declined` or `error`. |
* **Audit.** Audit events are written in the same transaction as the change they describe. A change is therefore never stored without its audit event, and the reverse is also true. If the audit write fails, the change fails too.
* Payments are processed by `FakePaymentGateway`, which adds ~400 ms of simulated latency.
* In production the service runs as **3 replicas** behind a load balancer. Containers use the `Europe/Dublin` time zone.
