# Review Notes — meal-planner-v2

## Review cycle 1 — 2026-07-09

Branch: `claude/menu-planner-ready-for-agent-d7f4fi` (6 commits vs `origin/main`)
Task: /home/user/second-brain/projects/menu-planner/tasks/view-menu-history.md ("View previously created menu plans")

Verified locally: frontend Jest suites for `past-plans`, `past-plans/[id]`, and `Sidebar` all pass (33/33). Backend `compile` and `test-compile` succeed offline; the Quarkus integration tests could NOT be executed in this environment (no Docker for the Postgres devservice — `clock_timestamp()`/`ON CONFLICT` need real Postgres), so backend test results are reviewed-by-reading only.

STATUS: NEEDS_CHANGES

CRITICAL:
- **Broken print flow on the new detail page** (`services/menu-planner-ui/app/past-plans/[id]/page.tsx`). The shopping-list overlay was copied from `MealPlanView.tsx` including the print button (`onClick={() => window.print()}`), but *not* the `useEffect` that toggles `document.body.classList.add('shopping-list-open')` (MealPlanView.tsx lines 33–39). All the `@media print` rules in `app/globals.css` that make printing work are gated on `body.shopping-list-open` — in particular the ones that unclip `.app-shell` (`h-screen`) and `.app-main` (`overflow-y-auto`) so the list can flow past one page. Additionally, the content-hiding rules only target `.meal-plan-page-header` / `.meal-plan-main-header` / `.recipe-grid`, none of which exist on this page. Net effect: pressing the print button on a past plan prints the page's own heading/recipe list mixed in with the shopping list, and the output can be clipped to a single viewport height. Given the app's stated priority is the shopping list, this is a shipped-broken control. Fix by (a) replicating the body-class effect and giving the page header/recipe list print-hideable classes, (b) extracting the overlay (see warning below) so the working behaviour comes for free, or (c) removing the print button from this page.

WARNINGS:
- **~50 lines of overlay chrome duplicated** between `MealPlanView.tsx` (lines 212+) and `past-plans/[id]/page.tsx` (backdrop, pane, print/close buttons, loading/error states). The spec said to reuse the `<ShoppingList>` overlay UX; only the inner `<ShoppingList>` component was reused. This duplication is exactly what caused the critical print bug — the copy drifted from the original. Extract a shared `ShoppingListOverlay` component and use it in both places.
- **N+1 / unbounded scan in `MealPlanService.getRecentMealPlans`** (`services/recipie-service/.../service/MealPlanService.java`): it loads *all* of the user's plans (`MealPlan.list("userId = ?1 ORDER BY createdAt DESC")`), then runs one `findAcceptedInteractions` query per plan until 10 qualify. If a user has many empty/rejected plans (empty plans are created every time someone opens the generator and bounces), every plan they've ever created is queried on each page load. Acceptable for a personal app today, but a single grouped latest-wins query (or at least a batched/limited scan) would be the right shape. Worth a follow-up if not fixed now.
- Backend integration tests could not be executed in this review environment (no Docker). They read correctly and compile, but a green run in CI should be confirmed before merge, especially `ShoppingListResourceTest`'s ghost-bug tests after the `ShoppingListService` refactor.

SUGGESTIONS:
- `getAcceptedRecipes` in `MealPlanResource` does a per-interaction `Recipe.findById` (N+1) and silently drops missing recipes via `filter(Objects::nonNull)`. Fine at plan sizes, but a single `id IN (:ids)` query would be cleaner; if a referenced recipe is ever missing, consider logging it rather than dropping silently.
- `getRecentMealPlans` orders only by `createdAt DESC`; plans created in the same instant have nondeterministic relative order. Add a secondary sort key (e.g. `id`) for determinism.
- `MealPlanSummaryResponse.createdAt` is `java.util.Date`. It serializes to ISO-8601 under Quarkus defaults (which the frontend's `new Date(createdAt)` relies on), but new DTOs would be better with `Instant`, and there is no test pinning the serialized date format.
- `PastMealPlan.recipeSource` is fetched and typed in `lib/api/mealPlans.ts` but never displayed — intentional per the spec's display rules, just noting it's currently dead weight on the UI side.

What checked out cleanly:
- `FeedbackRepository.findAcceptedInteractions` is a faithful extraction of the previous inline JPQL in `ShoppingListService` (same query text, same parameters); `buildShoppingList` behaviour is unchanged and the ghost-bug tests in `ShoppingListResourceTest` are untouched by the diff.
- No naive `interaction_type = 'ACCEPTED'` counting anywhere in the new code — both the list count and the accepted-recipes endpoint go through the shared method. (The `interaction_type = :acceptedType` filters in `RecipeRepository` are pre-existing recommendation-exclusion queries, out of scope and untouched.)
- List endpoint: cap of 10, newest first, zero-accepted plans excluded (including the accept-then-reject case), other users' plans excluded — all implemented and all covered by tests, including the exact regression case the spec calls out (`testListMealPlansAcceptedRecipeCountUsesLatestInteraction` covers accept→reject NOT counted and reject→accept counted).
- `/accepted-recipes`: 404 for unknown/malformed id, 403 for another user's plan — matches the existing `searchRecipes`/`getShoppingList` pattern verbatim; explicit `interactionAt` descending sort with a comment noting the query has no inherent order; ordering is safe against timestamp ties in tests because `saveFeedback` uses Postgres `clock_timestamp()` (microsecond precision). At most one ACCEPTED row per (user, recipe, plan) exists due to the upsert key, so no duplicate recipes.
- Frontend: proxy routes follow the existing auth/forwarding pattern and pass backend status codes through; sidebar entry added to the single `navigation` array as specified; empty-state text matches the spec exactly ("You haven't created any meal plans with recipes yet."); both views are read-only with tests asserting no accept/reject controls; detail page renders recipes in API order (test-asserted) and disables the generate button for zero recipes.
- Testing Decisions section: every listed backend and frontend test exists.

SUMMARY: Wire up (or remove) the print button on the past-plans detail overlay — it currently prints the page content and can clip the list because `body.shopping-list-open` is never set; extracting the duplicated overlay into a shared component is the recommended fix.

---json
{
  "status": "NEEDS_CHANGES",
  "critical": [
    "Print button in the past-plans detail shopping-list overlay is broken: the page never toggles body.shopping-list-open, so the @media print rules in globals.css don't apply — printed output includes the page's own heading/recipe list and can be clipped to one viewport height by .app-shell/.app-main. Fix the body-class wiring + hideable classes, extract a shared overlay component, or remove the button."
  ],
  "warnings": [
    "~50 lines of shopping-list overlay chrome duplicated from MealPlanView.tsx into past-plans/[id]/page.tsx — extract a shared ShoppingListOverlay component (this drift caused the critical bug)",
    "MealPlanService.getRecentMealPlans loads all of the user's plans and runs one interactions query per plan (N+1, unbounded when few plans qualify) — acceptable now, should become a single grouped query",
    "Backend Quarkus tests not executed in review environment (no Docker) — confirm green CI run, especially ShoppingListResourceTest after the extraction"
  ],
  "summary": "Latest-interaction-wins extraction, both endpoints, and test coverage are all correct per spec; the one blocker is the copied-but-unwired print button in the new detail page's shopping-list overlay."
}
---

## Review cycle 2 — 2026-07-09

Branch: `claude/menu-planner-ready-for-agent-d7f4fi` (now 10 commits vs `origin/main`; fix iteration = last 4: `0b456f1`, `f11dd21`, `36ff03b`, `e1eae03`)

Verified locally: full frontend Jest suite passes (9 suites, 70/70 tests, including the new `ShoppingListOverlay.test.tsx` and the three new print-flow tests in `past-plans/[id]/__tests__/page.test.tsx`). Backend `compile` + `test-compile` succeed. Quarkus integration tests still cannot run in this environment (no Docker) — same caveat as cycle 1.

STATUS: APPROVED

CRITICAL:
- none

Cycle 1 critical — verified fixed:
- `app/components/ShoppingListOverlay.tsx` now owns the `body.shopping-list-open` toggle (`useEffect` with cleanup on unmount) plus the full overlay chrome, and is used by BOTH `MealPlanView.tsx` and `past-plans/[id]/page.tsx`. This resolves the critical and the duplication warning in one move, as recommended.
- Past-plans detail page: all page content (back link, "Past Plan" header, generate button, loading/error/empty states, recipe list) is wrapped in a new `.shopping-list-print-hide` div; the overlay is rendered outside it. `globals.css` adds `body.shopping-list-open .shopping-list-print-hide` to the existing hide rule (inside the `@media print` block, correctly placed), with an explanatory comment. So with the overlay open: page content hidden, `.app-shell`/`.app-main` unclipped, sidebar hidden — the printed output is the shopping list alone and can span pages.
- MealPlanView did NOT regress: `.meal-plan-main-header` (line 137) and `.recipe-grid` (line 170) are still on its markup, `.meal-plan-page-header` is still on `app/meal-plan/page.tsx` line 22, and `.app-shell`/`.app-main`/`.app-sidebar` are unchanged in `layout.tsx`/`Sidebar.tsx` — every selector in the print CSS still has a matching element. The overlay's own print classes (`shopping-list-overlay`, `shopping-list-backdrop`, `shopping-list-pane`, `shopping-list-controls`) moved verbatim into the shared component. `MealPlanView.test.tsx` was untouched by the branch and still passes (print button integration included).
- Test coverage for the fix is good: `ShoppingListOverlay.test.tsx` covers body-class add/remove/unmount-cleanup, print button → `window.print`, close/backdrop → `onClose`, loading/error/closed states; the detail-page tests assert body-class toggling through the real open/close flow, `window.print`, and — nicely — that the `.shopping-list-print-hide` block contains the header and recipe list but NOT the overlay (guards against the exact regression class that caused cycle 1's critical).

Cycle 1 non-criticals addressed this iteration:
- `getAcceptedRecipes` now batches the recipe lookup (`Recipe.list("id in ?1", ids)` → map by id, guarded for the empty-ids case) and logs a warning instead of silently dropping a missing recipe. Interaction order is preserved by iterating interactions and looking up the map, so the most-recently-accepted-first ordering is intact. Duplicate-key risk in `Collectors.toMap` is nil (recipe ids are primary keys; at most one ACCEPTED interaction per recipe per plan).
- `getRecentMealPlans` now orders by `createdAt DESC, id DESC`; new test `testListMealPlansWithIdenticalCreatedAtHaveDeterministicOrder` creates three plans with an identical `createdAt` and asserts id-descending order. The test's comment about Postgres uuid byte-wise ordering matching canonical-string lexicographic order is correct (lowercase hex, fixed hyphen positions), and the sort happens DB-side so Java's `UUID.compareTo` signedness quirk doesn't apply. The full-list equality assertion is safe because `@AfterEach` deletes all plans/interactions.

WARNINGS:
- none blocking. Carried-forward items below.

Carried forward (non-critical, agreed out of scope for this task):
- **N+1 / unbounded scan in `MealPlanService.getRecentMealPlans`** — unchanged by design this iteration (explicitly deferred). Still worth a follow-up task: replace the load-all-plans + per-plan `findAcceptedInteractions` loop with a single grouped latest-interaction-wins query with `LIMIT 10`.
- **Backend integration tests not executed in this review environment** (no Docker for the Postgres devservice). The new/changed tests (`testListMealPlansWithIdenticalCreatedAtHaveDeterministicOrder`, the reworked `getAcceptedRecipes` path) read correctly and compile; confirm a green CI run before merge.

SUGGESTIONS:
- `MealPlanSummaryResponse.createdAt` is still `java.util.Date` with no test pinning the serialized format (cycle 1 suggestion, not addressed — fine to leave).
- `PastMealPlan.recipeSource` still unused on the UI side (intentional per spec display rules).

Spec compliance re-verified: all acceptance criteria in the task note are met, including the previously failing "reuses the existing shopping-list overlay" criterion — the detail view now genuinely reuses the same component with identical print behaviour.

SUMMARY: Looks good.

---json
{
  "status": "APPROVED",
  "critical": [],
  "warnings": [
    "Follow-up task (deferred by agreement): MealPlanService.getRecentMealPlans N+1 / unbounded plan scan — replace with a single grouped latest-interaction-wins query",
    "Backend Quarkus integration tests not runnable in this environment (no Docker) — confirm green CI run before merge"
  ],
  "summary": "Looks good."
}
---
