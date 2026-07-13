# Declarative Capabilities (Capstead 0.4.0)

> Status: **built and verified end-to-end** on 2026-07-12. Not yet committed or released.

## 1. What this feature is

Capstead started as a **governance/observability layer** that wraps *hand-written* `@Capability`
methods. Declarative capabilities flip that: you annotate an **interface method with no body**, and
Capstead **synthesizes the implementation** (renders the prompt, routes to a model, calls Spring AI,
binds the response) — while still governing it through the *existing* machinery.

The tagline: **~90% less infrastructure code.**

### Before Capstead
```java
ChatClient chatClient = ...;
String prompt = "...";

Lesson lesson = chatClient.prompt()
    .user(prompt)
    .call()
    .entity(Lesson.class);
```

### After Capstead
```java
@CapabilityClient
@ModelProfile("reasoning")
public interface LessonCapability {

    @Capability(name = "Generate Lesson", domain = "Learning")
    @Prompt("Generate a Java lesson for {{topic}}")
    Lesson execute(String topic);   // no body — Capstead writes it
}
```

Because the generated method is still a `@Capability`, it appears on the **dashboard, scorecard,
execution drill-down, catalog, and domain grouping** with **model / tokens / cost** — identical to a
hand-written capability. Nothing extra to wire.

## 2. The key design decision

The declarative proxy **only supplies the model-calling body**. Governance (execution recording,
budget enforcement, cost pricing, the parent/child execution tree) is applied *around* it by the
**standard `@Capability` AOP advisor** — the same advisor that wraps hand-written capabilities.

```
caller
  └─ Spring AOP proxy (capability advisor)      ← opens execution, times, prices, publishes
       └─ JDK dynamic proxy (this feature)       ← renders prompt, routes model, calls ChatClient
            └─ Spring AI ChatClient → ChatModel  ← observation bridge enriches tokens/model
```

This means we did **not** reimplement recording — declarative capabilities are governed for free.

## 3. What was built

### New annotations (`capstead-annotations`)
| Annotation | Target | Purpose |
|---|---|---|
| `@CapabilityClient` | interface (TYPE) | Marks an interface as a declarative capability client. Optional `profile()` sets a default model profile for all methods. |
| `@Prompt` | method | The user-message template, with `{{name}}` placeholders. |
| `@SystemPrompt` | method | Optional system-message template (same placeholders). |
| `@ModelProfile` | method or interface | Selects a model profile (`capstead.ai.profiles.<name>`). Method beats interface. |
| `@P` | parameter | Binds a parameter to a specific `{{name}}` placeholder (default is the parameter name). |

### New runtime (`capstead-spring-ai`)
| Class | Responsibility |
|---|---|
| `PromptTemplateRenderer` | Tiny dependency-free `{{name}}` renderer (tolerates `{{ spaces }}`; leaves unknown placeholders untouched so partial binding fails visibly). |
| `ModelProfileProperties` | `@ConfigurationProperties("capstead.ai")` → `Map<String, Profile>`; each `Profile` has `model`, `temperature`, `maxTokens`, `topP`. |
| `CapabilityClientInvocationHandler` | The core. Binds args → variables, renders `@Prompt`/`@SystemPrompt`, resolves `@ModelProfile` → `ChatOptions`, calls `ChatClient`, binds the return type (`String`/`void` verbatim, else `.entity(type)`). Handles `Object` and `default` methods. |
| `CapabilityClientFactoryBean<T>` | Creates the JDK dynamic proxy for one client interface; looks up `ChatClient.Builder` + `ModelProfileProperties` lazily. |
| `CapabilityClientRegistrar` | `BeanDefinitionRegistryPostProcessor` that scans the app's auto-configuration base packages for `@CapabilityClient` interfaces and registers one factory bean per interface — no `@Enable` needed (Spring Data-style). |
| `DeclarativeCapabilityCatalogRegistrar` | `SmartInitializingSingleton` that registers each declarative `@Capability` into the `CapabilityRegistry` so it shows in `/actuator/capabilities` + domain cards (regular discovery can't see proxy methods). |
| `CapsteadDeclarativeAutoConfiguration` | `@AutoConfiguration @ConditionalOnClass(ChatClient.class)`; wires the scanner + catalog registrar and enables `ModelProfileProperties`. Added to `AutoConfiguration.imports`. |

### Dependency change
`capstead-spring-ai/pom.xml` adds **`spring-ai-client-chat`** (optional — provides `ChatClient`) and
`spring-boot-configuration-processor` (optional). The declarative auto-config is gated on
`ChatClient` being present, so the observation bridge alone still works without it.

## 4. Configuration — model profiles

Profiles decouple capability code from concrete model names, so routing changes via config:

```yaml
capstead:
  ai:
    profiles:
      reasoning: { model: us.anthropic.claude-sonnet-4-6, temperature: 0.2 }
      fast:      { model: amazon.nova-pro, max-tokens: 512 }
```

`@ModelProfile("reasoning")` → applies `model` + `temperature` as Spring AI `ChatOptions` for that
call. If the profile is absent or unconfigured, the application's default Spring AI model is used.

## 5. Parameter binding & templates

- Parameters bind to `{{name}}` **by compiled parameter name** (Spring Boot enables `-parameters`
  by default) or explicitly via `@P("name")`.
- Templates use `{{topic}}` (Mustache-style). Multi-line prompts use Java text blocks.
- Return type drives output binding: `String`/`void` return the raw content; any other type is bound
  via Spring AI's structured-output converter (`.entity(type)`).

## 6. The sample (clone-and-run, no API keys)

`capstead/samples` gained a real declarative capability:

- `QuizCapability` — `@CapabilityClient @ModelProfile("reasoning") @Prompt(...) Quiz execute(String topic)`.
- `Quiz` — the structured return record.
- `StubChatModel` — a canned Spring AI `ChatModel` that returns fixed JSON and records the model +
  token usage, so the demo runs **without any API keys**.
- `SampleApplication` declares `@Bean ChatClient.Builder chatClientBuilder(ChatModel)` (avoids needing
  a Spring AI auto-configure starter).
- `application.yml` adds `capstead.ai.profiles`.
- `DemoRunner` exercises it at startup; `GET /demo/quiz?topic=...` triggers it on demand.

### Verified live
- **Catalog** (`/actuator/capabilities`) lists `Generate Quiz` — domain `Learning`, owner `Content Team`.
- **Scorecard** shows it with `claude-sonnet`, real token counts, and a priced cost.
- **Drill-down** (`/actuator/capabilityscorecard/Generate%20Quiz`) returns per-execution model/tokens/cost.

## 7. Tests

- `PromptTemplateRendererTest` (5) — binding, whitespace, unknown placeholders, null handling.
- `CapabilityClientInvocationHandlerTest` (3) — Mockito `ChatModel` + `ArgumentCaptor<Prompt>`:
  prompt rendering, entity binding, profile→model routing, raw `String` return, `@P`, `@SystemPrompt`,
  and no-options-when-unconfigured.
- Full reactor `mvn clean install` at **0.4.0** → BUILD SUCCESS (MySQL testcontainers still skipped
  without Docker).

## 8. Bugs found during build (and fixed)

1. **`ClassUtils.getAllInterfacesForClass(iface)` returns *super*-interfaces, not the interface
   itself.** The catalog registrar missed declarative capabilities because a proxy bean's type *is*
   the client interface. Fix: also check `beanType` itself when it is a `@CapabilityClient` interface.

2. **`@ConditionalOnBean(CapabilityRegistry)` is auto-configuration-order-sensitive.** The declarative
   auto-config was evaluated before the starter registered `CapabilityRegistry`, so the catalog
   registrar bean was never created (recording still worked via the scanner, so the bug was silent —
   scorecards populated but the catalog entry was missing). Fix: drop `@ConditionalOnBean` and resolve
   `CapabilityRegistry` **lazily** via `getBeanProvider(...).getIfAvailable()` at
   `afterSingletonsInstantiated` time.

## 9. Versioning & next steps

This is **0.4.0** (a feature — correct semver). 0.4.0 also carries the **domain-grouping dashboard
cards** built earlier (never cut as 0.3.4).

Remaining to ship:
1. Commit + release-deploy 0.4.0 → **Publish** in the Central Portal.
2. README / docs update for declarative capabilities.
3. `v0.4.0` GitHub release.
4. Bump `order-service` `0.3.2 → 0.4.0` and redeploy prod.
