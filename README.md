# Capstead

**A Spring Boot capability platform — registry, governance, versioning, and observability for your business capabilities.**

Capstead is **not** another AI framework. It's the governance layer that sits *around* your AI (Spring AI, LangChain4j, or anything else). You keep writing normal Spring Boot methods; Capstead turns them into **governed, discoverable, observable, versioned business capabilities** — with per-capability cost tracking and budgets — from a single annotation.

> Spring AI tells you about one model invocation. Capstead tells you about the **business capability**: how often it ran, which model it used, how many tokens it consumed, how much it cost, whether it succeeded, and which version executed.

---

## Why

Modern apps expose AI capabilities across many services and teams, and nobody can answer: *what capabilities exist, who owns them, what version is live, and what do they cost?* Frameworks like Spring AI and LangChain4j solve **execution** brilliantly. Capstead solves everything **around** execution:

| | Spring AI / LangChain4j | Capstead |
|---|---|---|
| Provider abstraction, structured output, tool calling | ✅ | *reuses them* |
| Capability registry + metadata + **versioning** | ❌ | ✅ |
| `/actuator/capabilities` discovery | ❌ | ✅ |
| First-class execution records + **cost/scorecards** | ❌ (per model-call only) | ✅ |
| **Daily budgets** & governance | ❌ | ✅ |
| Enforced provider hiding | ❌ | ✅ |

---

## Install

```xml
<dependency>
    <groupId>io.capstead</groupId>
    <artifactId>capstead-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

That's it. Auto-configuration wires everything; no code changes beyond annotating a method.

---

## Quick start

Annotate any Spring bean method:

```java
@Service
class LessonService {

    @Capability(
        name    = "Generate Lesson",
        domain  = "EngineerPrep",
        owner   = "Content Team",
        version = "2",
        tags    = {"lesson", "java"}
    )
    @DailyBudget("$25")
    public Lesson generateLesson(String topic) {
        // your normal logic — call Spring AI, LangChain4j, anything
        return chatClient.prompt().user(topic).call().entity(Lesson.class);
    }
}
```

Now hit the actuator:

```
GET /actuator/capabilities        # the catalog: name, domain, owner, version, tags
GET /actuator/capabilityscorecard # invocations, success rate, latency, tokens, cost
GET /actuator/capabilitymetrics   # Micrometer-backed stats (Prometheus-ready)
```

…and open the dashboard:

```
http://localhost:8080/capstead/
```

Expose the endpoints in `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: capabilities,capabilityscorecard,capabilitymetrics
```

---

## Automatic cost tracking

Capstead does **not** measure tokens itself — it *attributes* Spring AI's existing token/model data to the business capability. Add the bridge:

```xml
<dependency>
    <groupId>io.capstead</groupId>
    <artifactId>capstead-spring-ai</artifactId>
    <version>0.1.0</version>
</dependency>
```

Configure per-model pricing (price per million tokens):

```yaml
capstead:
  cost:
    models:
      claude-sonnet:
        input-per-million-tokens: 3.00
        output-per-million-tokens: 15.00
```

Now every capability's scorecard shows real token counts and estimated cost — with **no code in your method**. Once a capability's `@DailyBudget` is reached, further calls are blocked with a `CapabilityBudgetException` until the next day.

> Not using Spring AI? The enrichment seam is open: call
> `CapabilityExecutionContext.recordModel(...)` / `recordTokens(...)` / `recordCost(...)`
> from anywhere that makes the model call.

---

## Modules

| Module | Purpose |
|---|---|
| `capstead-annotations` | `@Capability`, `@DailyBudget` — dependency-free |
| `capstead-core` | Public model: `CapabilityMetadata`, `CapabilityExecution`, `CapabilityScorecard` |
| `capstead-runtime` | Registry, discovery, execution capture, cost, budgets |
| `capstead-starter` | Spring Boot auto-configuration + actuator endpoints + dashboard |
| `capstead-spring-ai` | Optional bridge: token/model attribution from Spring AI observations |

---

## Design principles

1. **Provider-agnostic** — Capstead never exposes `ChatClient`, model names, or tokens in the capability API. A startup guard rejects any `@Capability` whose signature leaks a provider type.
2. **Business-first** — you think in *Generate Lesson*, *Approve Payment*, not *prompts* and *temperature*.
3. **Convention over configuration** — one annotation exposes a governed capability.
4. **Spring Boot native** — auto-configuration, dependency injection, Actuator, Micrometer.

---

## Status

Early (`0.1.0`). The open-source core is complete and tested: registry, metadata, versioning, discovery, first-class executions, cost estimation, daily budgets, three actuator endpoints, a dashboard, and the Spring AI bridge.

## License

Apache License 2.0.
