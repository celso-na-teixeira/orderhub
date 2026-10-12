# Concurrency and locking

How OrderHub keeps data correct when several requests run at the same time, and the rules to follow when adding code.

## The rule

Lock where the shared data lives.

- Data in the database is protected by the database: row locks, conditional updates, unique indexes, sequences.
- Data that exists only in the memory of one process is protected by JVM tools: atomics, concurrent collections, `synchronized`.

OrderHub keeps all its business data in the database, so it uses no `synchronized` and no `Lock` objects.

## Project rules

1. **Stock changes go through a locked read.** Any code that changes an `InventoryItem` loads it with `InventoryRepository.findByProductIdForUpdate`. `InventoryService` does this in `lockItem`, which also refreshes the entity after locking it. Plain reads use `findByProductId`.
2. **Lock stock rows in product-ID order.** When one request touches several products, it processes them sorted by product ID. `OrderService.mergeLines`, `OrderService.inProductOrder` and `PaymentService.completeOrder` do this. Two requests that lock the same rows in different orders can deadlock.
3. **A lock needs a read-write transaction.** A row lock is held until the transaction ends, so the locking call must be made inside `@Transactional`, and not from a `readOnly = true` method. See `TRANSACTIONS.md`.
4. **A rule that must never be broken gets a database constraint.** Application checks can be raced; a unique index cannot. The check in the service gives a clear error message, and the index is the guarantee.
5. **Check and change in one statement where possible.** If a counter has a limit, increment it with a single `UPDATE ... WHERE` that includes the limit, as `DiscountRepository.incrementUsage` does.
6. **Do not protect database data with `synchronized`.** See [Why not `synchronized`](#why-not-synchronized).

## Mechanisms in use

| Mechanism | Where | What it guarantees | Rule |
|---|---|---|---|
| Row lock (`SELECT ... FOR UPDATE`) | `InventoryRepository.findByProductIdForUpdate` | One transaction at a time changes a product's stock | BR-11 |
| Row lock | `CustomerRepository.findByUserIdForUpdate`, used by `placeOrder` when an `Idempotency-Key` is sent | A repeated request waits for the first one, then returns its order | BR-24 |
| Conditional update | `DiscountRepository.incrementUsage` | A code is never used more than `maxUses` times | BR-52 |
| Unique index | `uq_discounts_code_lower` | Discount codes are unique, ignoring case | BR-50 |
| Unique index | `uq_orders_customer_idempotency_key` | One order per customer and key | BR-24 |
| Sequence | `order_number_seq`, read by `OrderNumberGenerator` | Order numbers are unique across restarts and instances | BR-23 |
| Version column (`@Version`) | `Order`, `Product` | A save based on stale data fails instead of overwriting | |

## How a row lock works

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select i from InventoryItem i where i.product.id = :productId")
Optional<InventoryItem> findByProductIdForUpdate(@Param("productId") Long productId);
```

1. The annotation makes Hibernate run `SELECT ... FOR UPDATE`, and Postgres locks that row.
2. A second transaction that runs the same query for the same row waits inside the database.
3. The lock is released when the first transaction commits or rolls back. There is no unlock call.
4. The waiting transaction then continues and reads the data the first one left behind.

For stock, this means the second checkout sees the reduced quantity and gets the normal "insufficient stock" response.

## Pessimistic and optimistic locking

| | Pessimistic (row lock) | Optimistic (`@Version`) |
|---|---|---|
| How | Lock the row before reading it | Check at save time that nobody else changed the row |
| On conflict | The second transaction waits | The second transaction fails and must retry |
| Suits | Rows that are often changed at the same time, such as stock | Rows that are rarely changed at the same time, such as a product's details |
| Cost | Requests queue behind each other | Failed requests need handling |

## Why not `synchronized`

- **It only covers one JVM.** Each instance of the application has its own locks. A database lock is shared by every instance.
- **It releases too early.** `synchronized` ends when the method returns, but Spring commits the transaction after the method returns. Another thread can read the old value in between.
- **It is bypassed by other writers.** A scheduled job, a script or another service writing to the same table does not take the lock.
- **It locks too much.** A `synchronized` method blocks every caller. A row lock only blocks requests for the same row.

A JVM lock can be made to work for database data with a single instance, if it wraps the whole transaction and every writer uses it. It stops working without warning when a second instance is started, so OrderHub does not rely on it.

## When JVM locks are the right tool

Use them for state that lives only in the memory of one process.

| Use case | Typical tool |
|---|---|
| A counter or statistic held in memory | `AtomicLong`, `LongAdder` |
| A shared in-memory map or cache | `ConcurrentHashMap` |
| A group of in-memory fields that must change together | `synchronized` or `ReentrantLock` |
| Creating something expensive only once | `computeIfAbsent`, `synchronized` lazy initialisation |
| Limiting how many threads do something at once | `Semaphore` |

`OrderNumberGenerator` used to be a counter in memory. Making it atomic would have fixed its thread safety, but not the requirement: order numbers must be unique across restarts and instances, which makes them shared, durable data. That is why it now reads a database sequence.

## Pitfalls

- **A locking query does not refresh an entity that is already loaded.** If the transaction has already loaded a row, a later `SELECT ... FOR UPDATE` takes the lock but Hibernate hands back the copy it already has, with the old values. Call `entityManager.refresh(entity)` after locking, as `InventoryService.lockItem` does. `InventoryLockingIntegrationTest` covers this.
- **Deadlocks.** Two transactions that each hold a row the other wants will deadlock; the database aborts one of them. Locking rows in a fixed order prevents it (project rule 2).
- **Long transactions hold locks.** A lock lasts until the transaction ends. `PaymentService.pay` calls the payment gateway inside its transaction, so the stock rows it locks afterwards are held only briefly, but the transaction itself stays open during the gateway call.
- **A lock taken outside a transaction does nothing useful.** It is released as soon as the statement finishes.
- **Check-then-insert is not safe on its own.** Two requests can both pass the check. Back it with a unique index, or lock a parent row first as `placeOrder` does with the customer.
