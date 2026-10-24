# NVDA transcripts — OCR cross-check only

**These are not the source of truth.** The verbatim Speech Viewer transcripts live in
[`docs/nvda-run-sheet.md`](../../docs/nvda-run-sheet.md), pasted under each script, and every
number in the scorecard is counted from those by `docs/analyze_nvda.py`.

The files here were recovered independently, by sampling frames from the three recordings and
running the Windows OCR engine over the Speech Viewer panel (`docs/ocr_frames.ps1`). They are kept
for one reason: they were produced without looking at the run sheet, and for Script B they agree
with it line for line — including the six single-character echoes `r i v e r a` and the exact
point where focus leaves the page. That is a useful independent confirmation that the transcripts
in the run sheet are complete and unedited.

Two known OCR limitations, which is why these files are not used for counting:

- isolated single characters are dropped unpredictably, so typing echo is undercounted;
- the Speech Viewer panel scrolls, so stitching frames can repeat lines, which inflates any
  count taken from them.
