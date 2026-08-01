package uk.co.cstdev.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@QuarkusTest
public class RecipeRepositoryTest {

    @Inject
    RecipeRepository recipeRepository;

    @Inject
    MealPlanRecipeRepository mealPlanRecipeRepository;

    @Inject
    EntityManager entityManager;

    private User user;
    private MealPlan mealPlan;
    private Recipe recipeA;
    private Recipe recipeB;
    private Recipe recipeC;

    @BeforeEach
    public void setup() {
        QuarkusTransaction.requiringNew().run(() -> {
            user = User.Builder.builder()
                    .id(UUID.randomUUID())
                    .email(UUID.randomUUID() + "@test.com")
                    .name("Test User")
                    .createdAt(new java.util.Date())
                    .build();
            user.persistAndFlush();
            recipeA = Recipe.Builder.recipe().title("Recipe A").servings(2).build();
            recipeA.persist();
            recipeB = Recipe.Builder.recipe().title("Recipe B").servings(2).build();
            recipeB.persist();
            recipeC = Recipe.Builder.recipe().title("Recipe C").servings(2).build();
            recipeC.persist();
            mealPlan = MealPlan.Builder.builder()
                    .userId(user.id)
                    .recipeSource("all")
                    .status("ACTIVE")
                    .createdAt(new java.util.Date())
                    .build();
            mealPlan.persistAndFlush();
        });
    }

    @AfterEach
    public void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            MealPlanRecipe.deleteAll();
            UserRecipeInteraction.deleteAll();
            MealPlan.deleteAll();
            Recipe.deleteAll();
            User.deleteAll();
        });
    }

    private void insertInteraction(UUID userId, UUID recipeId, UUID mealPlanId, FeedbackAction type, Instant at) {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                INSERT INTO user_recipe_interactions (user_id, recipe_id, meal_plan_id, interaction_type, interaction_at)
                VALUES (?, ?, ?, ?, ?)
                """)
                .setParameter(1, userId)
                .setParameter(2, recipeId)
                .setParameter(3, mealPlanId)
                .setParameter(4, type.name())
                .setParameter(5, at)
                .executeUpdate());
    }

    private Set<UUID> candidateIds(int limit) {
        return recipeRepository.findEligibleCandidates(mealPlan.id, user.id, limit)
                .stream()
                .map(r -> r.id)
                .collect(Collectors.toSet());
    }

    @Test
    public void excludesRecipesAcceptedInThisPlan() {
        insertInteraction(user.id, recipeA.id, mealPlan.id, FeedbackAction.ACCEPTED, Instant.now());

        assertFalse(candidateIds(10).contains(recipeA.id));
        assertTrue(candidateIds(10).contains(recipeB.id));
    }

    @Test
    public void excludesRecipesRejectedInThisPlan() {
        insertInteraction(user.id, recipeA.id, mealPlan.id, FeedbackAction.REJECTED, Instant.now());

        assertFalse(candidateIds(10).contains(recipeA.id));
    }

    @Test
    public void excludesRecipesWithAnyInteractionInTheLast90DaysAcrossAnyPlan() {
        MealPlan otherPlan = MealPlan.Builder.builder()
                .userId(user.id).recipeSource("all").status("ACTIVE").createdAt(new java.util.Date()).build();
        QuarkusTransaction.requiringNew().run(otherPlan::persistAndFlush);

        insertInteraction(user.id, recipeA.id, otherPlan.id, FeedbackAction.ACCEPTED,
                Instant.now().minusSeconds(60L * 60 * 24 * 30));

        assertFalse(candidateIds(10).contains(recipeA.id));
    }

    @Test
    public void includesRecipesWithInteractionsOlderThan90Days() {
        MealPlan otherPlan = MealPlan.Builder.builder()
                .userId(user.id).recipeSource("all").status("ACTIVE").createdAt(new java.util.Date()).build();
        QuarkusTransaction.requiringNew().run(otherPlan::persistAndFlush);

        insertInteraction(user.id, recipeA.id, otherPlan.id, FeedbackAction.ACCEPTED,
                Instant.now().minusSeconds(60L * 60 * 24 * 100));

        assertTrue(candidateIds(10).contains(recipeA.id));
    }

    @Test
    public void onlyConsidersTheGivenUsersInteractions() {
        User otherUser = User.Builder.builder()
                .id(UUID.randomUUID()).email(UUID.randomUUID() + "@test.com").name("Other")
                .createdAt(new java.util.Date()).build();
        QuarkusTransaction.requiringNew().run(otherUser::persistAndFlush);

        insertInteraction(otherUser.id, recipeA.id, mealPlan.id, FeedbackAction.ACCEPTED, Instant.now());

        // recipeA was only interacted with by a different user — still eligible for `user`.
        assertTrue(candidateIds(10).contains(recipeA.id));
    }

    @Test
    public void excludesRecipesAlreadyClaimedInThisPlan() {
        mealPlanRecipeRepository.claim(mealPlan.id, recipeA.id, "OFFERED");

        assertFalse(candidateIds(10).contains(recipeA.id));
        assertTrue(candidateIds(10).contains(recipeB.id));
    }

    @Test
    public void returnsFewerThanTheLimitWhenThePoolIsSmaller() {
        List<Recipe> candidates = recipeRepository.findEligibleCandidates(mealPlan.id, user.id, 100);

        // Only the 3 seeded recipes are eligible.
        assertEquals(3, candidates.size());
    }
}
