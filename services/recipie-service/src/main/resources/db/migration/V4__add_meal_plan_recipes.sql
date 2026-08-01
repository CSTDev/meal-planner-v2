-- Live representation of a meal plan's contents: at most one row per
-- (meal_plan, recipe) pair, enforced by the primary key so the same recipe
-- can never occupy two slots of the same plan at once.
CREATE TABLE meal_plan_recipes (
    meal_plan_id UUID NOT NULL REFERENCES meal_plans(id),
    recipe_id    UUID NOT NULL REFERENCES recipes(id),
    status       VARCHAR NOT NULL, -- 'OFFERED' | 'ACCEPTED'
    PRIMARY KEY (meal_plan_id, recipe_id)
);

CREATE INDEX idx_meal_plan_recipes_meal_plan ON meal_plan_recipes(meal_plan_id);
