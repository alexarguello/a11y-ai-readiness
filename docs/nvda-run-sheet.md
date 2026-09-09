# NVDA run sheet — task 5 (docs/PLAN.md)

Printable script + scorecard for the screen-reader head-to-head (beat sheet §8, 3:00–6:00) and
the cold open (§8, 0:00–1:30). Run this live while recording — the **structural facts** below
(what's focusable, what has a label, what's silent) are certain from the markup itself; the
**exact NVDA phrasing** is what NVDA actually says and should be confirmed/corrected against the
Speech Viewer during the real run, not assumed from this document.

## Setup

1. Start NVDA.
2. Open the Speech Viewer so the audience can read what NVDA hears: **NVDA menu (`NVDA+N`) →
   Tools → Speech Viewer.** Dock/position it as its own OBS panel per §8's recording setup.
3. Open Chrome (or Firefox) fresh, no browsing history/autofill that could pre-fill the form —
   use a clean/incognito window.
4. Navigate to `http://localhost:8080/accessible`.
5. Confirm NVDA is in **Browse Mode** on page load (default). It switches to **Focus Mode**
   automatically on entering a form field — no manual mode-switching needed in this script.

---

## Script A — `/accessible`, full task, keyboard only

**Confirmed against a live NVDA + Firefox run (Speech Viewer transcript).** The whole flow
completed as scripted, including the decisive step: the async `role="alert"` fired immediately
("alert There's a problem with your registration...") with the URL never changing during that
round-trip, and `aria-invalid` + `aria-describedby` together produced "invalid entry ... Enter a
valid email address." on the broken field. Two non-defects observed worth knowing about before
recording:

- **Linear browse-mode reads a Register link's name twice** ("visited link Register visited link
  for Building Accessible Forms in Java") the first time NVDA reads through the whole page
  top-to-bottom, because the visually-hidden context span is a nested inline element. When you
  actually **Tab to** the link (or check it in the Elements List), it reads correctly as one
  link with the full name. Don't "fix" this — it's a browse-mode reading quirk, not a broken name.
- **First-letter navigation in the `<select>` isn't guaranteed to land on Vegetarian in one
  press** — the live run showed "Standard" announced before "Vegetarian" (arrow-key behavior can
  differ from press-the-first-letter depending on what's focused). Budget 1–2 keypresses for this
  step, not exactly one.

Attendee for this run: **name** "Jordan Lee", **email** (deliberately malformed first)
"jordan-not-an-email", **meal** Vegetarian, **terms** accepted.

| # | Key(s) | Expected NVDA announcement | Notes |
|---|---|---|---|
| 1 | `Tab` | "Filter sessions, edit, blank" | Label announced — real `<label for>` |
| 2 | Type `Rivera` | (typing echo) | Filters to speaker "A. Rivera" |
| 3 | `Enter` | "JCON Workshops, heading level 1" (page reload) | Single-input form submits on Enter |
| 4 | `Tab` | "Filter sessions, edit, Rivera" | |
| 5 | `Tab` | "Filter, button" | |
| 6 | `Tab` | "Clear filter, link" | Only present because a filter is active |
| 7 | `Tab` | "Register for Building Accessible Forms in Java, link" | Full context in the *announced* name even though the visible text just says "Register" — see §4 |
| 8 | `Enter` | "Register: Building Accessible Forms in Java, heading level 1" | |
| 9 | `Tab` | "Back to sessions, link" | Inside a `<nav>` landmark |
| 10 | `Tab` | "Full name, edit, blank" | |
| 11 | Type `Jordan Lee` | (typing echo) | |
| 12 | `Tab` | "Email address, edit, blank" | |
| 13 | Type `jordan-not-an-email` | (typing echo) | Deliberately malformed (§3.4) — `novalidate` on the form means the browser won't intercept this before our own validation sees it |
| 14 | `Tab` | "Meal preference, combo box, Choose one…" | |
| 15 | Press `V` | "Vegetarian" | First-letter navigation in a native `<select>` |
| 16 | `Tab` | "I accept the workshop terms and conditions, checkbox, not checked" | |
| 17 | `Space` | "checked" | |
| 18 | `Tab` | "Complete registration, button" | |
| 19 | `Enter` | **"There's a problem with your registration. Please fix the highlighted fields below. Alert"** | This is the decisive moment (§3.4) — an async fetch swap, not a page reload, so this is a genuine live-region announcement, and JS also moves focus onto the alert directly |
| 20 | `Tab` | "Full name, edit, Jordan Lee" | Values are retained |
| 21 | `Tab` | "Email address, edit, invalid entry, jordan-not-an-email, Enter a valid email address" | `aria-invalid` + `aria-describedby` both fire here |
| 22 | Select-all + type `jordan.lee@example.com` | (typing echo) | Corrects the error |
| 23 | `Tab` `Tab` `Tab` | (Meal preference, Accept terms — already retained as Vegetarian / checked) | |
| 24 | `Tab` | "Complete registration, button" | |
| 25 | `Enter` | "You're registered! heading level 1" | Focus lands on the new heading; confirmation code is read on the next line down |

**Keystrokes to completion: 25** (count every row above, including typed-text entries as one
"action" each — adjust the scorecard's raw keystroke count to actual keys-per-character if you
want that level of granularity).

---

## Script B — `/inaccessible`, keyboard only

Same task, same order, starting fresh at `http://localhost:8080/inaccessible`.

| # | Key(s) | Expected NVDA announcement | Notes |
|---|---|---|---|
| 1 | `Tab` | "edit, blank" — **no field name at all**, or possibly "Filter sessions…, edit" if the browser falls back to using the placeholder text as the accessible name | Chrome/Firefox both implement the HTML-AAM placeholder-as-fallback-name rule, so don't be surprised if NVDA says *something* here — the real failure isn't silence on this one field, it's that the label **disappears the moment you start typing** (placeholder text vanishes on input), unlike `/accessible`'s persistent `<label>` |
| 2 | Type `Rivera` | (typing echo) | |
| 3 | `Enter` | page reloads, filtered | The "Filter" control itself is a `<div>` with no button semantics — Enter-to-submit only works because it's still a real `<input>` inside a real `<form>` |
| 4 | `Tab` | "edit, Rivera" (or similar) | |
| 5 | `Tab` | **nothing happens — focus leaves the page entirely** (moves to the browser's own UI, e.g. the address bar) | **Task ends here.** The "Filter" control, the "×" clear-filter, and every "Register" action are `<div>`/`<span>` elements with no `tabindex` — there is no keyboard path to registering for a workshop at all. This matches §9's expected outcome exactly: "task not completable." |

**Keystrokes to completion: task fails at keystroke 5 — not completable.** This is the cold-open
material (§8, 0:00–1:30): it's fast, it's blunt, and it needs no narration.

---

## Script C — `/inaccessible`, mouse + NVDA (for narrative depth beyond the hard block)

Script B proves the task is impossible unaided. This pass uses the mouse to get past that block
specifically so the recording has more than one beat to show in the 3:00–6:00 segment — it
demonstrates the *other* failures stacked underneath the first one.

| Action | Expected NVDA announcement | Notes |
|---|---|---|
| Click into the "Full name" field | "edit, blank" (placeholder read as name, or nothing) | Same placeholder caveat as above; label still isn't persistent |
| Tab to the meal-preference control | **Tab skips over it entirely** | The custom dropdown (`.fake-select`) has no `tabindex` on its trigger — even with the mouse used to *get to* this page, the dropdown is still unusable by keyboard. Clicking it with the mouse opens a plain `<div>` list with no `role="listbox"`/`role="option"`, so NVDA announces none of it as a picker at all — just inert text |
| Click the checkbox, then submit with a malformed email (mouse click on the "Complete registration" div) | **nothing announced** | No `role="alert"`, no live region, no focus movement — the only change is a CSS border-color swap. Confirm on the Speech Viewer that literally nothing prints when this happens; that silence *is* the finding |

---

## Scorecard (fill in live, per docs/PLAN.md §7)

| Metric | `/accessible` | `/inaccessible` |
|---|---|---|
| Task completes unaided (keyboard only) | ☐ yes / ☐ no | ☐ yes / ☐ no *(expect: no)* |
| Keystrokes to completion | ____ (script says 25) | ____ (script says: fails at 5) |
| Time to completion | ____ | ____ (time to the point of failure) |
| % of controls announcing full name + role + state | ____ % | ____ % |
| Count of dead announcements ("blank", "clickable", "unlabeled", or silent) | ____ | ____ |
| Was the validation error announced at all? | ☐ yes / ☐ no *(expect: yes — live)* | ☐ yes / ☐ no *(expect: no)* |

Write the actual numbers into this table during the recorded run, then transcribe them into
`results/` per §6.8 (all numbers generated/observed, not typed from memory) before they go on
any slide.
