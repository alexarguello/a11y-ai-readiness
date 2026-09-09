# a11y-ai-readiness

The same Java web app, built twice. Identical pixels, identical backend, identical CSS — only the
markup semantics differ. That alone decides whether a screen reader *or* an AI agent can use it.

## Why a developer should care

Your UI has two audiences you probably don't test: assistive technology, and software acting on a
user's behalf. Both consume the same thing, and it isn't your HTML — it's the **accessibility
tree** the browser builds from it: roles, names, states.

An agent driving a browser asks that tree "what is on this page, and what can I do to it?" A
screen reader asks the identical question. So a `<div onclick>` that looks like a button isn't a
minor style choice — it's a control that neither one can find. There is no separate "make it
AI-ready" project. It's the same work, and it's already in your backlog as an accessibility ticket.

## What it costs when you get it wrong

One agent run per variant, same model, same prompt, same task:

| | `/accessible` | `/inaccessible` |
|---|---|---|
| Completed the task | **yes** | **no** |
| Tool calls | 11 | 28 |
| Failed tool calls | 0 | 20 |
| Tokens | 32.8k | 71.1k |
| Cost | $0.065 | $0.130 |

Read across that row and the business case writes itself:

- **The task failed.** Not "was slower" — the agent never obtained a confirmation code. If that
  flow were a checkout, a booking, or a claim submission, it's a lost transaction. As agent-driven
  traffic grows, an inaccessible UI is one a customer's agent cannot buy from.
- **2× the cost for worse results.** The agent burns tokens guessing: 20 of its 28 actions failed
  because it was hunting for controls that expose no role or name. You pay per token for that
  guessing, on every run, forever.
- **Your test suite pays the same tax.** Missing semantics are exactly what forces
  `div:nth-child(4) > span` selectors instead of `getByRole`. Those break on every refactor. If
  your Playwright locators are brittle, that's a symptom, not bad luck.
- **You already owed this work.** Every fix here — a real `<button>`, a `<label for>`, a
  `role="alert"` on the error — is a WCAG requirement. The agent benefit is free.

**Read these numbers as directional, not settled.** One run per variant is an anecdote: LLM runs
vary, and a multi-run pass reporting medians isn't done yet. The committed results in `results/`
also predate two harness fixes (a submittable form on the broken variant, and role-based typing),
so they will move. Treat the mechanism as the finding; treat the digits as provisional.

## The two variants

One flow — register for a workshop — served twice by the *same* controller, validation and
stylesheet. Only the templates differ.

1. Filter the session list to find a workshop
2. Open its registration form
3. Submit with a malformed email, see the error, correct it
4. Submit again and get a confirmation code (`JCON-XXXXXX`)

| | `/accessible` | `/inaccessible` |
|---|---|---|
| Session list | `<table>` + `<th scope>` | CSS-grid `<div>` soup |
| Fields | `<label for>` | `placeholder` only |
| Dropdown | native `<select>` | `<div>` widget, no `role`/`aria-expanded` |
| Buttons & links | real `<button>` / `<a>` | `<div onclick>`, no `tabindex` — mouse only |
| Validation error | `role="alert"` + text + `aria-describedby` | red border, no text |
| Focus | visible ring everywhere | `outline: none` |

`VisualParityTest` asserts the two render pixel-identically, so no difference in outcome can be
blamed on the visual design. `/` links to both.

**One deliberate exception:** the broken variant's *submit* button is real. As a role-less `<div>`
it was unreachable by keyboard and by role, so the form couldn't be submitted at all — which put
the colour-only validation error, the most interesting defect on the page, beyond reach of
everything. Making that one control real isolates the defect worth showing.

## Run it

Needs **JDK 25** (a JDK, not a JRE). Nothing else — the Maven wrapper fetches its own Maven, and
there's no Node/npm step.

```bash
./mvnw spring-boot:run
```

Then open http://localhost:8080/.

Any IDE with Maven support opens this folder directly; `pom.xml` is the only source of truth, and
IDE metadata is intentionally not committed. If you hit "invalid source release", point the
project SDK at JDK 25 and re-import.

## Run the agent

The agent perceives the page *only* through accessibility-tree primitives — `ariaSnapshot()` to
look, role/name lookups to act. Nothing in its toolset reads raw HTML or CSS, which is what makes
the comparison meaningful. It never runs during `spring-boot:run` or `mvn test`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--agent.run=true --variant=accessible"
```

`--variant` is `accessible` or `inaccessible`. Each run writes one `results/*.json` (tool calls,
tokens, estimated cost, the action log, the confirmation code if it got one) and exits.

Needs `xai.api.key` in `secrets.properties` or `XAI_API_KEY` in the environment. Keys are never
committed — `cp secrets.properties.example secrets.properties` and see that file's comments.

## Tech stack

| Piece | Choice |
|---|---|
| Language / JDK | Java 25 |
| Framework | Spring Boot 4.1.1, Spring MVC |
| Templates | Thymeleaf, server-rendered — no frontend build step |
| Validation | Jakarta Bean Validation |
| Dynamic bits | ~30 lines of vanilla JS: `async-form.js` (live-region error updates) and `fake-widgets.js` (the broken variant's mouse-only controls) |
| AI agent | Spring AI 2.0.1, xAI/Grok via the OpenAI-compatible client; tools backed by Playwright |
| Build | Maven via the checked-in wrapper |
| Data | In-memory `SessionRepository` — no database |

## Project layout

```
src/main/java/com/a11y/a11y_ai_readiness/
├── web/           SessionController (both variants), HomeController
├── session/       WorkshopSession, SessionRepository (in-memory sample data)
├── registration/  RegistrationForm, MealPreference
└── agent/         AgentRunner, RegistrationAgentTools, ModelPricing

src/main/resources/
├── templates/accessible/     sessions.html, register.html, confirmation.html
├── templates/inaccessible/   sessions.html, register.html, confirmation.html
└── static/                   css/main.css (shared by both), js/, img/
```

## Troubleshooting: server won't start (Windows)

`java.io.IOException: Unable to establish loopback connection` isn't this app — it's the JVM's NIO
selector failing to open a local socket, usually blocked by a firewall, EDR agent, or VPN client.
Allow `java.exe` through Windows Defender Firewall for private networks, or disable the VPN, and
retry.
