## Review cycle 1 — 2026-10-08

STATUS: APPROVED

CRITICAL:
- none

WARNINGS:
- services/menu-planner-ui/app/components/__tests__/ShoppingList.test.tsx, test "is not hidden by the print stylesheet": it is close to vacuous. The split text lives in a plain `<span>` with no classes, and the test only inspects that span and its descendants. The comment says "Every ancestor/own class", but ancestors are never walked. The real print-hidden hooks (`.ingredient-chevron`, `.ingredient-breakdown`, `.shopping-list-controls`) are siblings or ancestors, so a regression such as moving the split under a hidden class would not be caught. It also does not guard against `css.indexOf('@media print')` returning -1, which makes `slice(-1)` silently return one character. Fix: walk `parentElement` up to the container, and assert the print block was found.
- The `.split('}')` parsing of the CSS is fragile. It will mis-handle the nested `@media print {` block (the first chunk carries the `@media print {` prefix). It works today only by accident.

SUGGESTIONS:
- ShoppingListResourceTest.java: add a blank line before `@SuppressWarnings` on `amountsOf`. It currently abuts the closing brace of the previous test.
- `buildParts` keys on `raw.quantity() + "|" + raw.unit()` (float to string). That is fine here, but a short comment on why float string equality is safe would help.
- Backend sort tie-break (equal base value, different unit, e.g. 1 kg vs 1000 g) is deterministic by unit string but untested. A small test would pin it.

Spec conformance: parts are computed in the backend per group. They use original normalised units, are sorted by base value descending, and collapse identical quantity and unit. They are empty when there are fewer than two contributions. No-quantity lines are skipped before the parts are collected. The TS types are updated and `parts` is optional for backwards compatibility. Backend and frontend tests cover every listed case. The split is rendered inline and the print CSS does not hide it (checked manually in globals.css). I did not run the test suites.

SUMMARY: Looks good; optionally harden the print-stylesheet test so it actually checks ancestors.

---json
{
  "status": "APPROVED",
  "critical": [],
  "warnings": [
    "Print-stylesheet test is near-vacuous: only checks the split span and descendants (no classes), never walks ancestors, and does not assert '@media print' was found",
    "Fragile CSS parsing via split('}') in that test"
  ],
  "summary": "Looks good."
}
---
