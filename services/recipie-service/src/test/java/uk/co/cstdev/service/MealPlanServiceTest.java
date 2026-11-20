package uk.co.cstdev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import uk.co.cstdev.data.FeedbackAction;
import uk.co.cstdev.data.RecipeRepository;
import uk.co.cstdev.data.MealPlan;
import uk.co.cstdev.data.MealPlanRecipe;
import uk.co.cstdev.data.MealPlanRecipeRepository;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.User;
import uk.co.cstdev.data.UserRecipeInteraction;
import uk.co.cstdev.data.mealplan.MealPlanRequest;

@QuarkusTest
public class MealPlanServiceTest {

    @Inject
    MealPlanService mealPlanService;

    @Inject
    MealPlanRecipeRepository mealPlanRecipeRepository;

    @Inject
    RecipeRepository recipeRepository;

    @Inject
    EntityManager entityManager;

    private User user;

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
            Recipe.Builder.recipe().title("Recipe A").servings(2).build().persist();
            Recipe.Builder.recipe().title("Recipe B").servings(2).build().persist();
            Recipe.Builder.recipe().title("Recipe C").servings(2).build().persist();
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

    @Test
    public void createMealPlanAtomicallyClaimsItsInitialRecipesAsOffered() {
        MealPlan plan = mealPlanService.createMealPlan(new MealPlanRequest(2, "all"), user.id.toString());

        assertNotNull(plan.id);
        var rows = mealPlanRecipeRepository.listByMealPlan(plan.id);
        assertEquals(2, rows.size());
        rows.forEach(row -> assertEquals("OFFERED", row.status));
    }

    @Test
    public void createMealPlanClaimsFewerRecipesThanRequestedWhenThePoolIsSmaller() {
        MealPlan plan = mealPlanService.createMealPlan(new MealPlanRequest(10, "all"), user.id.toString());

        // Only 3 recipes exist in the seeded pool.
        assertEquals(3, mealPlanRecipeRepository.listByMealPlan(plan.id).size());
    }

    private MealPlan persistPlan() {
        return QuarkusTransaction.requiringNew().call(() -> {
            MealPlan plan = MealPlan.Builder.builder().userId(user.id).recipeSource("all").status("ACTIVE")
                    .createdAt(new java.util.Date()).build();
            plan.persistAndFlush();
            return plan;
        });
    }

    private void interact(MealPlan plan, Recipe recipe, FeedbackAction action) {
        QuarkusTransaction.requiringNew().run(
                () -> new UserRecipeInteraction(user.id, recipe.id, plan.id, action.name()).persistAndFlush());
    }

    @Test
    public void deletingAPlanStopsItsAcceptsAndRejectsExcludingRecipesFromOtherPlans() {
        MealPlan discarded = persistPlan();
        MealPlan other = persistPlan();
        List<Recipe> all = Recipe.listAll();
        Recipe accepted = all.get(0);
        Recipe rejected = all.get(1);
        interact(discarded, accepted, FeedbackAction.ACCEPTED);
        interact(discarded, rejected, FeedbackAction.REJECTED);

        List<UUID> before = recipeRepository.findEligibleCandidates(other.id, user.id, 10).stream()
                .map(r -> r.id).collect(Collectors.toList());
        assertFalse(before.contains(accepted.id));
        assertFalse(before.contains(rejected.id));

        mealPlanService.deleteMealPlan(discarded.id);

        List<UUID> after = recipeRepository.findEligibleCandidates(other.id, user.id, 10).stream()
                .map(r -> r.id).collect(Collectors.toList());
        assertTrue(after.contains(accepted.id));
        assertTrue(after.contains(rejected.id));
    }

    @Test
    public void deleteMealPlanIsAtomicWhenTheFinalDeleteFails() {
        MealPlan plan = persistPlan();
        Recipe recipe = Recipe.<Recipe>listAll().get(0);
        mealPlanRecipeRepository.claim(plan.id, recipe.id, "ACCEPTED");
        interact(plan, recipe, FeedbackAction.ACCEPTED);

        // Make the last of the three deletes (the meal_plans row) blow up.
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("""
                    CREATE OR REPLACE FUNCTION fail_plan_delete() RETURNS trigger AS $$
                    BEGIN RAISE EXCEPTION 'boom'; END; $$ LANGUAGE plpgsql
                    """).executeUpdate();
            entityManager.createNativeQuery("""
                    CREATE TRIGGER fail_plan_delete_trg BEFORE DELETE ON meal_plans
                    FOR EACH ROW EXECUTE FUNCTION fail_plan_delete()
                    """).executeUpdate();
        });
        try {
            assertThrows(Exception.class, () -> mealPlanService.deleteMealPlan(plan.id));
        } finally {
            QuarkusTransaction.requiringNew().run(() -> {
                entityManager.createNativeQuery("DROP TRIGGER fail_plan_delete_trg ON meal_plans").executeUpdate();
                entityManager.createNativeQuery("DROP FUNCTION fail_plan_delete()").executeUpdate();
            });
        }

        assertEquals(1, mealPlanRecipeRepository.listByMealPlan(plan.id).size());
        assertEquals(1, QuarkusTransaction.requiringNew()
                .call(() -> UserRecipeInteraction.count("mealPlanId", plan.id)));
        assertNotNull(QuarkusTransaction.requiringNew().call(() -> MealPlan.findById(plan.id)));
    }
}
