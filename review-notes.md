## Review cycle 1 — 2026-07-05

STATUS: NEEDS_CHANGES

CRITICAL:
- **Slide animation is broken.** The overlay is conditionally rendered (`{isExpanded && (...)}`) so it jumps into existence at `translate-x-0` — there is no actual transition. The `transition-transform duration-300` class on a freshly-mounted element does nothing because the element starts life in its final position. To implement a real slide-in, the overlay must exist in the DOM in the collapsed state (e.g. `translate-x-[-100%]`) and transition to `translate-x-0` when expanded. The design spec explicitly requires "slide animation".
- **`pl-14` applied to unauthenticated pages on mobile.** `layout.tsx` unconditionally sets `pl-14 md:pl-0` on `<main>`, adding 56 px of left padding on mobile for every route. `Sidebar` returns `null` for unauthenticated users (login/signup), so no rail is rendered — but the padding still applies, pushing the login and signup forms off-center on mobile.

WARNINGS:
- **Overlay content is visually obscured by the rail when expanded.** Both the overlay (`z-30`) and the rail (`z-30`) are anchored at `left-0`. Because the rail is rendered later in the DOM, it stacks on top of the left 56 px of the overlay. The overlay's nav items only have `px-4` (16 px) of left padding, and the header only `px-6` (24 px), neither of which clears the 56 px rail. The emoji icons and first characters of nav labels will be hidden under the rail when expanded. Either raise the overlay to `z-40` (so it covers the rail), or offset it to `left-14` so it starts to the right of the rail.
- **Escape key listener is always active.** The `useEffect` keydown handler fires `collapse()` for every keypress regardless of sidebar state or viewport size. On desktop, pressing Escape in any context (e.g. a form field) calls `setIsExpanded(false)` unnecessarily. Guard the listener: `if (!isExpanded) return;`, or conditionally attach/detach it.
- **No focus trap or `aria-modal` on expanded overlay.** When the overlay opens, focus is not moved into it and keyboard users can Tab through elements behind the backdrop. The overlay div should have `role="dialog"` and `aria-modal="true"`, focus should be moved to the first focusable element on open, and Tab/Shift-Tab should be trapped within it.
- **No body scroll lock.** Background content remains scrollable through the backdrop while the overlay is open.

SUGGESTIONS:
- No test verifies the desktop sidebar (`data-testid="sidebar-desktop"`). Given the conditional rendering split, a test confirming the desktop section renders as expected would prevent regressions.
- `user.email?.[0].toUpperCase()` will throw a `TypeError` if `user.email` is an empty string (the optional-chain only guards against `null`/`undefined`). Change to `user.email?.[0]?.toUpperCase()` in both the mobile overlay and desktop sections.
- The `‹` / `›` Unicode characters used as chevrons have no inherent semantic width or weight guarantee across fonts. The `aria-label` covers accessibility, but consider an SVG icon for visual consistency if the rest of the codebase uses an icon library.
- The component is now 197 lines with three distinct render trees. Consider splitting into `MobileRail`, `MobileOverlay`, and `DesktopSidebar` sub-components to improve readability.

SUMMARY: Two blockers must be fixed before approval: the overlay slide animation does not work (conditional rendering bypasses the CSS transition), and the unconditional `pl-14` in layout.tsx breaks the mobile layout of unauthenticated pages where the rail is not rendered.

---json
{
  "status": "NEEDS_CHANGES",
  "critical": [
    "Slide animation is broken — conditional rendering means the overlay appears instantly at translate-x-0 with no transition; the element must be in the DOM during both states for CSS transition to fire.",
    "pl-14 in layout.tsx is unconditional, adding 56px left padding to login/signup pages on mobile where no rail exists, breaking their centered layout."
  ],
  "warnings": [
    "Overlay content obscured by rail: both are z-30 at left-0, rail paints over the leftmost 56px of the expanded overlay, hiding icons and nav text.",
    "Escape key listener always active regardless of sidebar state or viewport; should guard with isExpanded check.",
    "Expanded overlay lacks role=dialog, aria-modal, focus trapping, and body scroll lock."
  ],
  "summary": "Two blockers must be fixed: the slide animation does not work due to conditional rendering, and the unconditional pl-14 in layout.tsx breaks mobile layout on unauthenticated pages."
}
---

## Review cycle 2 — 2026-07-05

STATUS: APPROVED

CRITICAL:
- none

WARNINGS:
- **Overlay is focusable while off-screen (new regression from always-in-DOM approach).** The overlay div now permanently lives in the DOM and is hidden only via `-translate-x-full` and `aria-hidden="true"`. However, `aria-hidden` hides elements from the accessibility tree but does not remove them from the keyboard tab order. The four nav `<Link>` elements and the Sign Out `<button>` inside the collapsed overlay are still reachable via Tab. A keyboard user will silently tab into an off-screen sidebar. Fix: add `inert` on the overlay div when `!isExpanded` (e.g. `inert={!isExpanded || undefined}`). This is a keyboard-accessibility regression introduced by the correct fix for the animation bug.
- **No focus trap or `aria-modal` when expanded (carried forward from cycle 1).** When the overlay opens, focus is not moved into it and there is no trap — users can Tab into the main content behind the backdrop. Add `role="dialog"`, `aria-modal="true"`, move focus to the first focusable child on open, and trap Tab/Shift-Tab within the overlay.
- **No body scroll lock (carried forward from cycle 1).** Background content remains scrollable through the backdrop while the overlay is open.

SUGGESTIONS:
- The backdrop (`z-20`) is lower than the rail (`z-30`), so the icon rail remains visually above the semi-transparent backdrop when the overlay is expanded. This may be intentional (rail icons can still be clicked to navigate), but it is inconsistent with a conventional modal-drawer pattern where the scrim covers everything except the drawer. Worth an explicit design decision.
- Still no test for `data-testid="sidebar-desktop"` (carried forward from cycle 1).
- The `‹`/`›` Unicode chevrons and the component size (209 lines, three render trees) are unchanged from cycle 1 suggestions.

SUMMARY: Both critical issues are resolved and all 37 tests pass; the only issue requiring attention before shipping to production is that the always-in-DOM overlay leaves its interactive children in the tab order when collapsed — add `inert` on the overlay when `!isExpanded`.

---json
{
  "status": "APPROVED",
  "critical": [],
  "warnings": [
    "Overlay links and button are still keyboard-reachable when the sidebar is collapsed (aria-hidden does not remove from tab order); add inert={!isExpanded || undefined} to the overlay div.",
    "No focus trap or aria-modal when expanded — carried forward from cycle 1.",
    "No body scroll lock — carried forward from cycle 1."
  ],
  "summary": "Both critical fixes land correctly and all 37 tests pass; add inert on the collapsed overlay before shipping to avoid keyboard users tabbing into the offscreen sidebar."
}
---
