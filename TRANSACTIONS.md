# Transactions in services

How `@Transactional` works, the ways it can be applied, and the convention OrderHub uses.

## The project standard

Every service class has `@Transactional` on the class. Methods that only read are marked `@Transactional(readOnly = true)`.

```java
@Service
@Transactional
public class CustomerService {

    @Transactional(readOnly = true)
    public Customer getById(Long id) { ... }

    public Customer update(Long id, UpdateCustomerRequest request, AppUserDetails currentUser) { ... }
}
```

When you add a method to a service:

- If it changes data, add nothing. It inherits the class-level read-write transaction.
- If it only reads, add `@Transactional(readOnly = true)`.
- Do not put `@Transactional` on private methods or rely on it for calls within the same class. See [Pitfalls](#pitfalls).

## How `@Transactional` works

### What it does

Spring wraps the service in a proxy. When a caller invokes a transactional method, the proxy opens a database transaction, runs the method, and then commits. If the method throws, the proxy rolls back instead.

Inside a transaction, entities loaded from the database are tracked. Any change made to them is written at commit, without calling `save()`. This is why `CustomerService.update` only calls setters.

### Class level and method level

| Where | Effect |
|---|---|
| On the class | Applies to every public method of the class |
| On a method | Replaces the class-level setting for that method |

The method-level annotation replaces the class-level one completely; the two are not merged.

### `readOnly = true`

It tells Hibernate not to look for changes to write. It is a small optimisation and a statement of intent.

The consequence to remember: changes made to entities inside a read-only transaction are silently not saved. A write method that accidentally runs read-only looks like it works and persists nothing.

### Calls between services (propagation)

The default propagation is `REQUIRED`: join the caller's transaction if there is one, otherwise start a new one.

| Propagation | Behaviour | Used here |
|---|---|---|
| `REQUIRED` (default) | Join the current transaction or start one | Everywhere, unless stated |
| `REQUIRES_NEW` | Always start a separate transaction; the caller's is paused | `AuditService.record` |

Because of `REQUIRED`, `OrderService.placeOrder` calling `PricingService.price`, `InventoryService.reserve` and `ReservationService.create` is one transaction. If any of them fails, the whole order is rolled back.

When a method joins an existing transaction, the outer transaction's settings win. `DiscountService.findApplicable` is read-only on its own, but when called while placing an order it runs inside the order's read-write transaction.

### What causes a rollback

| The method throws | Default result |
|---|---|
| A `RuntimeException` or an `Error` | Rollback |
| A checked exception | Commit |

All of this project's exceptions (`BusinessRuleException`, `ResourceNotFoundException`, ...) are runtime exceptions, so they roll back.

The defaults can be changed per method with `rollbackFor` and `noRollbackFor`.

## Ways to apply it

| Style | Looks like | Trade-off |
|---|---|---|
| Class-level read-write, read-only overrides | `@Transactional` on the class, `readOnly = true` on readers | A forgotten override costs a little performance. **This is the project standard.** |
| Class-level read-only, read-write overrides | `@Transactional(readOnly = true)` on the class, `@Transactional` on writers | A forgotten override means a write is silently lost |
| Per-method only | Nothing on the class, every public method annotated | A forgotten annotation means no transaction at all |

The standard was chosen because its failure mode is the least harmful.

## Exceptions to the standard

| Class | What is different | Why |
|---|---|---|
| `PaymentService.pay` | `@Transactional(noRollbackFor = PaymentDeclinedException.class)` | A declined card saves a `FAILED` payment record and then throws. That record must be kept, so this one exception commits. Every other failure rolls back the payment record, the stock commitment and the status change together (BR-43). |
| `AuditService.record` | `@Async` with `REQUIRES_NEW` | Audit events are written on another thread in their own transaction, so they do not slow down or roll back with the request. |
| `ReportService` | `@Transactional(readOnly = true)` on the class | Every method is a read, so there is nothing to override. |
| `DiscountCalculator`, `FakePaymentGateway` | No annotation | They do not touch the database. |

## Pitfalls

**Calling a transactional method from the same class does nothing.** The proxy only sees calls that come from outside the bean. If `pay()` calls `this.completeOrder()`, any annotation on `completeOrder` is ignored and it runs with whatever `pay()` has. Put the annotation on the method that is called from outside.

**Private methods are never transactional.** The proxy cannot intercept them. An annotation there is ignored without warning.

**Catching an exception does not always save the transaction.** If an inner transactional method throws and the exception leaves that method, the shared transaction is marked for rollback, even if the caller catches the exception. The commit then fails with `UnexpectedRollbackException`.

**Modifying queries need a transaction.** A repository method with `@Modifying`, such as `DiscountRepository.incrementUsage`, fails if it is called outside one.

**External calls inside a transaction hold a database connection.** `PaymentService.pay` calls the payment gateway while its transaction is open. That is acceptable with the fake gateway; with a slow real provider, charge first and open the transaction afterwards.

**Entities do not cross threads.** An entity loaded in one transaction should not be read from another thread. `AuditService.record` receives the caller's `Order` and reads it asynchronously, which can fail when lazy data is loaded.

**Lazy loading in controllers is not a transaction.** Mappers can read lazy associations in the controller only because `spring.jpa.open-in-view` is left at its default of `true`, which keeps the database session open for the whole request. Nothing changed there is saved.
