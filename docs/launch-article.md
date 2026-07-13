# Governing AI capabilities in Spring Boot: from `@Capability` to durable execution history

*Draft for Foojay / dev.to — the canonical write-up to link from every launch post.*

---

Frameworks like **Spring AI** and **LangChain4j** have made *calling* an LLM from Java easy: provider
abstraction, structured output, tool calling. But once you have AI features spread across several
services and teams, a different question shows up in every architecture review — and nobody can
answer it:

> *What AI capabilities do we have, who owns them, which version is live, and what do they cost?*

That gap is what **[Capstead](https://github.com/satya-anguluri/capstead)** fills. It is **not** another
AI framework. It's a small Spring Boot library that sits *around* whatever you already use to call the
model, and turns your methods into **governed, versioned, observable business capabilities** — with a
catalog, per-capability cost and budgets, and (new in 0.3.x) a durable execution recorder.

> Spring AI tells you about a model call. **Capstead tells you about the business capability.**

## One annotation

```java
@Service
class LessonService {

    @Capability(name = "Generate Lesson", domain = "Learning",
                owner = "Content Team", version = "2")
    @DailyBudget("$25")
    public Lesson generateLesson(String topic) {
        // your normal logic — Spring AI, LangChain4j, a raw HTTP call, anything
        return chatClient.prompt().user(topic).call().entity(Lesson.class);
    }
}
```

Add `capstead-starter`, and that method is now a first-class capability. Hit the actuator:

```
GET /actuator/capabilities        # the catalog: name, domain, owner, version, tags
GET /actuator/capabilityscorecard # invocations, success rate, latency, tokens, cost
```

No code in your method measured anything. Prefer not to annotate? Declare capabilities in YAML instead
— ideal for third-party or generically-named methods — and the two styles coexist:

```yaml
capstead:
  capabilities:
    - { name: "Generate Lesson", bean: lessonService, method: generate, domain: Learning }
```

## Cost, attributed to a capability

Capstead does **not** measure tokens itself — Spring AI already does. Capstead *attributes* that
per-model-call data to the **business capability**, so a scorecard shows real token counts and
estimated cost per capability, and `@DailyBudget` blocks further calls once the day's spend is hit.
Configure per-model pricing and you get cost with zero code in your methods.

## New in 0.3.x: the durable execution recorder

Here's the part that turns static metadata into something you can observe over time. Every
`@Capability` call becomes a first-class `CapabilityExecution`, and 0.3.x makes those **structured and
durable**:

- **Per-model invocations.** A capability that calls the model several times captures each call, so
  cost is attributed *per model*, not just per capability.
- **Execution trees.** When one `@Capability` calls another, the nested execution is linked to its
  parent automatically — an execution tree with no workflow engine. `GET /actuator/capabilityexecutions/{id}`
  walks it.
- **Durable, cross-instance history.** Add `capstead-jdbc` and executions persist to your database
  (PostgreSQL, MySQL, H2). Scorecards and trees survive restarts and aggregate across every instance —
  which matters the moment you run more than one replica.
- **Privacy by default.** Inputs/outputs aren't stored unless you opt in, with a pluggable redactor.

```yaml
capstead:
  jdbc:
    enabled: true
    retention-days: 90
```

## Export capabilities as governed MCP tools

Capstead can publish your capabilities as [Model Context Protocol](https://modelcontextprotocol.io)
tools, so an MCP client or an LLM can discover and call them — while they stay **versioned, owned, and
budget-enforced**. Plain MCP tools carry none of that governance; Capstead carries it through.

## What Capstead is *not*

It is not a way to call LLMs, and not an alternative to Spring AI or LangChain4j. It reuses them. It
adds the layer they deliberately leave out: capability registry, versioning, ownership, cost/budgets,
execution history, and discovery — the *governance* around execution.

## It runs in production

Capstead is dogfooded at **[engineerprep.io](https://engineerprep.io)**, an AI-powered
technical-interview-prep platform on Spring Boot, governing about a dozen capabilities across two
domains and attributing cost across Anthropic Claude and Amazon Nova (Bedrock) — all on the
`/capstead` dashboard.

## Try it

```xml
<dependency>
  <groupId>io.capstead</groupId>
  <artifactId>capstead-starter</artifactId>
  <version>0.3.1</version>
</dependency>
```

A clone-and-run sample (no AI credentials needed) is in
[`samples/`](https://github.com/satya-anguluri/capstead/tree/main/samples). Star the repo, try it on a
real service, and tell me what governance you wish it had next.
