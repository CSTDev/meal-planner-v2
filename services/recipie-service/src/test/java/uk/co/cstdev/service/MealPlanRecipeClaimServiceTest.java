package uk.co.cstdev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
import uk.co.cstdev.data.RecipeRepository;
import uk.co.cstdev.data.User;
import uk.co.cstdev.data.UserRecipeInteraction;

@QuarkusTest
public class MealPlanRecipeClaimServiceTest {

    @Inject
    MealPlanRecipeClaimService claimService;

    @Inject
    MealPlanRecipeRepository mealPlanRecipeRepository;

    @Inject
    RecipeRepository recipeRepository;

    private User user;
    private MealPlan mealPlan;
    private Recipe recipeA;
    private Recipe recipeB;

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

    @Test
    public void claimFromCandidatesRetriesTheNextCandidateWhenTheFirstLosesTheClaimRace() {
        // Simulate a concurrent request that already claimed recipeA a
        // moment earlier.
        mealPlanRecipeRepository.claim(mealPlan.id, recipeA.id, "OFFERED");

        List<Recipe> claimed = claimService.claimFromCandidates(
                mealPlan.id, Arrays.asList(recipeA, recipeB), 1, "OFFERED");

        assertEquals(1, claimed.size());
        assertEquals(recipeB.id, claimed.get(0).id);

        // recipeA's original row is untouched, recipeB was claimed as a new row.
        List<MealPlanRecipe> rows = mealPlanRecipeRepository.listByMealPlan(mealPlan.id);
        Set<UUID> claimedRecipeIds = rows.stream().map(r -> r.id.recipeId).collect(Collectors.toSet());
        assertEquals(Set.of(recipeA.id, recipeB.id), claimedRecipeIds);
    }

    @Test
    public void claimNewClaimsDistinctRecipesUpToTheEligiblePool() {
        List<Recipe> claimed = claimService.claimNew(mealPlan.id, user.id, 5, "OFFERED");

        // Only 2 recipes exist in the eligible pool.
        assertEquals(2, claimed.size());
        assertEquals(2, mealPlanRecipeRepository.listByMealPlan(mealPlan.id).size());
    }

    @Test
    public void claimReplacementSkipsRecipesAlreadyClaimedElsewhereInThePlan() {
        mealPlanRecipeRepository.claim(mealPlan.id, recipeA.id, "OFFERED");
        mealPlanRecipeRepository.claim(mealPlan.id, recipeB.id, "OFFERED");

        Optional<Recipe> replacement = claimService.claimReplacement(mealPlan.id, user.id, recipeA.id, "OFFERED");

        // No eligible candidates remain — both recipes are already claimed.
        assertTrue(replacement.isEmpty());
    }
}
