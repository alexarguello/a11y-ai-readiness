# a11y-ai-readiness

The same Java web app, built twice: identical backend, identical CSS, only the markup differs.
The session list is proven pixel-identical between the two variants; the registration form, where
the actual testing happened, looks the same and works the same way, with nothing a user would
notice visually. That markup difference alone decides whether a screen reader or an AI agent can
use it.

## Why a developer should care

Your UI has two audiences you probably don't test: assistive technology, and software acting on a
user's behalf. Both consume the same thing, and it isn't your HTML — it's the **accessibility
tree** the browser builds from it: roles, names, states.

An agent driving a browser asks that tree "what is on this page, and what can I do to it?" A
screen reader asks the identical question. So a `<div onclick>` that looks like a button isn't a
minor style choice — it's a control that neither one can find. There is no separate "make it
AI-ready" project. It's the same work, and it's already in your backlog as an accessibility ticket.

## What it costs when you get it wrong

Two vendors' models, same prompt, same task, five runs per variant each — medians:

| Provider | Variant | Success | Tool calls | Tokens | Cost/run |
|---|---|---|---|---|---|
| Grok `grok-4.6` | `/accessible` | 100% | 11 | 36k | $0.071 |
| Grok `grok-4.6` | `/inaccessible` | **0%** | 33 | 127k | $0.247 |
| Claude `claude-sonnet-5` | `/accessible` | 100% | 11 | 47k | $0.104 |
| Claude `claude-sonnet-5` | `/inaccessible` | **0%** | 3 | 11k | $0.027 |

Both vendors complete `/accessible` 5/5 and fail `/inaccessible` 5/5. They were tested
separately, so this isn't one model's quirk.

Note the two shapes of failure. Grok kept hunting — 3× the calls, 3.5× the tokens, 3.5× the cost,
and still no confirmation code. Claude recognised there was no reachable control and gave up after
three calls. **Cheap is not good news here: the transaction is lost either way.**

Read across those rows and the business case writes itself:

- **The task failed.** Not "was slower" — neither agent ever obtained a confirmation code. If that
  flow were a checkout, a booking, or a claim submission, it's a lost transaction. As agent-driven
  traffic grows, an inaccessible UI is one a customer's agent cannot buy from.
- **You pay more for worse, or you pay less for nothing.** Where the model keeps trying, it burns
  tokens guessing at controls that expose no role or name, and you pay for that guessing on every
  run, forever. Where it quits early, the bill is small and the outcome is still a failure.
- **Your test suite pays the same tax.** Missing semantics are exactly what forces
  `div:nth-child(4) > span` selectors instead of `getByRole`. Those break on every refactor. If
  your Playwright locators are brittle, that's a symptom, not bad luck.
- **You already owed this work.** Every fix here — a real `<button>`, a `<label for>`, a
  `role="alert"` on the error — is a WCAG requirement. The agent benefit is free.

These come from `results/summary-*.json`. Per-run detail (tool calls, tokens, cost, the full
action log) is one file per run in `results/`, and `results/dashboard.html` renders all of it.
Runs that hit the tool-call cap are flagged `budgetExhausted` and excluded from the summaries as
spoiled data — five such runs are kept in `results/` rather than deleted, so the exclusion is
auditable.

## The screen reader says the same thing

The same task was run by hand with **NVDA** on Firefox, three scripts, recorded with the Speech
Viewer. Verbatim transcripts and a scorecard are in
[`docs/nvda-run-sheet.md`](docs/nvda-run-sheet.md); the counts in it are derived from those
transcripts by a script rather than remembered.

| | `/accessible` | `/inaccessible` (keyboard) | `/inaccessible` (mouse + NVDA) |
|---|---|---|---|
| Task completed | yes — confirmation code `JCON-C7ED97` | **no** | **no, even with a mouse** |
| Where it stopped | — | After the filter field — `Tab` moves focus off the page entirely | Silent resubmission loop on the meal-preference field |
| Validation error announced | yes — `role="alert"` fired immediately | never reached the form | **never announced** |

On `/inaccessible` the task could not even be *started* by keyboard: the filter box is the only
reachable control. That is the same defect the agents hit, reported by a different consumer.

`results/nvda-*.json` carries those findings in the same structured form as the agent and axe-core
results, so the dashboard's pass/fail matrix covers all three consumers.

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

`VisualParityTest` asserts the session list renders pixel-identically between the two variants, so
no difference in outcome there can be blamed on the visual design. The registration form shares
the same CSS and isn't separately screenshot-tested, but looks and behaves the same way; the
differences that matter live entirely in the markup, not anything visible. `AxeBaselineTest` runs
axe-core against the session list and the
registration form on both variants, writing WCAG violation counts to `results/axe-*.json` —
third-party confirmation that `/inaccessible` is really non-compliant, not just a comparison
rigged to fail. `DashboardGeneratorTest` reads every `results/*.json` — agent runs, axe-core, and
`results/nvda-*.json` (a structured transcription of the confirmed NVDA findings) — and renders
`results/dashboard.html`: a headline claim plus a unified pass/fail matrix across the screen
reader and both AI vendors, with cost and axe-core detail underneath as supporting evidence. No
server or browser needed to generate or view it. `/` links to both variants.

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
./mvnw spring-boot:run -Dspring-boot.run.arguments="--agent.run=true --variant=accessible --provider=xai"
```

`--variant` is `accessible` or `inaccessible`. `--provider` is `xai` (Grok) or `anthropic` (Claude
Sonnet 5) — omit it to use whatever `agent.provider` is set to in `secrets.properties` (`xai` by
default). Both providers' clients are always wired up; only the one you pick for a given run
actually gets called. Each run writes one `results/*.json` (tool calls, tokens, estimated cost,
the action log, the confirmation code if it got one) and exits.

### Multi-run pass

Add `--runs=N` to run both variants N times each instead of a single one-off attempt, in an
alternating A-B-B-A order so neither variant systematically benefits from running first or last:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--agent.run=true --runs=5 --provider=xai"
```

`--variant` is ignored in this mode (both run). This makes 2×N real API calls in one process. The
committed passes cost **$1.71 for Grok and $0.69 for Claude** at N=5 — read that as an order of
magnitude, not a quote, since the broken variant's cost is driven by how long a given model keeps
trying before it gives up. Watch the console as it runs. Writes one `results/*.json` per run plus one
`results/summary-<provider>-*.json` with medians and ranges per variant (runs that hit the
tool-call budget cap are excluded from the summary as spoiled data — the model was cut off, not
finished). Run each provider separately, same as single runs.

Needs the corresponding key — `xai.api.key` / `XAI_API_KEY` for Grok, `anthropic.api.key` /
`ANTHROPIC_API_KEY` for Claude — in `secrets.properties` or the environment. Keys are never
committed — `cp secrets.properties.example secrets.properties` and see that file's comments.

## Tech stack

| Piece | Choice |
|---|---|
| Language / JDK | Java 25 |
| Framework | Spring Boot 4.1.1, Spring MVC |
| Templates | Thymeleaf, server-rendered — no frontend build step |
| Validation | Jakarta Bean Validation |
| Dynamic bits | ~30 lines of vanilla JS: `async-form.js` (live-region error updates) and `fake-widgets.js` (the broken variant's mouse-only controls) |
| AI agent | Spring AI 2.0.1 — xAI/Grok via the OpenAI-compatible client, Anthropic via its own client; tools backed by Playwright |
| Build | Maven via the checked-in wrapper |
| Data | In-memory `SessionRepository` — no database |

## Project layout

```
src/main/java/com/a11y/a11y_ai_readiness/
├── web/           SessionController (both variants), HomeController
├── session/       WorkshopSession, SessionRepository (in-memory sample data)
├── registration/  RegistrationForm, MealPreference
└── agent/         AgentRunner, RegistrationAgentTools, ModelPricing,
                  AgentRunResult, SingleAgentRun, RunStats, MultiRunSummary

src/main/resources/
├── templates/accessible/     sessions.html, register.html, confirmation.html
├── templates/inaccessible/   sessions.html, register.html, confirmation.html
└── static/                   css/main.css (shared by both), js/, img/

src/test/java/com/a11y/a11y_ai_readiness/
├── parity/     VisualParityTest, ScreenshotDiff   → pixel parity of the session list
├── axe/        AxeBaselineTest                     → results/axe-*.json
├── snapshot/   AccessibilityTreeSnapshotTest       → results/accessibility-tree-*.json
└── dashboard/  DashboardGeneratorTest              → results/dashboard.html

docs/      nvda-run-sheet.md (verbatim NVDA transcripts + scorecard), screen recordings
results/   every measurement this README cites, plus the generated dashboard
```

`./mvnw test` runs the parity, axe-core, snapshot and dashboard tests. They drive a real browser,
which Playwright downloads on first run. No test calls a paid API.

## Troubleshooting: server won't start (Windows)

`java.io.IOException: Unable to establish loopback connection` isn't this app — it's the JVM's NIO
selector failing to open a local socket, usually blocked by a firewall, EDR agent, or VPN client.
Allow `java.exe` through Windows Defender Firewall for private networks, or disable the VPN, and
retry.

## License

[MIT](LICENSE) — use it, fork it, lift the fixes into your own app. That last one is the point.
