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

    public List<Recipe> findRecommendations(int numRecipes, UUID mealPlanId, UUID userId) {
        String hql = """
                SELECT r.*
                FROM recipes r
                WHERE r.id NOT IN (
                    -- Exclude recipes already accepted in this meal plan
                    SELECT recipe_id FROM user_recipe_interactions
                    WHERE interaction_type = :acceptedType
                    AND meal_plan_id = :meal_plan_id
                    and user_id = :user_id
                )
                AND r.id NOT IN (
                    -- Exclude recipes rejected in this meal plan
                    SELECT recipe_id FROM user_recipe_interactions
                    WHERE interaction_type = :rejectedType
                    AND meal_plan_id = :meal_plan_id
                    and user_id = :user_id
                )
                AND r.id NOT IN (
                    -- Exclude recently shown recipes (last 90 days)
                    SELECT recipe_id FROM user_recipe_interactions
                    WHERE interaction_at > NOW() - INTERVAL '90 days'
                    and user_id = :user_id
                )
                ORDER BY RANDOM()
                LIMIT :num_recipes;
                """;

        return em.createNativeQuery(hql, Recipe.class)
                .setParameter("num_recipes", numRecipes)
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
