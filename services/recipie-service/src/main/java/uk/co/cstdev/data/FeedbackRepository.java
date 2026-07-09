package uk.co.cstdev.data;

import java.util.List;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class FeedbackRepository implements PanacheRepository<UserRecipeInteraction> {

    /**
     * Returns the ACCEPTED interactions for a meal plan using
     * latest-interaction-wins semantics: because saveFeedback upserts per
     * (user, recipe, meal_plan, interaction_type), a recipe can have both an
     * ACCEPTED and a REJECTED row — a recipe only counts as accepted when no
     * REJECTED row exists that is newer than its ACCEPTED row. A naive
     * {@code interaction_type = 'ACCEPTED'} filter would wrongly include
     * recipes that were accepted and later rejected.
     * <p>
     * The result has no inherent order — callers needing an order must sort
     * explicitly.
     */
    public List<UserRecipeInteraction> findAcceptedInteractions(UUID mealPlanId, UUID userId) {
        return getEntityManager().createQuery(
                "SELECT i FROM UserRecipeInteraction i " +
                "WHERE i.mealPlanId = :mealPlanId AND i.userId = :userId AND i.interactionType = :acceptedType " +
                "AND NOT EXISTS (" +
                "  SELECT r FROM UserRecipeInteraction r " +
                "  WHERE r.mealPlanId = :mealPlanId AND r.userId = :userId AND r.recipeId = i.recipeId " +
                "  AND r.interactionType = :rejectedType AND r.interactionAt > i.interactionAt" +
                ")",
                UserRecipeInteraction.class)
                .setParameter("mealPlanId", mealPlanId)
                .setParameter("userId", userId)
                .setParameter("acceptedType", FeedbackAction.ACCEPTED.name())
                .setParameter("rejectedType", FeedbackAction.REJECTED.name())
                .getResultList();
    }

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
