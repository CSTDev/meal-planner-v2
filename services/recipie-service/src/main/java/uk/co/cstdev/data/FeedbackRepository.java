package uk.co.cstdev.data;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class FeedbackRepository implements PanacheRepository<UserRecipeInteraction> {

    // "Currently accepted in this plan" reads (past plans, shopping list) no
    // longer derive latest-interaction-wins state from this log — they read
    // the live meal_plan_recipes table directly instead. This repository
    // now serves only as an append-only history for recommendation
    // exclusion (see RecipeRepository's cross-plan 90-day window and
    // permanent per-plan accept/reject exclusion).

    @Transactional
    public void saveFeedback(UUID userId, UUID recipeId, UUID mealPlanId, FeedbackAction interactionType) {
        getEntityManager().createNativeQuery("""
                INSERT INTO user_recipe_interactions
                    (id, user_id, recipe_id, meal_plan_id, interaction_type, interaction_at)
                VALUES
                    (gen_random_uuid(), :userId, :recipeId, :mealPlanId, :interactionType, clock_timestamp())
                ON CONFLICT (user_id, recipe_id, meal_plan_id, interaction_type)
                DO UPDATE SET interaction_at = clock_timestamp()
                """)
                .setParameter("userId", userId)
                .setParameter("recipeId", recipeId)
                .setParameter("mealPlanId", mealPlanId)
                .setParameter("interactionType", interactionType.name())
                .executeUpdate();
    }
}
