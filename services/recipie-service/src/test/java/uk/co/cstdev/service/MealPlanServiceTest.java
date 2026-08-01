package uk.co.cstdev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
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
}
