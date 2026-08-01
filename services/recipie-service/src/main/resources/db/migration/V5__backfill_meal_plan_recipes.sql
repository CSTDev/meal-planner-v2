-- One-time backfill: meal_plan_recipes is only populated going forward
-- (V4 onwards); plans created before this shipped have no rows in it at
-- all. Without this, every pre-existing plan would go silently blank the
-- moment reads switch from user_recipe_interactions to meal_plan_recipes.
--
-- Mirrors FeedbackRepository.findAcceptedInteractions's latest-interaction-
-- wins derivation, run once as DML instead of per-request: a recipe only
-- counts as accepted when no REJECTED row exists that is newer than its
-- ACCEPTED row.
--
-- Known, accepted gap: only ACCEPTED recipes can be recovered this way. A
-- recipe merely offered-but-undecided on an old, still-in-progress plan at
-- deploy time has no server-side record and won't reappear. Past plans are
-- read-only in the UI, so this is fine as a one-time job.
INSERT INTO meal_plan_recipes (meal_plan_id, recipe_id, status)
SELECT i.meal_plan_id, i.recipe_id, 'ACCEPTED'
FROM user_recipe_interactions i
WHERE i.interaction_type = 'ACCEPTED'
AND NOT EXISTS (
    SELECT 1 FROM user_recipe_interactions r
    WHERE r.meal_plan_id = i.meal_plan_id
    AND r.recipe_id = i.recipe_id
    AND r.interaction_type = 'REJECTED'
    AND r.interaction_at > i.interaction_at
)
ON CONFLICT (meal_plan_id, recipe_id) DO NOTHING;
