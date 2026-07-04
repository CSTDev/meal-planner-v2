## Review cycle 1 — 2026-07-04

STATUS: NEEDS_CHANGES

CRITICAL:
- none

WARNINGS:
- `RecipeRepository.findRecommendations` (lines 66 and 73 of `/home/user/meal-planner-v2/services/recipie-service/src/main/java/uk/co/cstdev/data/RecipeRepository.java`) still contains hardcoded `'ACCEPTED'` and `'REJECTED'` string literals in a native SQL query — the same anti-pattern this commit was created to eliminate. The fix is incomplete because renaming a `FeedbackAction` constant would still silently break `RecipeRepository`. Replace both literals with named parameters (e.g. `:acceptedType` / `:rejectedType`) and bind them via `.setParameter("acceptedType", FeedbackAction.ACCEPTED.name())` / `.setParameter("rejectedType", FeedbackAction.REJECTED.name())`, adding the `FeedbackAction` import to that class.

SUGGESTIONS:
- none

SUMMARY: The `ShoppingListService` change itself is correct — the import is right, the named parameters are bound before `getResultList()`, and `FeedbackAction.ACCEPTED.name()` / `FeedbackAction.REJECTED.name()` return the same strings that were hardcoded — but the same hardcoded literals remain in production code in `RecipeRepository.findRecommendations`, leaving the refactor incomplete.

---json
{
  "status": "NEEDS_CHANGES",
  "critical": [],
  "warnings": [
    "RecipeRepository.findRecommendations (lines 66 and 73) still uses hardcoded 'ACCEPTED' and 'REJECTED' literals in a native SQL query. Add FeedbackAction import and replace both literals with named parameters bound via FeedbackAction.ACCEPTED.name() / FeedbackAction.REJECTED.name()."
  ],
  "summary": "The ShoppingListService fix is correct but RecipeRepository.findRecommendations still contains the same hardcoded enum string literals the task intended to eliminate."
}
---

## Review cycle 2 — 2026-07-04

STATUS: NEEDS_CHANGES

CRITICAL:
- `MealPlanResourceTest.java` lines 356 and 415: two native SQL INSERT statements in `testGetRecommendationsExcludesMealsUsedInLast90Days` and `testOnlyInteractionsForCurrentUserAreConsidered` still embed `'ACCEPTED'` as a raw SQL string literal. This is identical to the defect pattern fixed in production — if `FeedbackAction.ACCEPTED` were ever renamed the tests would silently insert wrong data and the production exclusion logic would stop working, causing flaky test failures rather than a build error. `FeedbackAction` is already imported in this file (line 28). Fix: replace `'ACCEPTED'` with a positional placeholder `?`, shift the `interactionDate` parameter from position 4 to position 5, and insert `.setParameter(4, FeedbackAction.ACCEPTED.name())` between the `meal_plan_id` and `interaction_at` bindings. Both occurrences must be fixed.

WARNINGS:
- `V2__add_remaining_tables.sql` line 24 comment reads `-- 'accepted', 'rejected', 'viewed'` (all lowercase), but the application stores uppercase values (`ACCEPTED`, `REJECTED`). This is a stale documentation mismatch that will mislead anyone reading the schema. Update the comment to reflect the actual stored values.
- `RecipeRepository.java` lines 38, 45, and 51 (dead-code comment block, lines 22–58): contains lowercase `'accepted'`, `'rejected'` string literals. If this block were ever uncommented it would silently match zero rows, because the column stores uppercase values. Either remove the dead block entirely or update the literals to uppercase before it can be revived.

SUGGESTIONS:
- none

SUMMARY: Both production files (ShoppingListService and RecipeRepository) are now correctly parameterised, but the same hardcoded `'ACCEPTED'` literal was left in two test-layer native SQL INSERT statements in MealPlanResourceTest.java — these must be replaced with bound parameters using FeedbackAction.ACCEPTED.name() before this branch can be approved.

---json
{
  "status": "NEEDS_CHANGES",
  "critical": [
    "MealPlanResourceTest.java lines 356 and 415 still embed 'ACCEPTED' as a raw SQL string literal in native INSERT statements. Replace with positional parameter ? bound to FeedbackAction.ACCEPTED.name(), renumbering the interactionDate binding from parameter 4 to 5. Both occurrences must be fixed."
  ],
  "warnings": [
    "V2__add_remaining_tables.sql line 24 comment documents interaction_type values in lowercase ('accepted', 'rejected', 'viewed') but the application stores them uppercase (ACCEPTED, REJECTED) — update the comment.",
    "RecipeRepository.java lines 38/45/51 (commented-out block) contain lowercase 'accepted'/'rejected' literals that would silently match nothing if the block were uncommented — remove or correct before the comment misleads a future developer."
  ],
  "summary": "Both production files are now correctly parameterised, but the same hardcoded 'ACCEPTED' literal remains in two test-layer native SQL INSERT statements in MealPlanResourceTest.java and must be replaced with FeedbackAction.ACCEPTED.name() bound parameters."
}
---

## Review cycle 3 — 2026-07-04

STATUS: NEEDS_CHANGES

CRITICAL:
- `ShoppingListResourceTest.java` lines 420 and 428: The `testDoubleAcceptIsIdempotent` test constructs HTTP request bodies containing a hardcoded `"ACCEPTED"` string literal (`{"action": "ACCEPTED"}`). This file was not touched in any of the three commits under review, but the task scope explicitly requires finding all remaining hardcoded `'ACCEPTED'`/`'REJECTED'` literals across the service. These two occurrences carry the same rename-fragility risk: if `FeedbackAction.ACCEPTED` were renamed, the tests would still compile and potentially still pass (depending on how the API handles unknown enum values), masking the breakage. Fix: replace the raw `"ACCEPTED"` token with `FeedbackAction.ACCEPTED.name()` interpolated into each body string, e.g. `"{\"recipe_id\": \"" + recipe.id + "\", \"action\": \"" + FeedbackAction.ACCEPTED.name() + "\"}"`. `FeedbackAction` is already imported at line 24 of the file.

WARNINGS:
- `RecipeRepository.java` line 40: The SQL inline comment reads `-- Exclude recently shown recipes (last 30 days)` but the actual query interval directly below it is `INTERVAL '90 days'`. The RecipeRepository was substantively changed in this iteration (dead-code block removed, literals parameterised) and the stale comment was carried through unchanged. It will mislead anyone reading or tuning the query. Fix: update the comment to say `last 90 days`.

SUGGESTIONS:
- `FeedbackAction` enum defines only `ACCEPTED` and `REJECTED`, but `V2__add_remaining_tables.sql` line 24 (corrected this iteration) now documents `'VIEWED'` as a third valid `interaction_type` value. No application code produces a `VIEWED` interaction, so either add `VIEWED` to `FeedbackAction` if the feature is planned, or remove `'VIEWED'` from the schema comment to avoid implying support for a value that cannot be round-tripped through the enum.

SUMMARY: All cycle-1 and cycle-2 findings are resolved; one remaining hardcoded `"ACCEPTED"` literal survives in the test HTTP request bodies in ShoppingListResourceTest.java (lines 420 and 428) and must be replaced with FeedbackAction.ACCEPTED.name() before this branch can be approved.

---json
{
  "status": "NEEDS_CHANGES",
  "critical": [
    "ShoppingListResourceTest.java lines 420 and 428 contain hardcoded \"ACCEPTED\" string literals in HTTP request body JSON inside testDoubleAcceptIsIdempotent. Replace with FeedbackAction.ACCEPTED.name() interpolated into each body string; FeedbackAction is already imported at line 24."
  ],
  "warnings": [
    "RecipeRepository.java line 40 SQL comment says 'last 30 days' but the query uses INTERVAL '90 days'. Update the comment to match the actual interval."
  ],
  "summary": "All prior findings are resolved; one hardcoded 'ACCEPTED' string literal remains in ShoppingListResourceTest.java lines 420 and 428 and must be replaced with FeedbackAction.ACCEPTED.name() before approval."
}
---

## Review cycle 4 — 2026-07-04

STATUS: APPROVED

CRITICAL:
- none

WARNINGS:
- none

SUGGESTIONS:
- none

SUMMARY: Looks good.

---json
{
  "status": "APPROVED",
  "critical": [],
  "warnings": [],
  "summary": "Looks good."
}
---
