package uk.co.cstdev.data;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class FeedbackRepository implements PanacheRepository<UserRecipeInteraction> {

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
