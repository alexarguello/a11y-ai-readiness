# NVDA run sheet

Three screen-reader runs of one task — register for a workshop — against the two variants,
captured with NVDA's Speech Viewer on Firefox on 2026-09-20. This file is both the record of what
happened and the procedure for reproducing it.

Every number below is counted from the verbatim transcripts at the bottom of this file by
`docs/analyze_nvda.py`, not typed by hand.

**[Results](#results)** · [The three findings](#the-three-findings) ·
[How the numbers were derived](#how-the-numbers-were-derived) · [Re-running it](#re-running-it) ·
[Transcripts](#transcripts)

---

## Results

| | A — `/accessible` | B — `/inaccessible`, keyboard | C — `/inaccessible`, mouse + NVDA |
|---|---|---|---|
| Task completed | **yes** — code `JCON-C7ED97` | **no** | **no**, even with a mouse |
| Where it stopped | — | after the filter field; `Tab` leaves the page | silent resubmission loop on meal preference |
| Validation error announced | **yes** — `role="alert"`, immediately | never reached the form | **no** — but see the email caveat below |
| Characters typed | 40 | **6** | **54** |
| `alert` announcements | **2** | 0 | **0** |
| Meal control heard as a `combo box` | 1 | never reached | **0** |
| Meal control heard as plain text | 0 | never reached | **5** |
| "Complete registration" took focus | 1 | **0** | 4 |
| Form re-rendered after a submit | 2 | 0 | **9** |
| Recording length | 2:47 | 0:42 | 3:12 |

Recordings are in `docs/`: `NVDA ACCESSIBLE.mp4`, `NVDA INACCESSIBLE keyboard Only.mp4`,
`NVDA INACCESSIBLE keyboard Mouse.mp4`. The same findings are in `results/nvda-*.json` in the
structured form the dashboard reads.

---

## The three findings

**B — eight keystrokes, and the page is behind you.** `Tab` lands on the filter field, six
characters go in, and the next `Tab` lands on "Browser tabs tool bar" — Firefox's own chrome.
From there focus walks the new-tab button, the navigation toolbar, the sidebar button, the
search-engine picker, the address bar, the bookmark button and the VPN button. The filter box is
the only control on the page a keyboard can reach, and there is no ninth keystroke that helps.
For contrast, A reached a confirmation code in about 60 keystrokes — 40 characters plus roughly
21 navigation and activation keys.

**C — 54 characters, nine re-renders, nothing said.** With a mouse to get past B's block, this
run typed 14 more characters than the successful one and drove the form through nine re-renders,
and NVDA announced `alert` zero times. "Meal preference…" was read five times as plain text and
never once as a control: the fake dropdown's click-to-open never registered, so the selection
never took, so the form could never be satisfied — and nothing ever said which field was wrong.
That is worse than the scripted prediction, which expected three separate observations rather
than one unbreakable loop.

**The precision worth keeping.** The *email* error on `/inaccessible` **was** announced —
"invalid entry … Enter a valid email address." That is the browser's own native `type="email"`
validity leaking through, not the markup: `/inaccessible` never got `aria-invalid`, only
`/accessible` did. The *meal preference* failure, the one that actually blocked the task, was
announced never. Said loosely, this becomes an overclaim an informed questioner will catch.

---

## How the numbers were derived

NVDA echoes every typed character as its own line (`r`, `i`, `v`, … plus word-names for
punctuation: `space`, `at`, `dot`), so the transcripts are keystroke-level.

```bash
python docs/analyze_nvda.py
```

It reads the transcripts straight out of this file, so the results table can be re-derived rather
than remembered. What each run actually typed:

| | Typed |
|---|---|
| A | `RIVERA` `JORDAN` `MAL FORMED` `JORDAN@HOTMAIL.COM` |
| B | `rivera` |
| C | `Riverssa` `jordan smith` `jordan malformed` `jordan@hotmail.com` |

`results/nvda-transcripts/` holds an independent OCR recovery of the same transcripts, used only
as a cross-check — see the README there for why it is not the source of truth.

---

## Re-running it

The step tables below are the **planned** scripts. The live runs followed them in structure but
not character-for-character: the attendee details actually typed are in the table above. The
structural facts (what is focusable, what has a label, what is silent) are certain from the
markup; the exact NVDA phrasing should be confirmed against the Speech Viewer on the day.

### Setup

1. Start NVDA.
2. Open the Speech Viewer so the audience can read what NVDA hears: **NVDA menu (`NVDA+N`) →
   Tools → Speech Viewer.** Dock/position it as its own OBS panel.
3. Open Chrome (or Firefox) fresh, no browsing history/autofill that could pre-fill the form —
   use a clean/incognito window.
4. Navigate to `http://localhost:8080/accessible`.
5. Confirm NVDA is in **Browse Mode** on page load (default). It switches to **Focus Mode**
   automatically on entering a form field — no manual mode-switching needed in this script.

### Script A — `/accessible`, full task, keyboard only

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

Attendee as planned: **name** "Jordan Lee", **email** (deliberately malformed first)
"jordan-not-an-email", **meal** Vegetarian, **terms** accepted.

| # | Key(s) | Expected NVDA announcement | Notes |
|---|---|---|---|
| 1 | `Tab` | "Filter sessions, edit, blank" | Label announced — real `<label for>` |
| 2 | Type `Rivera` | (typing echo) | Filters to speaker "A. Rivera" |
| 3 | `Enter` | "JCON Workshops, heading level 1" (page reload) | Single-input form submits on Enter |
| 4 | `Tab` | "Filter sessions, edit, Rivera" | |
| 5 | `Tab` | "Filter, button" | |
| 6 | `Tab` | "Clear filter, link" | Only present because a filter is active |
| 7 | `Tab` | "Register for Building Accessible Forms in Java, link" | Full context in the *announced* name even though the visible text just says "Register" |
| 8 | `Enter` | "Register: Building Accessible Forms in Java, heading level 1" | |
| 9 | `Tab` | "Back to sessions, link" | Inside a `<nav>` landmark |
| 10 | `Tab` | "Full name, edit, blank" | |
| 11 | Type `Jordan Lee` | (typing echo) | |
| 12 | `Tab` | "Email address, edit, blank" | |
| 13 | Type `jordan-not-an-email` | (typing echo) | Deliberately malformed — `novalidate` on the form means the browser won't intercept this before our own validation sees it |
| 14 | `Tab` | "Meal preference, combo box, Choose one…" | |
| 15 | Press `V` | "Vegetarian" | First-letter navigation in a native `<select>` |
| 16 | `Tab` | "I accept the workshop terms and conditions, checkbox, not checked" | |
| 17 | `Space` | "checked" | |
| 18 | `Tab` | "Complete registration, button" | |
| 19 | `Enter` | **"There's a problem with your registration. Please fix the highlighted fields below. Alert"** | This is the decisive moment — an async fetch swap, not a page reload, so this is a genuine live-region announcement, and JS also moves focus onto the alert directly |
| 20 | `Tab` | "Full name, edit, Jordan Lee" | Values are retained |
| 21 | `Tab` | "Email address, edit, invalid entry, jordan-not-an-email, Enter a valid email address" | `aria-invalid` + `aria-describedby` both fire here |
| 22 | Select-all + type `jordan.lee@example.com` | (typing echo) | Corrects the error |
| 23 | `Tab` `Tab` `Tab` | (Meal preference, Accept terms — already retained as Vegetarian / checked) | |
| 24 | `Tab` | "Complete registration, button" | |
| 25 | `Enter` | "You're registered! heading level 1" | Focus lands on the new heading; confirmation code is read on the next line down |

**25 steps**, counting each typed value as one. As individual keypresses that is about 60,
which is the figure in the results table.

### Script B — `/inaccessible`, keyboard only

Same task, same order, starting fresh at `http://localhost:8080/inaccessible`.

| # | Key(s) | Expected NVDA announcement | Notes |
|---|---|---|---|
| 1 | `Tab` | "edit, blank" — **no field name at all**, or possibly "Filter sessions…, edit" if the browser falls back to using the placeholder text as the accessible name | Chrome/Firefox both implement the HTML-AAM placeholder-as-fallback-name rule, so don't be surprised if NVDA says *something* here — the real failure isn't silence on this one field, it's that the label **disappears the moment you start typing** (placeholder text vanishes on input), unlike `/accessible`'s persistent `<label>` |
| 2 | Type `Rivera` | (typing echo) | |
| 3 | `Enter` | page reloads, filtered | The "Filter" control itself is a `<div>` with no button semantics — Enter-to-submit only works because it's still a real `<input>` inside a real `<form>` |
| 4 | `Tab` | "edit, Rivera" (or similar) | |
| 5 | `Tab` | **nothing happens — focus leaves the page entirely** (moves to the browser's own UI, e.g. the address bar) | **Task ends here.** The "Filter" control, the "×" clear-filter, and every "Register" action are `<div>`/`<span>` elements with no `tabindex` — there is no keyboard path to registering for a workshop at all. This is the predicted outcome exactly: "task not completable." |

**The task fails at step 5 — not completable.** Blunt enough to need no narration.

### Script C — `/inaccessible`, mouse + NVDA

Script B proves the task is impossible unaided. This pass uses the mouse to get past that block
on purpose, so the recording shows the *other* failures stacked underneath the first one.

| Action | Expected NVDA announcement | Notes |
|---|---|---|
| Click into the "Full name" field | "edit, blank" (placeholder read as name, or nothing) | Same placeholder caveat as above; label still isn't persistent |
| Tab to the meal-preference control | **Tab skips over it entirely** | The custom dropdown (`.fake-select`) has no `tabindex` on its trigger — even with the mouse used to *get to* this page, the dropdown is still unusable by keyboard. Clicking it with the mouse opens a plain `<div>` list with no `role="listbox"`/`role="option"`, so NVDA announces none of it as a picker at all — just inert text |
| Click the checkbox, then submit with a malformed email (mouse click on the "Complete registration" div) | **nothing announced** | No `role="alert"`, no live region, no focus movement — the only change is a CSS border-color swap. Confirm on the Speech Viewer that literally nothing prints when this happens; that silence *is* the finding |

---

## Transcripts

Verbatim Speech Viewer output, unedited. These are the source the counts above are derived from,
collapsed because they are keystroke-level and long.

<details>
<summary><strong>Script A — /accessible, keyboard only</strong> — verbatim Speech Viewer output, 154 lines</summary>

<!-- transcript:A -->

```text
Register — Building Accessible Forms in Java — Personal — Mozilla Firefox Private Browsing
Register — Building Accessible Forms in Java
banner landmark    breadcrumb  navigation landmark    visited  link    ← Back to sessions
heading    level 1  Register: Building Accessible Forms in Java
A.
Register: Building Accessible Forms in Java
Register: Building Accessible Forms in Java
A. Rivera — Tue 10:00
Full name
Full name
Email address
Email address
Start recording  button
a11y-ai-readiness — Personal — Mozilla Firefox Private Browsing
System audio unmuted. Select to mute system audio.  button
DesktopWindowXamlSource
Extensions
Other Bookmarks
— same task, div/span soup instead
/accessible
a11y-ai-readiness — Personal — Mozilla Firefox Private Browsing
a11y-ai-readiness  document
•   visited  link    /accessible   — real semantics: button, label, select, role="alert"
/accessible  visited  link  
JCON Workshops — accessible
main landmark    table  with 6 rows and 5 columns  row 2  Action  column 5  visited  link    Register  visited  link    for Building Accessible Forms in Java
row 3  Title  column 1  Spring AI: Building Tool-Calling Agents
Speaker  column 2  J.
main landmark    JCON workshop sessions  table  with 6 rows and 5 columns  row 3  Action  column 5  Register  for Spring AI: Building Tool-Calling Agents  link  
row 4  Register  for Playwright for Java: Role-Based Locators  link  
row 5  Register  for Virtual Threads in Production  link  
row 6  Register  for Vaadin Components: Accessible by Default  visited  link  
Browser tabs  tool bar
tab control
JCON Workshops — accessible  tab  selected  1 of 1  level 1
Open a new tab (Ctrl+T)  button  2 of 2
Navigation  tool bar
Sidebars  button  Show sidebar (Alt+Ctrl+Z)  1 of 4
Pick a Search Engine  button  collapsed  subMenu  Google, pick a search engine
Search with Google or enter address  combo box  collapsed  has auto complete  editable  selected http://localhost:8080/accessible
Bookmark this page (Ctrl+D)  button
VPN  button  1 of 5
Bookmarks  tool bar
TV  button  subMenu  1 of 8
JCON Workshops — accessible  document
visited  link    Register
JCON Workshops — accessible  document
main landmark
Filter sessions  edit  has auto complete  e.g. accessible, Spring AI, Rivera  blank
R
I
V
E
R
A
Filter  button  
JCON Workshops — accessible
banner landmark    heading    level 1  JCON Workshops
main landmark    clickable  Filter sessions
edit  has auto complete    RIVERA
button    Filter
visited  link    Clear filter
table  with 2 rows and 5 columns  caption    JCON workshop sessions  out of caption
row 1  column 1  Title
column 2  Speaker
column 3  Time
column 4  Format
main landmark
Filter sessions  edit  has auto complete  RIVERA
Filter  button  
Clear filter  visited  link  
JCON workshop sessions  table  with 2 rows and 5 columns  row 2  Action  column 5  Register  for Building Accessible Forms in Java  visited  link  
Register — Building Accessible Forms in Java
banner landmark    breadcrumb  navigation landmark    visited  link    ← Back to sessions
heading    level 1  Register: Building Accessible Forms in Java
A.
main landmark
Full name  edit  has auto complete  blank
J
O
R
D
A
N
Email address  edit  has auto complete  blank
M
invalid entry
A
L
space
F
O
R
M
E
D
banner landmark    Register: Building Accessible Forms in Java  heading    level 1
alert  There's a problem with your registration. Please fix the highlighted fields below.
main landmark
Full name  edit  has auto complete  selected JORDAN
Email address  edit  invalid entry  has auto complete  Enter a valid email address.  selected MAL FORMED
J
selection removed
O
R
D
A
N
at
H
O
T
M
A
I
L
dot
C
O
M
Meal preference  combo box  Choose one…  collapsed  invalid entry  Choose a meal preference.
Standard
Vegetarian
Vegan
I accept the workshop terms and conditions  check box  not checked  invalid entry    You must accept the terms to register.
checked
Complete registration  button  
main landmark    You're registered!  heading    level 1
banner landmark    breadcrumb  navigation landmark  ← Back to sessions  visited  link  
Browser tabs  tool bar
tab control
Registration confirmed  tab  selected  1 of 1  level 1
Open a new tab (Ctrl+T)  button  2 of 2
Navigation  tool bar
Sidebars  button  Show sidebar (Alt+Ctrl+Z)  1 of 4
Pick a Search Engine  button  collapsed  subMenu  Google, pick a search engine
Search with Google or enter address  combo box  collapsed  has auto complete  editable  selected http://localhost:8080/accessible/register/accessible-forms
Bookmark this page (Ctrl+D)  button
VPN  button  1 of 5
Bookmarks  tool bar
TV  button  subMenu  1 of 8
Registration confirmed  document
visited  link    ← Back to sessions
Browser tabs  tool bar
tab control
Registration confirmed  tab  selected  1 of 1  level 1
Open a new tab (Ctrl+T)  button  2 of 2
Thanks, JORDAN. Your registration for Building Accessible Forms in Java is confirmed.
Your confirmation code:
Thanks, JORDAN. Your registration for Building Accessible Forms in Java is confirmed.
You're registered!
Thanks, JORDAN. Your registration for Building Accessible Forms in Java is confirmed.
Your confirmation code:
JCON-C7ED97
```

</details>

<details>
<summary><strong>Script B — /inaccessible, keyboard only</strong> — verbatim Speech Viewer output, 80 lines</summary>

<!-- transcript:B -->

```text
Other Bookmarks
Account
DesktopWindowXamlSource
Resume recording  button
Recording resumed
VPN
Same registration flow, same backend, same CSS file — only the markup differs.
— same task, div/span soup instead
/inaccessible
a11y-ai-readiness — Personal — Mozilla Firefox Private Browsing
document
main landmark
list
link  visited
JCON Workshops — inaccessible — Personal — Mozilla Firefox Private Browsing
unknown
JCON Workshops — inaccessible — Personal — Mozilla Firefox Private Browsing
JCON Workshops — inaccessible
JCON Workshops — inaccessible
JCON Workshops
Filter sessions…  edit  has auto complete  
Filter
Title
Speaker
Time
Format
Action
Building Accessible Forms in Java
A.
Filter sessions…  edit  has auto complete  blank
r
i
v
e
r
a
Browser tabs  tool bar
tab control
JCON Workshops — inaccessible  tab  selected  1 of 1  level 1
Open a new tab (Ctrl+T)  button  2 of 2
Navigation  tool bar
Sidebars  button  Show sidebar (Alt+Ctrl+Z)  1 of 4
Pick a Search Engine  button  collapsed  subMenu  Google, pick a search engine
Search with Google or enter address  combo box  collapsed  has auto complete  editable  selected http://localhost:8080/inaccessible
Bookmark this page (Ctrl+D)  button
VPN  button  1 of 5
Bookmarks  tool bar
TV  button  subMenu  1 of 8
JCON Workshops — inaccessible  document
Filter sessions…  edit  has auto complete  rivera
JCON Workshops — inaccessible
JCON Workshops — inaccessible
JCON Workshops
Filter sessions…  edit  has auto complete    rivera
Filter
×  Title
Speaker
Time
Format
Action
Building Accessible Forms in Java
Filter sessions…  edit  has auto complete  rivera
Browser tabs  tool bar
tab control
JCON Workshops — inaccessible  tab  selected  1 of 1  level 1
Title
Filter
http://localhost:8080/inaccessible?q=rivera
Browser tabs
Private browsing
Browser tabs
DesktopWindowXamlSource
Recording toolbar
Stop recording  button
Microphone unmuted. Select to mute microphone.  button
JCON Workshops — inaccessible — Personal — Mozilla Firefox Private Browsing
Snipping Tool
Play  button  Alt,   P    Space
Capture mode switched
DesktopWindowXamlSource
```

</details>

<details>
<summary><strong>Script C — /inaccessible, mouse + NVDA</strong> — verbatim Speech Viewer output, 173 lines</summary>

<!-- transcript:C -->

```text
/inaccessible
/inaccessible  visited  link  
JCON Workshops — inaccessible  busy
Filter sessions…  edit  has auto complete  
Filter
Title
Speaker
Time
Format
Action
Building Accessible Forms in Java
A.
Filter sessions…  edit  has auto complete  blank
R
i
v
e
r
s
s
a
Browser tabs  tool bar
tab control
JCON Workshops — inaccessible  tab  selected  1 of 1  level 1
Rivera
Filter
JCON Workshops — inaccessible  document   
JCON Workshops — inaccessible
JCON Workshops
Filter sessions…  edit  has auto complete    Rivera
Filter
×  Title
Speaker
Time
Format
Action
Building Accessible Forms in Java
Filter
Title
Building Accessible Forms in Java
A. Rivera
Tue 10:00
Register
Register — Building Accessible Forms in Java
← Back to sessions
Register: Building Accessible Forms in Java
A.
Full name  edit  has auto complete  blank
j
o
r
d
a
n
space
s
m
i
t
h
Email address  edit  has auto complete  blank
Register — Building Accessible Forms in Java
Email address  edit  has auto complete  
Meal preference…
check box  not checked  
I accept the workshop terms and conditions
button    Complete registration

Full name  edit  has auto complete  selected jordan smith
Email address  edit  has auto complete  blank
j
invalid entry
o
r
d
a
n
space
m
a
l
f
o
r
m
e
d
Register — Building Accessible Forms in Java
Register — Building Accessible Forms in Java
Email address  edit  invalid entry  has auto complete    jordan malformed
Meal preference…
check box  not checked  
I accept the workshop terms and conditions
button    Complete registration

check box  not checked  
checked
Complete registration  button  
Register — Building Accessible Forms in Java
button    Complete registration

jordan smith
jordan smith
jordan malformed
jordan smith
Full name  edit  has auto complete  jordan smith
Email address  edit  invalid entry  has auto complete  selected jordan malformed
check box  checked  
Complete registration  button  
Register — Building Accessible Forms in Java
button    Complete registration

Full name  edit  has auto complete  selected jordan smith
Email address  edit  invalid entry  has auto complete  selected jordan malformed
j
selection removed
o
r
d
a
n
at
h
o
t
m
a
i
l
dot
invalid entry
c
o
m
Register — Building Accessible Forms in Java
Email address  edit  has auto complete    jordan@hotmail.com
Meal preference…
check box  checked  
I accept the workshop terms and conditions
button    Complete registration

check box  checked  
Complete registration  button  
Register — Building Accessible Forms in Java
button    Complete registration

jordan smith
jordan@hotmail.com
Meal preference…
I accept the workshop terms and conditions
Complete registration
Complete registration  button  
Register — Building Accessible Forms in Java
button    Complete registration

Complete registration
I accept the workshop terms and conditions
Meal preference…
jordan@hotmail.com
jordan smith
jordan smith
Register: Building Accessible Forms in Java
Bookmarks
VPN
DesktopWindowXamlSource
Recording toolbar
Stop recording  button
Microphone unmuted. Select to mute microphone.  button
Register — Building Accessible Forms in Java — Personal — Mozilla Firefox Private Browsing
Snipping Tool
Play  button  Alt,   P    Space
Capture mode switched
DesktopWindowXamlSource
```

</details>
