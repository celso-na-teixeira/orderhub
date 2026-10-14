# Time zones

When to use UTC, when to use a specific zone, and why the JVM's default zone is not used in server code.

## The rule

Do not use the JVM's default zone. Avoid `LocalDateTime.now()`, `LocalDate.now()` and `ZoneId.systemDefault()` with no argument.

The default zone depends on where the code runs, not on a business rule. A developer laptop in Lisbon runs at `+01:00` in summer, while a Docker container usually runs in UTC. The same code then behaves differently on each machine.

Before picking a type, decide whether the value is a moment in time or a time on someone's clock.

## Choosing a type and zone

| What you are modelling | Type | Zone |
|---|---|---|
| A moment: created/updated timestamps, audit events, token expiry, reservation timeouts | `Instant` (or `OffsetDateTime` in UTC) | UTC. Convert only for display. |
| A wall-clock rule: "valid until midnight on Dec 31", "shop closes at 18:00", a future appointment | `LocalDateTime` / `LocalTime` with an explicit `ZoneId` | The zone of the business or the user, stored or configured explicitly (e.g. `Europe/Lisbon`) |
| A date with no time: birthday, invoice date, report day | `LocalDate` | None for storage. "Today" still needs an explicit zone: `LocalDate.now(businessZone)` |
| A time shown to a person | Format the `Instant` in the viewer's zone | Usually the client's zone |

### Why UTC for moments

UTC has no daylight saving time. No hour repeats and no hour is skipped, so comparing two moments and measuring the time between them always gives the right result.

### Why an explicit zone for wall-clock rules

A rule like "expires at midnight" only makes sense on a specific clock. The zone is part of the rule, so it has to be stored or configured. It must not depend on the server.

For future events, store the local time together with the zone, not a converted instant. If the time-zone rules change before the event, for example a country dropping daylight saving time, the local time is still correct.

## When the default zone is acceptable

- Desktop or command-line tools, where the machine's clock is the user's clock.
- Log timestamps, which the logging framework formats on its own.

## Testing

Inject a `java.time.Clock` instead of calling `now()` directly. Tests can then fix the time and zone, and the zone used in production is explicit:

```java
LocalDateTime now = LocalDateTime.now(clock);
```

## In OrderHub

`Discount.validFrom` and `validUntil` are `LocalDateTime` values compared against `LocalDateTime.now(ZoneOffset.UTC)` in `DiscountService`. This is consistent, but a code an admin sets to expire at `2026-12-31T23:59` expires at 23:59 UTC. In Lisbon that is the same time in winter, but in summer it is an hour later, and for customers in other zones it differs as well.

These dates are wall-clock rules, so one of the following should be chosen:

- Store `validFrom`/`validUntil` as `Instant`, and have the admin UI convert from the admin's zone.
- Keep `LocalDateTime` and compare against `LocalDateTime.now(businessZone)`, with `businessZone` read from configuration (e.g. `app.business-zone: Europe/Lisbon`).

This has not been decided yet.
