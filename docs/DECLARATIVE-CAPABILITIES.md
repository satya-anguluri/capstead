# Declarative Capabilities (Capstead 0.5.0)

> Provider-neutral since 0.5.0 — works **with or without Spring AI**.

## 1. What this feature is

Capstead governs *hand-written* `@Capability` methods. Declarative capabilities flip that: you
annotate a **bodyless interface method** with a prompt, and Capstead **synthesizes the
implementation** (renders the prompt, routes to a model via your invoker, binds the response) — while
governing it through the *existing* machinery (catalog, scorecard, cost, budgets, dashboard, MCP).

### Before
```java
ChatClient chatClient = ...;                 // or your own LLM client
Lesson lesson = chatClient.prompt().user(prompt).call().entity(Lesson.class);
```

### After
```java
@CapabilityClient
@ModelProfile("reasoning")
public interface LessonCapability {

    @Capability(name = "Generate Lesson", domain = "Learning")
    @Prompt("Generate a Java lesson for {{topic}}")
    Lesson execute(String topic);   // no body — Capstead writes it
}
```

## 2. The design: a provider-neutral SPI

The proxy does **not** talk to a model directly. It calls a single application-provided bean:

```java
// io.capstead.runtime
@FunctionalInterface
public interface CapabilityModelInvoker {
    String invoke(CapabilityModelRequest request);   // return the model's raw text
}
```

Capstead owns everything provider-agnostic — prompt rendering, profile resolution, output binding, and
governance — and delegates only the actual model call:

```
caller
  └─ Spring AOP proxy (capability advisor)   ← opens execution, times, prices, publishes
       └─ JDK dynamic proxy (this feature)    ← render prompt, resolve profile, bind output
            └─ CapabilityModelInvoker (yours) ← the only model-specific code
```

This means declarative capabilities work with **any** backend, and Capstead never hard-depends on a
model SDK.

## 3. How each project wires the model

**Not using Spring AI** — implement one method:
```java
@Bean
CapabilityModelInvoker modelInvoker(MyLlmClient llm) {           // LangChain4j, an SDK, raw HTTP…
    return req -> llm.complete(req.model(), req.systemPrompt(), req.userPrompt());
}
```

**Using Spring AI** — add `capstead-spring-ai`; a default `SpringAiCapabilityModelInvoker`
(`ChatClient`-backed) is auto-registered when a `ChatClient.Builder` bean exists. No bean needed.
Provide your own `CapabilityModelInvoker` to override it (`@ConditionalOnMissingBean`).

## 4. What was built

### Annotations (`capstead-annotations`, dependency-free)
`@CapabilityClient` (interface), `@Prompt`, `@SystemPrompt`, `@ModelProfile` (method/interface), `@P`.

### SPI (`capstead-runtime`, no Spring AI)
- `CapabilityModelInvoker` — the one-method model seam.
- `CapabilityModelRequest` — rendered prompts + resolved model/profile + return type.

### Declarative engine (`capstead-starter`, **no Spring AI**)
| Class | Responsibility |
|---|---|
| `PromptTemplateRenderer` | `{{name}}` renderer (tolerates whitespace; leaves unknown placeholders). |
| `ModelProfileProperties` | `@ConfigurationProperties("capstead.ai")` → `Map<String, Profile>` (model, temperature, maxTokens, topP). |
| `StructuredOutputBinder` | Appends a JSON format instruction for non-`String` returns; strips fences and binds via Jackson. |
| `CapabilityClientInvocationHandler` | Renders prompts, resolves profile, calls the `CapabilityModelInvoker`, binds the return type. |
| `CapabilityClientFactoryBean<T>` | Creates the JDK proxy per interface; resolves invoker/profiles/binder lazily. |
| `CapabilityClientRegistrar` | Scans auto-config base packages for `@CapabilityClient` interfaces (no `@Enable`). |
| `DeclarativeCapabilityCatalogRegistrar` | Registers declarative caps into the catalog / domain grouping. |
| `CapsteadDeclarativeAutoConfiguration` | Wires the above; enables `ModelProfileProperties`. Always on (no Spring AI condition). |

### Spring AI default (`capstead-spring-ai`, optional)
- `SpringAiCapabilityModelInvoker implements CapabilityModelInvoker` — `ChatClient`-backed, returns raw text.
- `CapsteadSpringAiInvokerAutoConfiguration` — registers it `@ConditionalOnClass(ChatClient)` +
  `@ConditionalOnBean(ChatClient.Builder)` + `@ConditionalOnMissingBean(CapabilityModelInvoker)`.

## 5. Configuration — model profiles
```yaml
capstead:
  ai:
    profiles:
      reasoning: { model: us.anthropic.claude-sonnet-4-6, temperature: 0.2 }
      fast:      { model: amazon.nova-pro, max-tokens: 512 }
```
`@ModelProfile("reasoning")` sets `request.model()`/`temperature()`/… for that call. Absent/unconfigured
→ the invoker's own default.

## 6. Output binding
- `String` / `void` return → the model text verbatim (or ignored).
- Any other type → Capstead appends a JSON format instruction, then `StructuredOutputBinder` strips
  markdown fences, extracts the JSON body, and maps it to the return type with Jackson. No Spring AI
  converter involved, so it works for every backend.

## 7. The sample (clone-and-run, **no Spring AI, no API keys**)
`capstead/samples` depends on **only** `capstead-starter`. It declares:
- `QuizCapability` — `@CapabilityClient @ModelProfile("reasoning") @Prompt(...) Quiz execute(String topic)`.
- `StubModelInvoker implements CapabilityModelInvoker` — returns canned JSON and records model/token
  usage. This is exactly the one-method bean a real project writes.

Verified live: `Generate Quiz` appears in `/actuator/capabilities` (domain `Learning`), the scorecard
prices it (`claude-sonnet`), and `GET /demo/quiz?topic=…` binds the `Quiz` record — all with zero
Spring AI on the classpath.

## 8. Tests
- `capstead-starter`: `PromptTemplateRendererTest`, `StructuredOutputBinderTest`,
  `CapabilityClientInvocationHandlerTest` (fake `CapabilityModelInvoker`).
- `capstead-spring-ai`: `SpringAiCapabilityModelInvokerTest` (Mockito `ChatModel`).
- Full reactor `mvn clean install` at 0.5.0 → BUILD SUCCESS.

## 9. Positioning
Declarative capabilities are a **convenience** on top of Capstead's real value — governance. The SPI
keeps Capstead decoupled from any model framework: it *governs* whatever produces the text, rather than
competing with Spring AI's or LangChain4j's prompt/binding features.
