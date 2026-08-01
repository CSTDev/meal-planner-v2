package uk.co.cstdev.data;

import java.util.List;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@ApplicationScoped
public class RecipeRepository implements PanacheRepository<Recipe> {

    @PersistenceContext
    EntityManager em;

    public List<Recipe> findByUserId(UUID userId) {
        return list("scrapedByUserId", userId);
    }

    /**
     * Excludes recipes currently present in meal_plan_recipes for this
     * plan — offered or accepted — so a user can't search up and pick a
     * recipe already pending elsewhere in the same plan.
     */
    public List<Recipe> searchByTitle(String q, UUID mealPlanId, UUID userId) {
        String sql = """
                SELECT r.*
                FROM recipes r
                WHERE r.title ILIKE '%' || :q || '%'
                AND r.id NOT IN (
                    SELECT recipe_id FROM meal_plan_recipes
                    WHERE meal_plan_id = :meal_plan_id
                )
                ORDER BY r.title ASC
                LIMIT 10
                """;

        return em.createNativeQuery(sql, Recipe.class)
                .setParameter("q", q)
                .setParameter("meal_plan_id", mealPlanId)
                .getResultList();
    }

    /**
     * Candidate recipes eligible to be claimed into a meal plan slot, in
     * random order: excludes recipes already accepted/rejected in this
     * plan, recipes with any interaction in the last 90 days, and recipes
     * already present in meal_plan_recipes for this plan (offered or
     * accepted elsewhere in it) — a single join rather than three separate
     * NOT IN subqueries.
     */
    public List<Recipe> findEligibleCandidates(UUID mealPlanId, UUID userId, int limit) {
        String sql = """
                SELECT r.*
                FROM recipes r
                WHERE r.id NOT IN (
                    SELECT recipe_id FROM user_recipe_interactions
                    WHERE user_id = :user_id
                    AND (
                        (interaction_type IN (:acceptedType, :rejectedType) AND meal_plan_id = :meal_plan_id)
                        OR interaction_at > NOW() - INTERVAL '90 days'
                    )
                )
                AND r.id NOT IN (
                    SELECT recipe_id FROM meal_plan_recipes WHERE meal_plan_id = :meal_plan_id
                )
                ORDER BY RANDOM()
                LIMIT :limit
                """;

        return em.createNativeQuery(sql, Recipe.class)
                .setParameter("limit", limit)
                .setParameter("meal_plan_id", mealPlanId)
                .setParameter("user_id", userId)
                .setParameter("acceptedType", FeedbackAction.ACCEPTED.name())
                .setParameter("rejectedType", FeedbackAction.REJECTED.name())
                .getResultList();
    }

    // Use entity manager for complecated queries if needed e.g.:
    /*
     * String hql = """
     * SELECT new map(
     * c.id as customerId,
     * c.name as customerName,
     * c.email as customerEmail,
     * o.id as orderId,
     * o.orderDate as orderDate,
     * o.totalAmount as orderTotal,
     * p.name as productName,
     * oi.quantity as quantity,
     * oi.price as itemPrice
     * )
     * FROM Customer c
     * JOIN c.orders o
     * JOIN o.orderItems oi
     * JOIN oi.product p
     * WHERE c.id = :customerId
     * ORDER BY o.orderDate DESC
     * """;
     * 
     * return em.createQuery(hql, Map.class)
     * .setParameter("customerId", customerId)
     * .getResultList();
     */
}
