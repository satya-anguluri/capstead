# Capstead — 60-Second YouTube Shorts (vertical, one-take)

**Format:** 9:16 vertical, 1080×1920. Screen capture (phone-framed or cropped editor) + fast cuts.
**Runtime:** ~60s. **Narration:** ~150 words. Punchy, high energy. Big on-screen captions.
**Demo app:** `capstead/samples` (no API keys). Keep every shot ≤ 8 seconds.

---

## Script (timed)

### 0:00–0:06 — Hook (caption: "Your AI has no control plane.")
**VO:** "You're shipping AI features in Spring Boot — but can you say what they cost, who owns them, or how reliable they are? Probably not."

### 0:06–0:18 — One annotation (show `@Capability` on a method)
**VO:** "Capstead fixes that with one annotation. Tag any method `@Capability` — name it, own it, version it. Your code still calls the model; Capstead governs everything around it."

### 0:18–0:36 — The dashboard (screen-record `/capstead/`, click a row)
**VO:** "Instantly you get a live catalog and dashboard — grouped by domain, with success rate, tokens, and real dollar cost per capability. Click in, and you drill into every execution: the model, the tokens, the cost, even the parent-child call tree."

### 0:36–0:50 — Declarative (show the bodyless `@CapabilityClient` interface)
**VO:** "Or go declarative — a prompt on an interface method, no body. Capstead writes the implementation, routes the model, binds the result. Ninety percent less code, governed automatically."

### 0:50–0:60 — CTA (end card: GitHub + Maven Central)
**VO:** "Capstead. A control plane for your AI, in one annotation. Open source, on Maven Central. Link in bio."

---

## Shot list (5 clips, that's it)
1. `@Capability` on `generateLesson(...)` — 8s.
2. `/capstead/` dashboard: domain cards + Models column + cost — 10s.
3. Click a row → drill-down executions — 8s.
4. `@CapabilityClient` interface + `@Prompt` line highlighted — 10s.
5. End card: `github.com/satya-anguluri/capstead` · `io.capstead:capstead-starter` — 4s.

## Shorts production tips
- **Vertical crop:** record the editor/dashboard normally, then crop to 9:16 zoomed on the relevant code/table — don't shrink a wide screen into a tall frame.
- **Captions are mandatory** — most Shorts play muted. Burn in the VO as large, high-contrast subtitles (one line at a time).
- **First 2 seconds decide everything** — open on the hook caption + a fast dashboard flash, not a slow intro.
- **Music:** upbeat, ~20% volume; cut on the beat.
- **Loop-friendly ending:** end card can hard-cut back to the hook so it loops cleanly (Shorts autoplay-loops).

## Caption / title
> Give your Spring Boot AI a control plane — in ONE annotation. #SpringBoot #Java #SpringAI #LLM #AIGovernance

**Pinned comment:** ⭐ github.com/satya-anguluri/capstead — Maven Central: io.capstead:capstead-starter
