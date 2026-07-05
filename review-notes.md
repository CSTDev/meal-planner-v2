## Review cycle 1 — 2026-07-05

STATUS: NEEDS_CHANGES

CRITICAL:
- `/services/menu-planner-ui/app/api/meal-plans/route.ts` (POST — create meal plan) still uses `throw new Error(\`Backend returned ${response.status}\`)` inside the `!response.ok` block (line 37). The throw is caught by the outer `catch` and always returned as 500, meaning all backend 4xx responses (e.g. 400 Bad Request, 404 Not Found, 429 Rate Limited) are silently collapsed to 500. This is exactly the bug that was fixed in `recommendations/route.ts` and `feedback/route.ts` in commit c898b9b, but the `meal-plans` POST route was not updated.

WARNINGS:
- `console.error('Backend error:', errorText)` logs the full raw backend response body to the UI server's stdout/stderr. If the Java gateway returns internal error details (SQL error messages, stack traces, internal service paths), they land unredacted in production server logs. The same pattern already existed in `recipe-search/route.ts` and `shopping-list/route.ts` before this change; `feedback/route.ts` now joins them. Consider capping or sanitising before logging (e.g. `errorText.slice(0, 200)`), or logging only a structured summary.
- The "fewer than 2 characters" test in `RecipeSelector.test.tsx` advances fake timers by 500ms (line 76), but the component's debounce is 300ms (`RecipeSelector.tsx` line 41). The mismatch is harmless since 500 > 300, but the unexplained magic number will cause confusion if the debounce delay is ever changed — the test would silently continue passing without covering the actual debounce window. Use 300ms or a shared constant.

SUGGESTIONS:
- `act(() => { jest.advanceTimersByTime(500); })` on line 76 is not awaited inside the `async` test. It is safe here because the length guard (`searchQuery.length >= 2`) short-circuits before any async work or state updates occur, but the idiomatic React 18 / Testing Library form is `await act(async () => { ... })` to flush all pending microtasks and avoid potential "not wrapped in act" warnings if the component internals change.
- The first test ("typing >= 2 characters triggers search") uses `userEvent.setup()` with real timers and relies on `waitFor`'s default 1000ms timeout to catch the 300ms debounced API call. This is workable but could be flaky under load. A comment explaining why real timers are acceptable here, or switching to fake timers for consistency with the other test, would make the suite easier to reason about.

SUMMARY: The `meal-plans` POST route at `/app/api/meal-plans/route.ts` was missed in the status-code propagation fix — it still throws inside `!response.ok` and returns 500 for all backend errors; apply the same `return NextResponse.json(...)` pattern used in the other two routes.

---json
{
  "status": "NEEDS_CHANGES",
  "critical": [
    "/services/menu-planner-ui/app/api/meal-plans/route.ts POST handler still throws inside !response.ok, collapsing all backend 4xx errors into 500 — the same bug fixed in recommendations and feedback routes was not applied here"
  ],
  "warnings": [
    "console.error logs full raw backend response body unredacted; if the Java gateway leaks internal error details they land in production UI-server logs",
    "RecipeSelector.test.tsx fake-timer test advances by 500ms while the component debounce is 300ms — unexplained magic number will mislead future maintainers if the debounce changes"
  ],
  "summary": "The meal-plans POST route was not updated alongside recommendations and feedback — it still swallows backend 4xx status codes as 500 and must be fixed with the same return NextResponse.json pattern."
}
---
