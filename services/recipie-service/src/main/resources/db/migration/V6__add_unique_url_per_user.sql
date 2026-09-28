-- Nothing has prevented duplicate (url, scraped_by_user_id) pairs until now,
-- so pre-existing recipes may already violate the constraint added below.
-- Postgres refuses to add a unique constraint over data that violates it, so
-- clean up existing duplicates first: for each (url, scraped_by_user_id)
-- group with more than one row, keep the earliest (by createdat, then id as
-- a deterministic tiebreaker) and delete the rest.
--
-- Rows with a null url are intentionally excluded — they are allowed to
-- repeat and are out of scope. Rows with a null scraped_by_user_id are also
-- excluded: standard SQL unique-constraint semantics never treat two nulls
-- as equal, so such rows could never violate the constraint being added and
-- must not be touched.
--
-- A duplicate row being removed may already be referenced by
-- meal_plan_recipes.recipe_id and/or user_recipe_interactions.recipe_id
-- (both REFERENCES recipes(id) with no ON DELETE action) — e.g. a user
-- could have scraped the same URL twice before this feature existed and
-- had the later duplicate offered/accepted into a meal plan, or recorded an
-- interaction against it. Deleting such a row outright would violate the
-- foreign key and abort the migration, so referencing rows are re-pointed
-- to the surviving (earliest) recipe id first.
--
-- Re-pointing itself can collide with a row that already represents the
-- survivor for the same plan/interaction (meal_plan_recipes' composite
-- primary key is (meal_plan_id, recipe_id) and user_recipe_interactions'
-- unique constraint is (user_id, recipe_id, meal_plan_id, interaction_type))
-- — including when two different duplicate rows both got referenced from
-- the same plan/interaction group before this migration ran. In that case
-- one referencing row is dropped (keeping the one with the lowest id as a
-- deterministic tiebreaker) rather than updated, since after re-pointing it
-- would represent exactly the same plan/interaction as the row kept.
DROP TABLE IF EXISTS recipe_url_duplicates;
CREATE TEMP TABLE recipe_url_duplicates AS
SELECT id,
       FIRST_VALUE(id) OVER (
           PARTITION BY url, scraped_by_user_id
           ORDER BY createdat ASC, id ASC
       ) AS survivor_id
FROM recipes
WHERE url IS NOT NULL AND scraped_by_user_id IS NOT NULL;

-- meal_plan_recipes: drop the redundant side of any (meal_plan_id, id after
-- re-pointing) collision, keeping the row with the lowest recipe_id.
DELETE FROM meal_plan_recipes mpr
WHERE EXISTS (
    SELECT 1
    FROM meal_plan_recipes other
    LEFT JOIN recipe_url_duplicates other_dup ON other_dup.id = other.recipe_id
    LEFT JOIN recipe_url_duplicates mpr_dup ON mpr_dup.id = mpr.recipe_id
    WHERE other.meal_plan_id = mpr.meal_plan_id
      AND COALESCE(other_dup.survivor_id, other.recipe_id) = COALESCE(mpr_dup.survivor_id, mpr.recipe_id)
      AND other.recipe_id < mpr.recipe_id
);

UPDATE meal_plan_recipes mpr
SET recipe_id = d.survivor_id
FROM recipe_url_duplicates d
WHERE mpr.recipe_id = d.id AND d.id <> d.survivor_id;

-- user_recipe_interactions: same idea, scoped to the unique constraint's
-- columns, keeping the row with the lowest id.
DELETE FROM user_recipe_interactions uri
WHERE EXISTS (
    SELECT 1
    FROM user_recipe_interactions other
    LEFT JOIN recipe_url_duplicates other_dup ON other_dup.id = other.recipe_id
    LEFT JOIN recipe_url_duplicates uri_dup ON uri_dup.id = uri.recipe_id
    WHERE other.user_id = uri.user_id
      AND other.meal_plan_id = uri.meal_plan_id
      AND other.interaction_type = uri.interaction_type
      AND COALESCE(other_dup.survivor_id, other.recipe_id) = COALESCE(uri_dup.survivor_id, uri.recipe_id)
      AND other.id < uri.id
);

UPDATE user_recipe_interactions uri
SET recipe_id = d.survivor_id
FROM recipe_url_duplicates d
WHERE uri.recipe_id = d.id AND d.id <> d.survivor_id;

DELETE FROM recipes r
USING recipe_url_duplicates d
WHERE r.id = d.id AND d.id <> d.survivor_id;

DROP TABLE recipe_url_duplicates;

-- Prevents the same user from scraping the same URL twice at the database
-- level: a safety net for the race condition where two identical scrape
-- requests are persisted concurrently, slipping past the application-level
-- pre-check in ScrapeResource. Standard SQL null semantics mean multiple
-- rows with a null url are still allowed, so pre-existing recipes without a
-- url are unaffected.
ALTER TABLE recipes ADD CONSTRAINT recipes_url_user_unique UNIQUE (url, scraped_by_user_id);
