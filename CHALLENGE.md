# OrderHub: Engineering Challenge

You have just joined the team that owns OrderHub. The previous owners have moved on. Customer support keeps escalating strange order totals and stock problems, the security team has booked a review, and product wants the unfinished features delivered.

The code looks like any other production service. Nothing is labelled as broken. Some code that looks suspicious is correct, and some code that looks clean is not. **`README.md` is the specification.** When the code and the README disagree, treat the README as correct and the code as suspect. The README could be wrong too, so if you think it is, argue your case.

Work through the stages in order. After each stage, send your mentor your notes, findings, or a diff/PR description.

---

## Stage 1: Codebase exploration  ← **start here**

Don't fix anything yet. Build a mental model of the service.

Deliverable: a short write-up (bullets are fine) that covers:

1. **Architecture**: the layers, and what each package is responsible for. What does a request pass through from the servlet container to the database and back? Name the filters, interceptors and proxies you think are involved.
2. **Entities & relationships**: sketch the domain model (text or a diagram). For every association, record the owning side, the fetch type and the cascade settings. Where is the domain model *different* from the database schema in `V1__initial_schema.sql`?
3. **Main business flows**: trace these through the code, class by class and method by method:
   * place order
   * pay for order
   * cancel order
   * reservation expiry
   For each flow, mark where transactions start and end, which calls run in a different thread, and which calls go through a Spring proxy.
4. **Security model**: how does a request become authenticated? Where are authorisation decisions made (URL rules, method security, service code)? List every place an ownership check happens.
5. **Configuration**: which beans and properties affect runtime behaviour (caching, async, scheduling, profiles, CORS, actuator)? What changes under the `prod` profile?
6. **Tests**: what is tested at each level (unit, repository, integration), and what important behaviour has no tests?
7. **First impressions**: anything that surprised or confused you. You don't need proof yet.

## Stage 2: Initial investigation

1. Start PostgreSQL and the application. Read the startup log from top to bottom, including the warnings.
2. Run `mvn test`. Record which tests pass, which fail, and which are skipped. **Don't fix anything yet.** For each failure, write down whether you think the production code is wrong, the test is wrong, or both.
3. Use Swagger UI, Postman or curl to walk through the main flows as each of the seeded users. Turn on SQL logging for part of the session.
4. List the behaviours that look suspicious, each with the evidence (request, response, log line or SQL).

## Stage 3: Bug hunting

Build your own issue list. For each issue, record:

* title
* category (Java / Spring / JPA / DB / API / security / concurrency / business logic / tests / config)
* severity (how bad it is in production) and your confidence
* evidence, and how you would **prove** it with a test
* the business rule it violates, if any

Your mentor will not tell you how many issues there are, or whether you have found them all.

## Stage 4: Fixing

Fix the issues, one logical change per commit/PR. Every bug fix comes with a test that fails before the fix and passes after it. Your mentor reviews each change the way a senior engineer reviews a PR.

## Stage 5: Missing features

Finish the in-progress tickets listed in the README (OH-231, 240, 244, 251, 255, 262, 270), following the business rules.

## Stage 6: Production hardening

Look at the service as if it were about to face real traffic on 3 replicas:
security, concurrency, transactions, database performance (indexes, N+1, pagination), scalability, error handling, and observability.

## Stage 7: Code review

Review the whole repository as if it had been submitted to you as a PR. What would you change, in what order, and why?

---

### Asking for help

Tell your mentor what you have found and ask for a hint. There are four hint levels:

1. **Conceptual**: which direction to think in
2. **Component**: the Spring/Java concept or layer involved
3. **Location**: the class or method, or the pattern to look for
4. **Solution**: an explanation of the problem and a possible fix (only on request, or after a serious attempt)
