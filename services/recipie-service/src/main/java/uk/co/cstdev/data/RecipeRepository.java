package uk.co.cstdev.data;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@ApplicationScoped
public class RecipeRepository implements PanacheRepository<Recipe> {

    @PersistenceContext
    EntityManager em;

    public List<Recipe> findRecommendations(int numRecipes) {
        /*
         * String hql = """
         * SELECT r.*
         * FROM recipes r
         * WHERE
         * -- Filter by recipe ownership based on meal plan preference
         * (
         * ($recipe_source = 'own' AND r.scraped_by_user_id = $user_id)
         * OR ($recipe_source = 'all')
         * OR ($recipe_source = 'shared' AND r.scraped_by_user_id != $user_id)
         * )
         * AND r.id NOT IN (
         * -- Exclude recipes already accepted in this meal plan
         * SELECT recipe_id FROM user_recipe_interactions
         * WHERE user_id = $user_id
         * AND meal_plan_id = $meal_plan_id
         * AND interaction_type = 'accepted'
         * )
         * AND r.id NOT IN (
         * -- Exclude recipes rejected in this meal plan
         * SELECT recipe_id FROM user_recipe_interactions
         * WHERE user_id = $user_id
         * AND meal_plan_id = $meal_plan_id
         * AND interaction_type = 'rejected'
         * )
         * AND r.id NOT IN (
         * -- Exclude recently shown recipes (last 30 days)
         * SELECT recipe_id FROM user_recipe_interactions
         * WHERE user_id = $user_id
         * AND interaction_type IN ('accepted', 'rejected')
         * AND interaction_at > NOW() - INTERVAL '30 days'
         * )
         * ORDER BY RANDOM()
         * LIMIT $num_recipes;
         * """;
         * 
         */

        String hql = """
                SELECT r.*
                FROM recipes r
                WHERE r.id NOT IN (
                    -- Exclude recipes already accepted in this meal plan
                    SELECT recipe_id FROM user_recipe_interactions
                    WHERE interaction_type = 'accepted'
                )
                AND r.id NOT IN (
                    -- Exclude recipes rejected in this meal plan
                    SELECT recipe_id FROM user_recipe_interactions
                    WHERE interaction_type = 'rejected'
                )
                AND r.id NOT IN (
                    -- Exclude recently shown recipes (last 30 days)
                    SELECT recipe_id FROM user_recipe_interactions
                    WHERE interaction_at > NOW() - INTERVAL '30 days'
                )
                ORDER BY RANDOM()
                LIMIT :num_recipes;
                """;

        return em.createNativeQuery(hql, Recipe.class)
                .setParameter("num_recipes", numRecipes)
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
