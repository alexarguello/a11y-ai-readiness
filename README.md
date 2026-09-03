# a11y-ai-readiness

JCON talk demo: same Java web app, same pixels, only the markup semantics differ — and that's
enough to make or break a screen reader and an AI agent. Full plan: [docs/PLAN.md](docs/PLAN.md).

## Functionality

One realistic flow — register for a JCON workshop — built twice at two URLs, sharing the same
Spring MVC controller, the same backend validation, and the same CSS file. **Only the authored
markup differs.**

- **`/accessible`** — real semantics throughout: `<table>` with `<th scope>`, `<label for>` on
  every field, a native `<select>`, real `<button>`/`<a>` elements, a `role="alert"` banner plus
  `aria-describedby` text on validation errors, and a visible focus ring on every interactive
  element.
- **`/inaccessible`** — the same task with the semantics stripped out: a CSS-grid `<div>` soup
  instead of a table, placeholder text instead of labels, a custom `<div>`-based dropdown with no
  `role`/`aria-expanded`, `<div>`/`<span>` elements with `onclick` instead of real buttons/links
  (mouse-operable, keyboard-unreachable — no `tabindex`), validation errors shown only as a red
  border with no text, and no visible focus indicator at all.

Both variants render pixel-identical — same table proportions, same button and dropdown styling —
because they share `static/css/main.css`; only the markup backing each page changes. The flow
itself is the same on both:

1. Filter the session list to find a workshop
2. Open its registration form
3. Submit with a deliberate validation error (e.g. malformed email), see it flagged, correct it
4. Submit again and get back a confirmation code (`JCON-XXXXXX`)

`/` is a landing page linking to both variants. See [docs/PLAN.md §3–§4](docs/PLAN.md) for the
full rationale and the complete accessible-vs-broken comparison table.

## Requirements

- **JDK 25** (a JDK, not just a JRE). Nothing else — the Maven wrapper below downloads its own
  Maven, and there's no Node/npm step.

## Run it

```
./mvnw spring-boot:run
```

Then open http://localhost:8080/. No global Maven install needed — the wrapper downloads its own.

## Opening in an IDE

This is a plain Maven project (`pom.xml` is the single source of truth), so any IDE with Maven
support — IntelliJ, Eclipse, VS Code + Java extensions, etc. — can open this folder directly with
no project-specific setup. IDE metadata (`.idea/`, `.vscode/`, `.classpath`, etc.) is intentionally
**not** committed, per standard open-source practice: it's local, per-contributor state, and
committing one person's IDE config into the repo tends to fight everyone else's setup.

The one thing to check by hand: this project targets **JDK 25**. Most current IDEs auto-detect an
installed JDK 25 and select it as the project SDK on import; if yours doesn't, or you get an
"invalid source release" / SDK-not-found error, set the project's JDK to 25 manually (in IntelliJ:
File → Project Structure → SDKs) and re-import the Maven project.

## Secrets

API keys are never committed. Copy the example file and fill in your own key, or export the
environment variable instead (the loader checks the environment first):

```
cp secrets.properties.example secrets.properties
```

See the comments in `secrets.properties.example` for which keys are needed and where to get them.

## Tech stack

| Piece | Choice |
|---|---|
| Language / JDK | Java 25 (latest LTS) |
| Framework | Spring Boot 4.1.1 |
| Web | Spring MVC (`spring-boot-starter-webmvc`) |
| Templates | Thymeleaf, server-rendered — no Node, no npm, no frontend build step |
| Validation | Jakarta Bean Validation (`spring-boot-starter-validation`) |
| Dynamic bits | ~30 lines of vanilla JS (`static/js/fake-widgets.js`), used only on `/inaccessible` to make its fake dropdown/buttons mouse-clickable |
| Build | Maven, via the checked-in wrapper (`./mvnw`) |
| Data | In-memory (`SessionRepository`) — no database |

See [docs/PLAN.md §2](docs/PLAN.md#2-tech-stack) for the full rationale, version history, and
what's stretch-goal (Playwright, Vaadin, Spring AI) vs. already built.

## Project layout

```
src/main/java/com/a11y/a11y_ai_readiness/
├── web/            SessionController (serves both variants), HomeController (landing page)
├── session/         WorkshopSession, SessionRepository (in-memory sample data)
└── registration/     RegistrationForm, MealPreference

src/main/resources/
├── templates/accessible/     sessions.html, register.html, confirmation.html
├── templates/inaccessible/   sessions.html, register.html, confirmation.html
└── static/                   css/main.css (shared), js/fake-widgets.js, img/session-icon.svg
```

## Troubleshooting: embedded server won't start (Windows)

If `./mvnw spring-boot:run` fails with `java.io.IOException: Unable to establish loopback
connection`, that's not this app — it's the JVM's NIO selector failing to open a local socket,
usually because a firewall, antivirus/EDR agent, or VPN client on the machine is blocking it.
Try disabling VPN/third-party antivirus, or allow `java.exe`/`javaw.exe` through Windows Defender
Firewall for private networks, then retry.
