# Capstead — Launch & Community Notification Checklist

A practical, sequenced playbook for announcing a Capstead release (e.g. `0.3.0`) to the Java/Spring
and AI-engineering communities. **Positioning first, then channels, then copy.**

---

## 0. Positioning (say this, not that)

Capstead is **not** "another AI framework." Lead with the gap it fills.

- ✅ **Say:** *"Governance & observability for your AI capabilities in Spring Boot — registry, versioning, per-capability cost/budgets, execution history, and MCP export, from one annotation."*
- ❌ **Don't say:** *"A new way to call LLMs"* / *"an alternative to Spring AI / LangChain4j."* Capstead sits **around** them and reuses them.

One-liner: **"Spring AI tells you about a model call. Capstead tells you about the business capability."**

The `0.3.0` hook: **"`@Capability` is no longer just static metadata — Capstead now records every execution over time: per-model invocations, parent-child execution trees, and durable, cross-instance history."**

**Proof (use it everywhere):** Capstead runs in production at **[engineerprep.io](https://engineerprep.io)**, governing ~a dozen AI capabilities across two domains and attributing cost across Anthropic Claude + Amazon Nova (Bedrock). "We run this in production" beats any feature list — screenshot the live `/capstead` dashboard + an execution tree.

---

## 1. Pre-launch gate (do NOT announce until all green)

- [ ] `0.3.0` (final, not `-rc`) published and **verified live** on Maven Central:
      `https://repo1.maven.org/maven2/io/capstead/capstead-starter/0.3.0/` returns the pom (200).
- [ ] README version references updated to `0.3.0` (install snippets, status).
- [ ] GitHub repo polish: description, topics (`spring-boot`, `spring-ai`, `observability`, `governance`, `mcp`, `llm`, `java`), `LICENSE`, and a pinned README that renders cleanly.
- [ ] GitHub **Release** created for `v0.3.0` with human-readable notes (the recorder story) + a link to Maven Central.
- [ ] A **60–90s demo** ready: dashboard at `/capstead` + `/actuator/capabilityexecutions` showing an execution tree with per-model invocations. GIF or short screen recording — this is the single highest-leverage asset.
- [ ] **Production proof captured**: a screenshot / short clip of the live [engineerprep.io](https://engineerprep.io) capabilities (dashboard, scorecard) — real usage is the most persuasive asset for HN/Reddit.
- [ ] *(Optional but high-value)* a **minimal sample repo** (`capstead-samples`): a fresh Spring Boot app with 2–3 `@Capability` methods + the Spring AI bridge, so newcomers can clone-and-run in 60 seconds. Link it from the README and every post.
- [ ] A canonical **write-up** exists to link to (a `dev.to`/`Foojay` article, see §3) so every post points to prose, not just a repo.
- [ ] Sanity check: a fresh Spring Boot app can add `capstead-starter:0.3.0` + annotate one method + see it on `/actuator/capabilities` (proves the "one annotation" claim).

---

## 2. Channels — where to notify (highest signal for Java/Spring first)

Post over **2–3 days**, not all at once, so you can respond to each thread.

### Tier 1 — Java/Spring ecosystem (best fit)
- [ ] **Foojay.io** (Friends of OpenJDK) — accepts library announcements/articles; excellent Java reach. Submit the write-up.
- [ ] **r/java** — a "show & tell" post; lead with the problem, not the pitch. Link the article.
- [ ] **r/SpringBoot** — emphasize auto-config + Actuator + Micrometer nativeness.
- [ ] **Baeldung "Java Weekly"** — submit the article link for inclusion (they curate ecosystem news).
- [ ] **InfoQ Java queue** — tip/news submission for a new observability/governance library.
- [ ] **Spring community Slack / spring.io** channels — share in the relevant discussion space.

### Tier 2 — AI-engineering / MCP
- [ ] **Model Context Protocol community** (Discord/GitHub discussions) — angle: "governed MCP tools" (versioned, owned, budget-enforced) via `capstead-mcp` / `capstead-mcp-server`.
- [ ] **LangChain4j** and **Spring AI** community channels — you *complement* them (attribution/governance), not compete.

### Tier 3 — Broad developer reach
- [ ] **Hacker News — "Show HN: Capstead — governance & observability for AI capabilities in Spring Boot."** Post early (US morning), then be present in comments for the first 2 hours.
- [ ] **LinkedIn** — founder post with the demo GIF; tag Spring / Java / AI-eng hashtags.
- [ ] **X/Twitter** — thread: problem → the annotation → the dashboard/execution-tree GIF → install line.
- [ ] **dev.to / Medium / Hashnode** — publish the canonical article (cross-post with canonical URL).

### Tier 4 — Durable discovery (do once, keeps paying off)
- [ ] Submit PRs to **awesome-lists**: `awesome-spring`, `awesome-java`, `awesome-mcp`, `awesome-llmops`.
- [ ] Ensure **libraries.io** / **mvnrepository.com** entries look right (description, tags).
- [ ] Add a short **docs site** or GitHub Pages (optional) so search traffic has a home.

---

## 3. The canonical article (write once, link everywhere)

Title idea: **"Governing AI capabilities in Spring Boot: from `@Capability` to durable execution history."**

Structure:
1. The problem — nobody can answer *what AI capabilities exist, who owns them, what version is live, what they cost.*
2. The one-annotation demo — `@Capability` + `/actuator/capabilities`.
3. Cost & budgets — attributing Spring AI's tokens to a business capability; `@DailyBudget`.
4. **New in 0.3.0** — the durable recorder: per-model invocations, execution trees, `/actuator/capabilityexecutions`, JDBC persistence, privacy controls.
5. MCP — export governed capabilities as MCP tools.
6. What Capstead is *not* (not an AI framework) + call to action (Maven coordinates, GitHub star).

---

## 4. Sample copy

**Show HN / Reddit title:**
> Show HN: Capstead — governance & observability for AI capabilities in Spring Boot

**Reddit r/java body (short):**
> Frameworks like Spring AI and LangChain4j nail *executing* an LLM call. But across many services nobody can answer: what AI capabilities exist, who owns them, which version is live, and what do they cost?
>
> Capstead is a small Spring Boot library that turns any annotated method into a **governed, versioned, observable business capability** — with a `/actuator/capabilities` catalog, per-capability cost/budgets, and (new in 0.3.0) a **durable execution recorder**: every run is recorded with its per-model invocations and parent-child execution tree, viewable at `/actuator/capabilityexecutions` and persistable to a database.
>
> It's **not** another AI framework — it sits *around* Spring AI and reuses it. One annotation, auto-configured. We run it in production at engineerprep.io across ~a dozen capabilities.
>
> Maven: `io.capstead:capstead-starter:0.3.0` · [GitHub] · [article]

**X/LinkedIn one-liner:**
> `@Capability` on a Spring method → a governed, versioned business capability with a live catalog, per-capability cost & budgets, and now a durable execution recorder (per-model invocations + execution trees). Not another AI framework — governance *around* your AI. `io.capstead:capstead-starter:0.3.0`

---

## 5. After you post

- [ ] Be present in every thread for the first few hours — answer "how is this different from X?" (Spring AI = per model-call; Capstead = per business capability + governance + versioning + durable history).
- [ ] Pin the best question/answer into the README FAQ.
- [ ] Watch GitHub stars/issues; label good-first-issues to invite contribution.
- [ ] Capture feedback into the roadmap; a fast follow-up release signals momentum.

> Tip: honesty wins these audiences. Call out limitations (e.g. same-bean self-invocation isn't
> proxied so nested trees need cross-bean calls; snapshots aren't on Maven Central). Skepticism
> converts to trust when you're upfront.
