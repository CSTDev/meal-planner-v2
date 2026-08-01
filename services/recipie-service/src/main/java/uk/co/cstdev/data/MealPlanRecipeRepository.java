package uk.co.cstdev.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class MealPlanRecipeRepository implements PanacheRepositoryBase<MealPlanRecipe, MealPlanRecipeId> {

    public Optional<MealPlanRecipe> findByPlanAndRecipe(UUID mealPlanId, UUID recipeId) {
        return find("id.mealPlanId = ?1 and id.recipeId = ?2", mealPlanId, recipeId).firstResultOptional();
    }

    public List<MealPlanRecipe> listByMealPlan(UUID mealPlanId) {
        return list("id.mealPlanId", mealPlanId);
    }

    /**
     * Recipe ids currently ACCEPTED into this plan. The result has no
     * inherent order — callers needing an order must sort explicitly.
     */
    public List<UUID> listAcceptedRecipeIds(UUID mealPlanId) {
        return list("id.mealPlanId = ?1 and status = ?2", mealPlanId, MealPlanRecipeStatus.ACCEPTED.name())
                .stream()
                .map(row -> row.id.recipeId)
                .toList();
    }

    /**
     * Counts ACCEPTED rows per meal plan, scoped to the given user's plans,
     * in a single grouped query — avoiding the N+1 of counting one plan at
     * a time. Plans with zero accepted recipes are simply absent from the
     * result rather than mapped to zero.
     */
    @SuppressWarnings("unchecked")
    public Map<UUID, Long> countAcceptedByUser(UUID userId) {
        List<Object[]> rows = getEntityManager().createNativeQuery("""
                SELECT mpr.meal_plan_id, COUNT(*)
                FROM meal_plan_recipes mpr
                JOIN meal_plans mp ON mp.id = mpr.meal_plan_id
                WHERE mpr.status = 'ACCEPTED' AND mp.user_id = ?1
                GROUP BY mpr.meal_plan_id
                """)
                .setParameter(1, userId)
                .getResultList();

        Map<UUID, Long> countsByPlan = new HashMap<>();
        for (Object[] row : rows) {
            countsByPlan.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return countsByPlan;
    }

    /**
     * Attempts to claim {@code recipeId} into {@code mealPlanId} with the
     * given status. Returns {@code false} (without throwing) if the
     * (meal_plan_id, recipe_id) pair is already claimed — the caller is
     * expected to retry with a different candidate.
     */
    @Transactional
    public boolean claim(UUID mealPlanId, UUID recipeId, String status) {
        int inserted = getEntityManager().createNativeQuery("""
                INSERT INTO meal_plan_recipes (meal_plan_id, recipe_id, status)
                VALUES (?1, ?2, ?3)
                ON CONFLICT (meal_plan_id, recipe_id) DO NOTHING
                """)
                .setParameter(1, mealPlanId)
                .setParameter(2, recipeId)
                .setParameter(3, status)
                .executeUpdate();
        return inserted > 0;
    }

    /**
     * Atomically moves the row identified by (mealPlanId, oldRecipeId) onto
     * a new recipe, setting its status. Returns {@code false} (without
     * throwing) if the row doesn't exist, or if {@code newRecipeId} is
     * already claimed by a different row in this plan — the unique
     * constraint on (meal_plan_id, recipe_id) is relied on directly (rather
     * than a pre-check) to detect this, so it's caught even under a genuine
     * concurrent collision, not just one that was already committed. The
     * attempt runs in its own transaction so that a constraint violation
     * only rolls back this attempt, leaving the caller's own transaction
     * (and any work already done in it) unaffected — mirroring
     * {@link #claim}, which never throws on collision either.
     */
    public boolean reclaim(UUID mealPlanId, UUID oldRecipeId, UUID newRecipeId, String status) {
        try {
            return QuarkusTransaction.requiringNew().call(() -> {
                int updated = getEntityManager().createNativeQuery("""
                        UPDATE meal_plan_recipes
                        SET recipe_id = ?3, status = ?4
                        WHERE meal_plan_id = ?1 AND recipe_id = ?2
                        """)
                        .setParameter(1, mealPlanId)
                        .setParameter(2, oldRecipeId)
                        .setParameter(3, newRecipeId)
                        .setParameter(4, status)
                        .executeUpdate();
                return updated > 0;
            });
        } catch (PersistenceException e) {
            return false;
        }
    }

    /**
     * Updates the status of an existing row in place (e.g. OFFERED ->
     * ACCEPTED). Returns {@code false} if no such row exists.
     */
    @Transactional
    public boolean updateStatus(UUID mealPlanId, UUID recipeId, String status) {
        int updated = getEntityManager().createNativeQuery("""
                UPDATE meal_plan_recipes
                SET status = ?3
                WHERE meal_plan_id = ?1 AND recipe_id = ?2
                """)
                .setParameter(1, mealPlanId)
                .setParameter(2, recipeId)
                .setParameter(3, status)
                .executeUpdate();
        return updated > 0;
    }

    @Transactional
    public void deleteByPlanAndRecipe(UUID mealPlanId, UUID recipeId) {
        delete("id.mealPlanId = ?1 and id.recipeId = ?2", mealPlanId, recipeId);
    }
}
