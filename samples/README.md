# Capstead Sample

A tiny, clone-and-run Spring Boot app that shows what Capstead does — **no AI credentials required**
(a `DemoModel` stands in for a real LLM client and records realistic token usage).

## Run

```bash
mvn spring-boot:run
```

Then open the dashboard:

```
http://localhost:8080/capstead
```

The app exercises every capability once at startup, so the dashboard and these endpoints are already
populated:

```
GET http://localhost:8080/actuator/capabilities         # the catalog
GET http://localhost:8080/actuator/capabilityscorecard  # invocations, latency, tokens, cost
GET http://localhost:8080/actuator/capabilityexecutions # execution history (ids, parent, invocations)
```

Trigger more executions on demand:

```
GET http://localhost:8080/demo/course?subject=Kafka
GET http://localhost:8080/demo/tutor?question=Explain%20backpressure
GET http://localhost:8080/demo/report
GET http://localhost:8080/demo/budget?product=Capstead   # watch a @DailyBudget get enforced
```

## What it demonstrates

| Capability | Declared via | Notes |
|---|---|---|
| **Generate Lesson** | `@Capability` annotation | has a `@DailyBudget("$10")` |
| **Lesson Tutor** | `@Capability` annotation | different model (`nova-pro`) |
| **Build Course** | `@Capability` annotation | **composed** — calls *Generate Lesson* twice → a parent-child execution **tree** |
| **Summarize Report** | **YAML config** (`capstead.capabilities`) | no annotation on the bean at all |
| **Generate Quiz** | **declarative** (`@CapabilityClient`) | no method body — provider-neutral `CapabilityModelInvoker` |
| **Draft Tagline** | `@Capability` annotation | tiny `@DailyBudget("$0.02")` — **budget enforcement** demo |

### Budget enforcement (governance in action)

`GET /demo/budget` calls the budgeted **Draft Tagline** capability in a loop and returns the exact
call at which Capstead blocked it once the day's spend hit the `@DailyBudget`. The method itself has
**no budget code** — the runtime enforces it. Example output:

```
call 1: ok
call 2: ok
call 3: ok
call 4: BLOCKED — budget=0.02, spent today=0.0243

Capstead enforced the daily budget after 3 successful call(s).
```


It also shows **per-model cost** (priced from `capstead.cost`), **per-model invocations** on each
execution, and the **execution recorder** settings (`capstead.executions.*`). Grab an execution id
from `/actuator/capabilityexecutions` and open `/actuator/capabilityexecutions/{id}` to see the
`Build Course` tree with its child `Generate Lesson` executions.

## Point at a specific Capstead version

The pom uses `${capstead.version}` (default `0.3.0`). To try another:

```bash
mvn -Dcapstead.version=0.3.0-rc1 spring-boot:run
```
