# Changelog

Notable changes to Capstead. Versions follow `0.MINOR.PATCH` while the library is pre-1.0: a **minor** bump
may change behaviour or a public shape, a **patch** never does.

> **This file is the source for GitHub release notes.** `scripts/changelog-section.sh <version>` extracts a
> section verbatim and `gh release create --notes-file` publishes it, so an entry has to read as notes: open
> with one line saying what the release gives you, then the categorised detail. Anything written only on the
> release page will be lost the next time the notes are regenerated.

> **Entries before 0.8.0 came the other way.** This file did not exist until 0.8.0, so 0.3.1 through
> 0.7.0 were written on the GitHub release pages first and are transcribed here — condensed to the
> categories used above, with the code samples left on the releases. Where the two differ, the release
> page is the original record.

## 0.8.0

Walk a whole capability execution tree in one call, and get the actual tree from the actuator instead of one
level of it.

### Breaking

- **`GET /actuator/capabilityexecutions/{id}` nests to full depth, and its `children` array changed shape.**

  ```diff
  -{ "execution": { … }, "children": [ { … }, { … } ] }
  +{ "execution": { … }, "children": [ { "execution": { … }, "children": [ … ] } ] }
  ```

  `children` now holds `ExecutionTree` objects rather than `CapabilityExecutionView` objects, so a consumer
  reading `children[].executionId` must read `children[].execution.executionId` instead.

  The response type has always been called `ExecutionTree` and previously returned one execution plus its
  **direct** children. A capability that called a capability that called another reported the middle layer and
  stopped, so a caller who did not know to walk it themselves received a complete-looking answer that was
  missing everything below depth one. The type now matches its name.

  An unknown or aged-out id still returns `null`, so callers already handling that case are unaffected.

- **`CapabilityExecutionQuery` gained `subtree(String executionId)`.** Any third-party implementation of that
  interface will not compile until it implements the new method. Both bundled implementations
  (`InMemoryCapabilityExecutionStore`, `JdbcCapabilityExecutionReader`) do.

### Added

- **`subtree(executionId)` — a whole execution tree in one call.** `childrenOf` answers a single level;
  reassembling a tree from it cost a round trip per level and left every caller writing the same traversal.
  In-memory this is one pass to index by parent then a breadth-first walk; over JDBC it is a recursive CTE,
  exercised against both H2 and MySQL 8.

  Ordering is part of the contract: the root comes first and every node appears after its own parent, so a
  consumer can build the nested shape in a single pass with no sorting and no second index. Siblings are
  most-recent-first, tie-broken on execution id so that executions recorded in the same millisecond — a
  capability fanning out to several tools does exactly that — still come back in a fixed order. Both
  implementations produce the same order, and a test asserts the property rather than a fixed list.

  Malformed graphs are bounded rather than fatal. Nothing validates that parent links form a tree, so a
  record whose ancestor claims it as a parent would otherwise loop forever on a read path serving an actuator
  endpoint: the in-memory store tracks visited ids, the CTE bounds depth at 50, and each returns a truncated
  tree instead of hanging. A duplicated id cannot detach already-attached children from the nested response.

- **A build.** `mvn -B -ntp verify` now runs on every pull request and push to `main`, on a runner with
  Docker, so the MySQL Testcontainers round-trips actually execute. They are
  `@Testcontainers(disabledWithoutDocker = true)` and therefore *skip* on a machine without a daemon, which
  meant a green local run could be several tests that never ran. The build reports totals and names any suite
  that skipped.

### Fixed

- **Test isolation in `JdbcCapabilityExecutionRoundTripTest`.** Every test opened the same H2 in-memory
  database under its default name and none closed it, so rows leaked between tests. Harmless while each test
  used unique ids, and the sort of failure that passes alone and fails together.

### Not included

`subtree` returns the same `CapabilityExecution` records the rest of `CapabilityExecutionQuery` returns
rather than a new export type. An export shape that consumers persist and diff is a contract worth settling
once, alongside the ordered execution events it will need to carry, so `findByAttribute` and a versioned
export shape remain open.

### Example

```java
// Every descendant, ordered root-first, parents before children.
List<CapabilityExecution> tree = query.subtree(rootExecutionId);
```

```
GET /actuator/capabilityexecutions/{id}
{
  "execution": { "capabilityName": "Generate Course", "executionId": "exec-1", … },
  "children": [
    { "execution": { "capabilityName": "Generate Lesson", … },
      "children": [ { "execution": { "capabilityName": "Score Lesson", … }, "children": [] } ] }
  ]
}
```

---

`io.capstead:capstead-starter:0.8.0` · [Maven Central](https://repo1.maven.org/maven2/io/capstead/capstead-starter/0.8.0/)
## 0.7.0

Meter and price model calls that are not billed by token — text-to-speech characters, transcription
seconds, per-request APIs — with no change to client code.

### Added

- `capstead.capabilities[].usage`: declare `model`, `unit` (`tokens` | `characters` | `seconds` |
  `requests`) and `input-from-arg`, and the interceptor synthesises one `ModelInvocation` from the declared
  argument when the call records nothing itself. Real enrichment always wins over the synthesised value.
- `CapabilityUsageRule` in the runtime, priced through the existing `capstead.cost` estimator like any
  token-billed model.

### Fixed

- `capstead.cost.models` accepts input-only or output-only rates. Usage-metered models have exactly one
  side, so requiring both made them impossible to price.

## 0.6.0

Describe a multi-step workflow in configuration and read its runs back, without client code or schema
changes.

### Added

- `capstead.pipelines`: named steps with a bounded `max-gap`. Runs are assembled at read time from
  already-recorded root executions by ordered step matching, so nothing new is written to record a
  pipeline.
- `/actuator/capabilitypipelines`: pipeline scorecards and per-run drill-down — wall time, tokens, cost
  and a per-step breakdown.
- A Pipelines section in the dashboard, with the run detail view.
- `PipelineDefinition`, `PipelineRun`, `PipelineScorecard` and `PipelineAssembler` in the core module.

### Fixed

- The registry and metadata-resolver beans are static `ROLE_INFRASTRUCTURE` beans, which silences the
  "not eligible for getting processed by all BeanPostProcessors" warning on startup in client
  applications.

## 0.5.1

Declarative capabilities stop requiring Spring AI, and the dashboard is worth opening before anything has
run.

### Added

- `CapabilityModelInvoker` and `CapabilityModelRequest`: a one-method SPI in the runtime. Capstead renders
  the prompt, resolves the model profile, binds structured output and governs the call; you supply only the
  model call itself — LangChain4j, an SDK, raw HTTP.
- `capstead-spring-ai` provides the default `ChatClient`-backed invoker, so Spring AI users need no bean.

### Changed

- The declarative engine moved into `capstead-starter` and no longer depends on Spring AI.
- Structured output is bound provider-neutrally, JSON to the declared return type.
- The dashboard merges the catalog with the scorecards, so every registered capability is listed before it
  has ever run, with a "No runs" badge and its metadata still reachable.

### Fixed

- Clicking a domain with no recorded executions showed a blank page.

## 0.4.0

Capstead can write and govern a capability, not only observe a hand-written one.

### Added

- `@CapabilityClient` on a bodyless interface method: Capstead renders the prompt, routes the model, calls
  Spring AI's `ChatClient` and binds the response to the return type, while `@Capability` governs it
  exactly as it governs a hand-written method — recording, cost, budgets, execution tree, dashboard,
  catalog.
- `@Prompt`, `@SystemPrompt`, `@ModelProfile` and `@P`.
- `capstead.ai.profiles`: model routing lives in configuration, so capability code never names a model.
- Domain cards in the dashboard, grouped from the catalog, with click-to-filter.

### Notes

- Declarative capabilities appear in `/actuator/capabilities`, in the dashboard and in domain grouping
  identically to annotation- and config-declared ones.
- Requires `capstead-spring-ai` alongside the starter. No breaking changes to existing APIs.

## 0.3.2

Completes the 0.3.x line: the dashboard and the scorecards read the durable store, so what they report
survives a restart and covers every instance rather than the one that answered.

### Fixed

- `/actuator/capabilityscorecard`, `/actuator/capabilityexecutions` and the `/capstead` dashboard read the
  durable store when `capstead-jdbc` is enabled. In 0.3.1 the dashboard read only the in-memory store.

### Notes

- **Use 0.3.2 rather than either earlier 0.3.x.** It supersedes 0.3.0, which had an actuator serialisation
  bug, and 0.3.1, whose dashboard read the wrong store.

## 0.3.1

Turns `@Capability` from static metadata into a durable execution recorder.

### Added

- Per-model invocations: a capability that calls the model several times — retries, multi-step, fan-out —
  captures each call, so tokens and cost are attributed per model rather than only per capability.
- Parent-child execution trees: nested `@Capability` calls are linked automatically, and
  `GET /actuator/capabilityexecutions/{id}` returns the tree.
- Durable cross-instance persistence through `capstead-jdbc`, with a vendor-appropriate schema for
  PostgreSQL, MySQL and H2. Capstead creates and owns its tables.
- `capstead.executions.recording-mode`: `best-effort`, `sync` or `async`. Recording never fails the
  business call.
- `GET /actuator/capabilityexecutions`, and `/{id}` for the tree.
- Privacy by default: inputs and outputs are not stored unless opted in, with a pluggable
  `CapabilityDataRedactor` and `CapabilityPrincipalProvider`.
- MySQL support in `capstead-jdbc`.

### Notes

- Capabilities can be declared by annotation or by `capstead.capabilities` in YAML, and the two coexist.
- 0.3.0 and 0.5.0 were released and then superseded within the same line; they have no entry here because
  neither should be used. See the 0.3.2 note above.
