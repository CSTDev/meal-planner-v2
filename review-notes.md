# Review Notes

## Review cycle 1 — 2026-09-28

STATUS: NEEDS_CHANGES

CRITICAL:
- The `V6__add_unique_url_per_user.sql` migration (`ALTER TABLE recipes ADD CONSTRAINT recipes_url_user_unique UNIQUE (url, scraped_by_user_id)`) will fail outright if any pre-existing recipes already share the same `(url, scraped_by_user_id)` pair — which is entirely plausible, since deduplication has never existed until now and a user could easily have scraped the same URL twice already. Postgres refuses to add a unique constraint over data that violates it, so on any environment with existing duplicates the Flyway migration aborts and the app fails to boot (Flyway migrations run at startup and a failed migration blocks the whole deploy, not just this feature). Neither the migration nor the task doc account for this — only the `url IS NULL` case is addressed ("No backfill" section). Before merging: query prod for existing duplicate `(url, scraped_by_user_id)` pairs and either add a companion cleanup migration (e.g. delete/merge older duplicates, keeping the earliest `scraped_at`) or confirm none exist and document that assumption.

WARNINGS:
- No test exercises the actual production call path for the race-condition safety net. `RecipeService.addRecipe()` is only ever invoked from `CreateRecipeMessageReceiver.receiveRecipe()`, which is itself `@Transactional` (plain, no `dontRollbackOn`) — so in production, `addRecipe()`'s `@Transactional(dontRollbackOn = PersistenceException.class)` runs as a *joined* (non-owning) transaction, not as the outermost boundary. The new `RecipeServiceTest` only calls `addRecipe()` directly from a plain test method (no ambient transaction), making `addRecipe()` the transaction owner there — a materially different scenario from the nested case in prod. The existing `CreateRecipeMessageReceiverTest` mocks `RecipeService` (`@InjectMock`), so it never exercises real persistence at all. Recommend adding (or extending) an integration test that sends two `RecipeScrapeCompleted` events with the same URL/user through the real (non-mocked) Kafka→`CreateRecipeMessageReceiver`→`RecipeService` path and asserts: no exception/500, the message is acknowledged (not redelivered), and exactly one `Recipe` row persists. This is exactly acceptance criterion 3 ("Kafka message is acked, no 500 or retry loop") and it currently has no direct coverage. (Note: I could not find evidence this actually breaks — the closest analogous test, `RecipeServiceTest`, does pass — but the nested-transaction path it doesn't cover is precisely the one described as the target scenario in the task doc.)
- `isUniqueConstraintViolation` is matched purely by exception type (`PersistenceException`), and `@Transactional(dontRollbackOn = PersistenceException.class)` is applied at the class-exception-type level, not scoped to "is a unique violation." Today this is harmless because the only caller (`CreateRecipeMessageReceiver.receiveRecipe()`) has no `dontRollbackOn` of its own, so a genuinely different (non-duplicate) `PersistenceException` rethrown by `addRecipe()` would still be correctly marked for rollback by the outer interceptor when it escapes `receiveRecipe()`. But this correctness currently depends entirely on that specific call graph; if `addRecipe()` is ever invoked as a standalone top-level transaction elsewhere (as the isolated unit test already does), a genuine, unrelated `PersistenceException` (e.g. a different constraint violation or transient DB error) would be rethrown to the caller but the transaction would *not* be marked for rollback, since `dontRollbackOn` can't distinguish "the swallowed duplicate case" from "the rethrown other case" by type alone. Worth a comment noting this fragility, or narrowing the risk (e.g. asserting in `isUniqueConstraintViolation`'s caller that only the swallowed path benefits, and treating `addRecipe()` as never a valid tx-owning entry point).
- The `LOGGER.info("Duplicate recipe URL, ignoring")` message omits the URL and user id, which would make it hard to correlate/debug in production logs when duplicates are actually swallowed. Consider `LOGGER.info(() -> "Duplicate recipe URL, ignoring: url=" + recipe.url + " userId=" + userId)`.

SUGGESTIONS:
- The `recipes_url_user_unique` constraint is now declared in two places (the Flyway migration for prod, and `@Table(uniqueConstraints=...)` on the `Recipe` entity for dev/test schema generation) with a good explanatory comment on why — but this is a manual-sync point that could silently drift if either is changed later without the other. Consider a follow-up note/TODO or a test that fails if they diverge (e.g. asserting the constraint exists after `drop-and-create` in a `@QuarkusTest`), though the existing `RecipeRepositoryTest.uniqueConstraintRejectsADuplicateUrlForTheSameUser` test already indirectly covers the dev/test side.
- `ScrapeResource.ScrapeRecipe` now does two things that both depend on `url.url()` being non-null (the pre-check query and the emitted event) — this is pre-existing behavior (no `@NotBlank`/validation on `ScrapeRequest.url`), not introduced by this change, but since this PR is touching duplicate-detection logic anyway it would have been a low-cost place to add basic input validation. Not blocking.
- Migration correctly picked `V6` (next real slot, since `V4`/`V5` were already taken by unrelated migrations) rather than the `V4` suggested in the stale task doc — good catch, no action needed, just noting it as intentional deviation from the plan for whoever reviews against the task doc.

SUMMARY: Before merging, verify (or migrate away) any pre-existing duplicate (url, scraped_by_user_id) rows so the new unique-constraint migration doesn't fail on deploy, and add a test that exercises the real nested-transaction path (Kafka → CreateRecipeMessageReceiver → RecipeService.addRecipe, not mocked) to confirm the constraint-violation swallow behaves as intended in production, not just when addRecipe() is called as a standalone transaction.

---json
{
  "status": "NEEDS_CHANGES",
  "critical": [
    "V6__add_unique_url_per_user.sql will fail to apply (and block app startup) if any pre-existing recipes already share the same (url, scraped_by_user_id) pair; no check or cleanup migration is provided for this case."
  ],
  "warnings": [
    "No test exercises addRecipe() being invoked from within CreateRecipeMessageReceiver's own @Transactional method (the real production call path) — RecipeServiceTest calls addRecipe() as a standalone top-level transaction, and CreateRecipeMessageReceiverTest mocks RecipeService entirely, so the nested-transaction dontRollbackOn behavior described in the task doc is untested.",
    "dontRollbackOn = PersistenceException.class is type-based and can't distinguish the swallowed duplicate case from a rethrown unrelated PersistenceException; correctness currently relies on addRecipe() always being called as a joined (non-owning) transaction under CreateRecipeMessageReceiver.",
    "The swallowed-duplicate log message omits the URL and user id, hurting debuggability."
  ],
  "summary": "Verify/handle pre-existing duplicate (url, scraped_by_user_id) rows before the new unique constraint migration ships, and add a test covering the real Kafka-consumer nested-transaction path for the constraint-violation swallow."
}
---

## Review cycle 2 — 2026-09-28

STATUS: NEEDS_CHANGES

CRITICAL:
- The new cleanup step in `V6__add_unique_url_per_user.sql` (the `DELETE FROM recipes WHERE id IN (... ROW_NUMBER() ...)` added in 74244f7) does not actually make the migration deploy-safe: it will itself fail with a foreign-key violation if any of the non-earliest duplicate rows it tries to delete are referenced by `meal_plan_recipes.recipe_id` or `user_recipe_interactions.recipe_id` (both `REFERENCES recipes(id)` with no `ON DELETE` clause in `V2__add_remaining_tables.sql`/`V4__add_meal_plan_recipes.sql`, so they default to `NO ACTION`/`RESTRICT`). I reproduced this directly against the live dev Postgres instance (host.docker.internal:54322) in a rolled-back transaction: seeding two recipes with the same `(url, scraped_by_user_id)` and pointing a `meal_plan_recipes` row (or, separately, a `user_recipe_interactions` row) at the *later* duplicate, then running the exact DELETE from the migration, produces:
  - `ERROR: update or delete on table "recipes" violates foreign key constraint "meal_plan_recipes_recipe_id_fkey" ... DETAIL: Key (id)=(...) is still referenced from table "meal_plan_recipes".`
  - `ERROR: update or delete on table "recipes" violates foreign key constraint "user_recipe_interactions_recipe_id_fkey" ... DETAIL: Key (id)=(...) is still referenced from table "user_recipe_interactions".`
  This is entirely plausible in production: a user scraping the same URL twice before this feature existed could easily have accepted/rejected/viewed the second (later) duplicate into a meal plan, which is exactly the kind of pre-existing duplicate this migration is meant to clean up. The net effect is the same failure mode cycle 1 originally flagged — Flyway migration aborts, app fails to boot at deploy — just moved from the `ALTER TABLE` to the new `DELETE`. The new `AddUniqueUrlPerUserMigrationTest` doesn't catch this because it only ever inserts rows into `recipes` in isolation and never seeds a `meal_plan_recipes`/`user_recipe_interactions` row referencing one of the duplicates, so the FK-violation path has zero coverage. Before merging: either re-point/delete the referencing rows for the duplicate being removed (e.g. re-point `meal_plan_recipes`/`user_recipe_interactions` rows from the deleted duplicate id to the surviving "earliest" id before the `DELETE`, or delete them too if that's an acceptable loss), or otherwise decide how referenced duplicates should be resolved, and add a migration test that seeds a referencing row to prove it.

WARNINGS:
- (Carried over from cycle 1, not addressed this round and not required to be — noting for completeness) `isUniqueConstraintViolation` matching is still purely exception-type-based and `@Transactional(dontRollbackOn = PersistenceException.class)` is still applied at the class-exception-type level on `addRecipe()`. This is unchanged from cycle 1 and remains a latent fragility if `addRecipe()` is ever called as a standalone top-level transaction elsewhere, though it's out of scope for this cycle's fixes.

SUGGESTIONS:
- The tie-break `ORDER BY createdat ASC, id ASC` falls back entirely to `id` ordering (arbitrary, since `id` is a random UUID) when `createdat` is NULL for all rows in a duplicate group — `createdAt` has no `NOT NULL` constraint in `V1__initial_schema.sql`, so old/imported rows could plausibly have a null value. The result is still deterministic and safe (some single row is always kept), just not necessarily "the actual earliest" in that edge case. Not blocking, but worth a one-line comment or a test case, since the rest of the migration's comment explicitly claims "keep the earliest."
- `AddUniqueUrlPerUserMigrationTest.runV6Migration()` splits the loaded SQL file on `;` to execute each statement individually — reasonable for the current file's contents (verified, no stray semicolons in the comments), but fragile if a future edit to `V6__...sql` adds a semicolon inside a comment or string literal. Not a real risk today given the file's simplicity.

VERIFICATION PERFORMED: Compiled the module (`mvn -o compile`, `mvn -o test-compile`) successfully. Ran the new `AddUniqueUrlPerUserMigrationTest` (3/3 pass) and `CreateRecipeMessageReceiverDuplicateHandlingTest` (1/1 pass) against the live dev Postgres/Kafka(Redpanda testcontainer) instance — both green, and the log output confirms the swallowed-duplicate message now includes `url=` and `userId=` as required. Also re-ran `RecipeServiceTest`, `RecipeRepositoryTest`, `CreateRecipeMessageReceiverTest`, and `ScrapeResourceTest` to confirm no regressions (all green). Separately reproduced the FK-violation gap described above directly against the same live DB in a rolled-back transaction (see CRITICAL).

SUMMARY: The cycle-1 CRITICAL is only partially fixed — the new DELETE-before-ALTER cleanup in V6 correctly handles plain duplicate recipes but will itself abort the migration with a foreign-key violation if a duplicate being removed is referenced by meal_plan_recipes or user_recipe_interactions, which is a realistic pre-existing-data scenario; both cycle-1 WARNINGs (nested-transaction test coverage, log message context) are genuinely and verifiably fixed.

---json
{
  "status": "NEEDS_CHANGES",
  "critical": [
    "V6__add_unique_url_per_user.sql's new cleanup DELETE (added to fix cycle 1's critical finding) will itself fail with a foreign-key violation and abort the migration if a duplicate recipe row being deleted is referenced by meal_plan_recipes.recipe_id or user_recipe_interactions.recipe_id (both REFERENCES recipes(id) with no ON DELETE action) -- reproduced directly against the live dev DB. No test seeds a referencing row, so this gap is uncovered. Deploy-time migration failure is still possible on realistic pre-existing data."
  ],
  "warnings": [
    "Carried over, not required this cycle: isUniqueConstraintViolation/dontRollbackOn is still exception-type-based rather than scoped to the specific duplicate case."
  ],
  "summary": "The DELETE-before-ALTER cleanup added this cycle does not fully resolve cycle 1's critical migration-failure risk: it will itself abort on foreign-key violations if a duplicate row being removed is referenced from meal_plan_recipes or user_recipe_interactions (reproduced against the live DB); the two in-scope WARNINGs (nested-transaction test, log message url/userId) are correctly and verifiably fixed."
}
---
