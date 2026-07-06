## Review cycle 1 — 2026-07-05

STATUS: APPROVED

CRITICAL:
- none

WARNINGS:
- **Blank-page regression on browser-level Ctrl+P (closed panel):** The `@media print` block unconditionally hides `.meal-plan-main-header` and `.recipe-grid`. The shopping list overlay is only mounted when `isShoppingListOpen` is `true` (conditional render: `{isShoppingListOpen && ...}`). If a user triggers print via Ctrl+P or File → Print while the panel is closed, there is nothing to print and the output is a blank page. Before this commit, Ctrl+P printed the full meal-plan view. Fix: gate the hide-main-content rules on a body-level class applied when the panel is open (e.g. `body.shopping-list-open .meal-plan-main-header { display: none }`) so browser-initiated print falls back to the full view when the panel is closed.
- **Missing `type="button"` on the print button:** Buttons default to `type="submit"` in a form context. The print button (and the pre-existing close button) both omit this attribute. It is safe today because neither is inside a `<form>`, but it is a fragile assumption. The `IngredientRow` expand button in `ShoppingList.tsx` correctly uses `type="button"` — the new button should match.

SUGGESTIONS:
- The `waitFor` wrapping the print-button assertion in the new test (`waitFor(() => expect(getByRole('button', { name: /print shopping list/i })).toBeInTheDocument())`) is not necessary because the print button is part of the panel header, which renders synchronously when the overlay mounts — only the list contents are async. Removing it would make the test intent clearer, though it causes no harm.
- No automated test covers the print CSS rules (not feasible in Jest/JSDOM). Consider noting this in a comment so future maintainers know to verify print layout manually when changing `globals.css`.

SUMMARY: The feature meets all five spec requirements and is well-tested; ship after addressing the unconditional `@media print` content-hide regression that produces a blank page when a user prints via Ctrl+P while the shopping list panel is closed.

---json
{
  "status": "APPROVED",
  "critical": [],
  "warnings": [
    "Blank-page regression on browser-level Ctrl+P when the shopping list panel is closed: the @media print block unconditionally hides .meal-plan-main-header and .recipe-grid, but the shopping list pane is only in the DOM when isShoppingListOpen is true, so Ctrl+P with the panel closed prints a blank page.",
    "Print button (and pre-existing close button) lack type=\"button\"; safe today since neither is inside a <form>, but inconsistent with the type=\"button\" on the IngredientRow expand button in ShoppingList.tsx."
  ],
  "summary": "Ship after addressing the unconditional @media print content-hide regression that produces a blank page when a user prints via Ctrl+P while the shopping list panel is closed."
}
---

## Review cycle 2 — 2026-07-06

STATUS: APPROVED

CRITICAL:
- none

WARNINGS:
- **No test for `app-shell`/`app-main` classes in `layout.tsx`:** The two class additions in `layout.tsx` (`app-shell` on the outer flex div, `app-main` on `<main>`) are the key hooks for the list-truncation fix, but no test verifies they exist. Layout-level testing in Next.js App Router is non-trivial, but at minimum an integration smoke test or a comment referencing the print CSS dependency would make the coupling explicit. If a future refactor removes those classes, the truncation bug silently returns.
- **Fragile count assertion for the mobile section `app-sidebar`:** The first print-class test uses `expect(appSidebarElements.length).toBeGreaterThanOrEqual(3)`. The mobile section wrapper (`<div className="md:hidden app-sidebar">`) has no `data-testid`, so it cannot be targeted directly; the count check is the only coverage it gets. `toBeGreaterThanOrEqual(3)` would pass even if four or five elements unexpectedly acquired the class. The two subsequent targeted tests cover `sidebar-desktop` and `sidebar-mobile-spacer` precisely, but the mobile section wrapper is only covered by this loose count.

SUGGESTIONS:
- Add `data-testid="sidebar-mobile-section"` to the outer `<div className="md:hidden app-sidebar">` in `Sidebar.tsx`. This would let the third test in the print-class describe block query it directly, and the first test could then use `toBe(3)` for an exact count.
- The `overflow: visible !important` rule on `body.shopping-list-open .app-shell` is technically redundant — the outer shell div has no explicit `overflow` set, so its computed value is already `visible`. The load-bearing fix is the `overflow: visible !important` on `app-main` (which carries `overflow-y-auto`). The shell rule is harmless but could be removed to reduce noise in the CSS.
- No automated test can exercise print CSS in Jest/JSDOM. A brief comment above the `@media print` block (or in `layout.tsx`) cross-referencing the classes would help future maintainers understand why `app-shell`, `app-main`, and `app-sidebar` exist and that removing them will silently break print.

SUMMARY: Looks good — all three reported bugs (sidebar leak, page-header leak, list truncation) are correctly addressed with targeted CSS hooks and matching tests; add a `data-testid` to the mobile section wrapper to eliminate the fragile count assertion and make test intent unambiguous.

---json
{
  "status": "APPROVED",
  "critical": [],
  "warnings": [
    "No test verifies that app-shell and app-main classes exist on the layout.tsx elements; removing them in a future refactor would silently reintroduce the list-truncation bug.",
    "The first Sidebar print-class test uses toBeGreaterThanOrEqual(3) because the mobile section wrapper has no data-testid, making it a loose assertion that cannot directly target the element it intends to cover."
  ],
  "summary": "Looks good."
}
---
