-- Users Table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Meal Plans Table
CREATE TABLE meal_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    recipe_source VARCHAR(20) DEFAULT 'own', -- 'own', 'all', 'shared'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'active'
);

-- User Recipe Interactions Table
CREATE TABLE user_recipe_interactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    recipe_id UUID REFERENCES recipes(id),
    meal_plan_id UUID REFERENCES meal_plans(id),
    interaction_type VARCHAR(20) NOT NULL, -- 'ACCEPTED', 'REJECTED', 'VIEWED'
    interaction_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, recipe_id, meal_plan_id, interaction_type)
);

-- Indexes for performance
CREATE INDEX idx_interactions_user_recipe ON user_recipe_interactions(user_id, recipe_id);
CREATE INDEX idx_interactions_meal_plan ON user_recipe_interactions(meal_plan_id);
