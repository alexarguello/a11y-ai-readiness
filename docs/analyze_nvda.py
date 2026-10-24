"""Count what the three NVDA runs actually did, from their Speech Viewer transcripts.

The transcripts are pasted verbatim into docs/nvda-run-sheet.md under each
script. NVDA echoes every typed character as its own line ("r", "i", "v", …,
plus word-names for punctuation: "space", "at", "dot"), so the transcripts are
keystroke-level for typing, and announce each control as it takes focus.

    python docs/analyze_nvda.py

Every number the run sheet's scorecard quotes comes from here, so it can be
re-derived rather than remembered.

An earlier pass recovered the same transcripts by OCR-ing the recordings
(docs/ocr_frames.ps1 + results/nvda-transcripts/). That was replaced by this
once the verbatim text turned out to be in the run sheet already; the OCR
output is kept only as an independent cross-check, and it agreed with Script B
line for line.
"""
import re
import sys

RUN_SHEET = "docs/nvda-run-sheet.md"

# NVDA speaks these punctuation names; each is one keypress.
PUNCT = {"space", "dot", "at", "dash", "hyphen", "comma", "underscore", "slash",
         "colon", "semicolon", "period", "minus", "plus", "star", "bang"}

# Screen-recorder and OS chrome the Speech Viewer also picked up.
RECORDER = re.compile(
    r"^(Recording|Snipping|Capture mode|DesktopWindow|Microphone|Start recording|"
    r"Stop recording|System audio|Play  button)")

SCRIPTS = {
    "A  /accessible, keyboard only": "RESULT :",
    "B  /inaccessible, keyboard only": "Results:",
    "C  /inaccessible, mouse + NVDA": "Result SpeechViewer:",
}


def block(lines, marker):
    """The transcript pasted after `marker`, up to the next heading or rule."""
    start = next(i for i, l in enumerate(lines) if l.strip() == marker) + 1
    end = len(lines)
    for j in range(start, len(lines)):
        if lines[j].startswith("## ") or lines[j].startswith("---"):
            end = j
            break
    return [l.strip() for l in lines[start:end] if l.strip() and not RECORDER.match(l.strip())]


def counts(blk):
    typed = [l for l in blk if (len(l) == 1 and l.isalnum()) or l.lower() in PUNCT]
    return {
        "transcript lines": len(blk),
        "characters typed": len(typed),
        "what was typed": "".join(t if len(t) == 1 else f"[{t}]" for t in typed),
        "'alert' announcements": sum(1 for l in blk if re.search(r"\balert\b", l, re.I)),
        "meal control announced as a combo box":
            sum(1 for l in blk if "combo box" in l.lower() and "meal" in l.lower()),
        "meal control read as plain text":
            sum(1 for l in blk if l.startswith("Meal preference…") and "combo" not in l),
        "'Complete registration' took focus":
            sum(1 for l in blk if re.match(r"^Complete registration\s+button", l)),
        "form re-rendered after a submit":
            sum(1 for l in blk if l == "Register — Building Accessible Forms in Java"),
        "confirmation code": next(
            (re.search(r"JCON-[A-Z0-9]{6}", l).group(0) for l in blk
             if re.search(r"JCON-[A-Z0-9]{6}", l)), "none"),
    }


def main():
    lines = open(RUN_SHEET, encoding="utf-8").read().splitlines()
    for name, marker in SCRIPTS.items():
        try:
            blk = block(lines, marker)
        except StopIteration:
            print(f"!! marker {marker!r} not found for Script {name}", file=sys.stderr)
            continue
        print(f"\n=== Script {name} ===")
        for k, v in counts(blk).items():
            print(f"  {k:38}: {v}")


if __name__ == "__main__":
    main()
