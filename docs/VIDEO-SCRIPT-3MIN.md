# Capstead — 3-Minute YouTube Video (script + shot list)

**Format:** screen recording (IDE + terminal + dashboard) with voiceover.
**Runtime:** ~3:00. **Narration:** ~450 words (~150 wpm).
**Record at:** 1080p/60. Use the `capstead/samples` app (runs with no API keys) for all demo shots.

---

## Title options
- **Capstead: Governance & Observability for AI Capabilities in Spring Boot**
- **Give your Spring AI apps a control plane — Capstead in 3 minutes**
- **Stop shipping ungoverned AI. Capstead for Spring Boot.**

---

## Script (timed)

### 0:00–0:18 — Hook
**On screen:** A busy Spring `@Service` calling `ChatClient` / a model. Then cut to a clean dashboard with capability cards.
**VO:**
> "Every Spring team is shipping AI features. Almost none can answer: which AI capabilities do we run? Who owns them? How reliable are they, and what do they cost? Capstead answers all of that — with one annotation."

### 0:18–0:42 — The problem
**On screen:** Bullets fade in — *No catalog · No versioning · No owner · No cost per feature · Just scattered model calls.*
**VO:**
> "Spring AI gives you great per-call metrics — tokens and latency for each model request. But a business runs on *capabilities*, not model calls. 'Generate Lesson.' 'Approve Payment.' There's no registry, no owner, no version, no cost attributed to the business capability. Capstead is that missing governance layer — and it sits on top of Spring AI, it doesn't replace it."

### 0:42–1:05 — What it is
**On screen:** One dependency in `pom.xml` (`capstead-starter`). Then the `@Capability` annotation on a method.
**VO:**
> "Add one dependency, and annotate any Spring bean method with `@Capability` — give it a name, a domain, an owner, a version. That's it. Capstead doesn't run your model; your code still does. Capstead governs everything *around* it: registration, discovery, versioning, policy, and observability."

### 1:05–1:55 — Demo: the payoff
**On screen (screen-record live):**
1. `GET /actuator/capabilities` → the catalog JSON.
2. Open `http://localhost:8080/capstead/` → the dashboard.
3. Point at the **domain cards** (Learning, Reporting), the **Models** column, cost, success rate.
4. **Click a row** → the execution drill-down (per-call model, tokens, cost, parent/child tree).
**VO:**
> "Now every capability shows up in a live catalog — and a dashboard. Grouped by domain. Success rate, latency, tokens, and real dollar cost, attributed to the *capability*, not just a model call. Click any capability and you drill into every execution — the exact model used, tokens in and out, cost, and when one capability calls another, the parent-child execution tree. No workflow engine. No custom metrics code. You wrote one annotation."

### 1:55–2:30 — The killer feature: declarative capabilities
**On screen:** The `@CapabilityClient` interface — a method with **no body**.
```java
@CapabilityClient @ModelProfile("reasoning")
interface LessonCapability {
  @Capability(name="Generate Lesson", domain="Learning")
  @Prompt("Generate a Java lesson for {{topic}}")
  Lesson execute(String topic);   // Capstead writes the body
}
```
**VO:**
> "In 0.4, Capstead can even *write* the capability. Annotate an interface method with a prompt — no body. Capstead renders the prompt, routes to the model from a config-driven profile, calls Spring AI, and binds the result to your type. Ninety percent less infrastructure code — and it's governed automatically, right next to your hand-written capabilities."

### 2:30–2:50 — Enterprise reach
**On screen:** Quick montage — `@DailyBudget("$25")`, `capstead.jdbc.enabled: true`, `/actuator/capabilitymcp`.
**VO:**
> "Set a daily budget and Capstead enforces it. Turn on the JDBC recorder for durable, cross-instance history. Export every capability as an MCP tool for agents. Governance, cost control, and discovery — all Spring Boot native."

### 2:50–3:00 — Close / CTA
**On screen:** `io.capstead` on Maven Central · GitHub `satya-anguluri/capstead` · `capstead-starter`.
**VO:**
> "Capstead. Governance for your AI capabilities, in one annotation. It's open source on Maven Central — link below. Give your AI a control plane."

---

## Shot list (record these clips)
1. Code: a `@Service` with a normal `ChatClient` call (the "before").
2. `pom.xml` with the single `capstead-starter` dependency.
3. Code: `@Capability` on `generateLesson(...)`.
4. Terminal: `curl localhost:8080/actuator/capabilities` (pretty-printed).
5. Browser: `/capstead/` dashboard — domain cards, table, Models column.
6. Browser: click a capability row → drill-down executions.
7. Code: the `@CapabilityClient` declarative interface + `capstead.ai.profiles` YAML.
8. Code montage: `@DailyBudget`, `capstead.jdbc.enabled`, `/actuator/capabilitymcp`.
9. End card: Maven Central + GitHub URLs.

## Production tips
- Run the sample: `cd capstead/samples; mvn spring-boot:run` (or the jar). It seeds executions at startup, so the dashboard has data immediately — hit `GET /demo/quiz?topic=Records` on camera for a live declarative call.
- Zoom the editor font to ~18–20pt; hide the file tree during code shots.
- Keep cuts tight — one idea per shot. Add soft background music at ~15% volume.
- Captions/subtitles boost retention; the VO above doubles as the caption track.

## YouTube description (paste-ready)
> Capstead is an open-source Spring Boot library that adds a governance and observability control plane to your AI capabilities. Annotate any method with `@Capability` — or declare a bodyless `@CapabilityClient` interface — and get a live catalog, dashboard, per-capability cost, budgets, durable execution history, execution trees, and MCP export. Built on top of Spring AI.
>
> ⭐ GitHub: https://github.com/satya-anguluri/capstead
> 📦 Maven Central: io.capstead:capstead-starter
> 📖 Docs: declarative capabilities, dashboard, MCP export
>
> Chapters:
> 0:00 The problem
> 0:42 One annotation
> 1:05 The dashboard
> 1:55 Declarative capabilities
> 2:30 Budgets, JDBC, MCP
> 2:50 Get started
>
> #SpringBoot #Java #SpringAI #LLM #AIGovernance #Observability

**Tags:** spring boot, spring ai, java, llm, ai governance, observability, actuator, maven central, capstead, mcp
