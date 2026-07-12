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
| Export capabilities as **governed MCP tools** | ❌ | ✅ |

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

## Export capabilities as MCP tools

Capstead can publish your governed capabilities as [Model Context Protocol](https://modelcontextprotocol.io) (MCP) tools — so an MCP client (or an LLM) can discover and call them, while they stay **versioned, owned, and budget-enforced**. Plain MCP tools have none of that governance; Capstead carries it through.

### Transport-agnostic tool model + actuator

```xml
<dependency>
    <groupId>io.capstead</groupId>
    <artifactId>capstead-mcp</artifactId>
    <version>0.1.0</version>
</dependency>
```

Each `@Capability` becomes an MCP tool: a stable `name@version`-derived tool id, a JSON input schema derived from the method signature, and a `governance` block (owner, domain, version, tags). Two actuator surfaces are added:

```
GET  /actuator/capabilitymcp          # tools/list — every capability as an MCP tool
GET  /actuator/capabilitymcp/{name}   # a single tool definition
POST /actuator/capabilitymcp/{name}   # tools/call — body {"arguments": { ... }}
```

Invocations route through the capability's governing proxy, so `@DailyBudget` and execution recording apply exactly as for a direct call.

### Serve over a live MCP server

To expose the tools over a real MCP transport (STDIO, SSE, Streamable-HTTP), add the bridge plus any Spring AI MCP server starter:

```xml
<dependency>
    <groupId>io.capstead</groupId>
    <artifactId>capstead-mcp-server</artifactId>
    <version>0.1.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server</artifactId>
</dependency>
```

`capstead-mcp-server` registers a Spring AI `ToolCallbackProvider` built from your capabilities; the MCP server starter discovers it and serves every capability automatically — no per-tool code.

---

## Modules

| Module | Purpose |
|---|---|
| `capstead-annotations` | `@Capability`, `@DailyBudget` — dependency-free |
| `capstead-core` | Public model: `CapabilityMetadata`, `CapabilityExecution`, `CapabilityScorecard` |
| `capstead-runtime` | Registry, discovery, execution capture, cost, budgets |
| `capstead-starter` | Spring Boot auto-configuration + actuator endpoints + dashboard |
| `capstead-spring-ai` | Optional bridge: token/model attribution from Spring AI observations |
| `capstead-mcp` | Optional: export capabilities as MCP tools + `/actuator/capabilitymcp` |
| `capstead-mcp-server` | Optional: serve capabilities over a live MCP server via Spring AI `ToolCallbackProvider` |

---

## Design principles

1. **Provider-agnostic** — Capstead never exposes `ChatClient`, model names, or tokens in the capability API. A startup guard rejects any `@Capability` whose signature leaks a provider type.
2. **Business-first** — you think in *Generate Lesson*, *Approve Payment*, not *prompts* and *temperature*.
3. **Convention over configuration** — one annotation exposes a governed capability.
4. **Spring Boot native** — auto-configuration, dependency injection, Actuator, Micrometer.

---

## Status

Early (`0.1.0`). The open-source core is complete and tested: registry, metadata, versioning, discovery, first-class executions, cost estimation, daily budgets, three actuator endpoints, a dashboard, the Spring AI bridge, and MCP export (tool model, actuator, and Spring AI MCP server bridge).

## License

Apache License 2.0.
