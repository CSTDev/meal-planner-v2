package uk.co.cstdev.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

@QuarkusTest
public class MealPlanRecipeRepositoryTest {

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
            MealPlan.deleteAll();
            Recipe.deleteAll();
            User.deleteAll();
        });
    }

    @Test
    public void primaryKeyRejectsADuplicateInsertForTheSamePlanAndRecipe() {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery(
                    "INSERT INTO meal_plan_recipes (meal_plan_id, recipe_id, status) VALUES (?1, ?2, ?3)")
                    .setParameter(1, mealPlan.id)
                    .setParameter(2, recipeA.id)
                    .setParameter(3, "OFFERED")
                    .executeUpdate();
        });

        assertThrows(PersistenceException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery(
                    "INSERT INTO meal_plan_recipes (meal_plan_id, recipe_id, status) VALUES (?1, ?2, ?3)")
                    .setParameter(1, mealPlan.id)
                    .setParameter(2, recipeA.id)
                    .setParameter(3, "ACCEPTED")
                    .executeUpdate();
        }));
    }

    @Test
    public void claimInsertsANewRowAndReturnsTrue() {
        boolean claimed = mealPlanRecipeRepository.claim(mealPlan.id, recipeA.id, "OFFERED");

        assertTrue(claimed);
        List<MealPlanRecipe> rows = mealPlanRecipeRepository.listByMealPlan(mealPlan.id);
        assertEquals(1, rows.size());
        assertEquals(recipeA.id, rows.get(0).id.recipeId);
        assertEquals("OFFERED", rows.get(0).status);
    }

    @Test
    public void claimReturnsFalseWithoutThrowingWhenTheRecipeIsAlreadyClaimedInThisPlan() {
        mealPlanRecipeRepository.claim(mealPlan.id, recipeA.id, "OFFERED");

        boolean secondClaim = mealPlanRecipeRepository.claim(mealPlan.id, recipeA.id, "ACCEPTED");

        assertFalse(secondClaim);
        // Still exactly one row, untouched by the failed second claim.
        List<MealPlanRecipe> rows = mealPlanRecipeRepository.listByMealPlan(mealPlan.id);
        assertEquals(1, rows.size());
        assertEquals("OFFERED", rows.get(0).status);
    }

    @Test
    public void reclaimMovesAnExistingRowOntoANewRecipe() {
        mealPlanRecipeRepository.claim(mealPlan.id, recipeA.id, "OFFERED");

        boolean reclaimed = mealPlanRecipeRepository.reclaim(mealPlan.id, recipeA.id, recipeB.id, "ACCEPTED");

        assertTrue(reclaimed);
        List<MealPlanRecipe> rows = mealPlanRecipeRepository.listByMealPlan(mealPlan.id);
        assertEquals(1, rows.size());
        assertEquals(recipeB.id, rows.get(0).id.recipeId);
        assertEquals("ACCEPTED", rows.get(0).status);
    }

    @Test
    public void reclaimReturnsFalseWhenTheNewRecipeIsAlreadyClaimedByAnotherRow() {
        mealPlanRecipeRepository.claim(mealPlan.id, recipeA.id, "OFFERED");
        mealPlanRecipeRepository.claim(mealPlan.id, recipeB.id, "OFFERED");

        boolean reclaimed = mealPlanRecipeRepository.reclaim(mealPlan.id, recipeA.id, recipeB.id, "ACCEPTED");

        assertFalse(reclaimed);
        // Both original rows remain untouched.
        List<MealPlanRecipe> rows = mealPlanRecipeRepository.listByMealPlan(mealPlan.id);
        assertEquals(2, rows.size());
    }

    @Test
    public void reclaimReturnsFalseInsteadOfThrowingUnderAGenuineConcurrentCollisionOnTheSameTargetRecipe()
            throws Exception {
        // Two distinct rows, each racing to become recipeC.
        mealPlanRecipeRepository.claim(mealPlan.id, recipeA.id, "OFFERED");
        mealPlanRecipeRepository.claim(mealPlan.id, recipeB.id, "OFFERED");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(2);
        try {
            Future<Boolean> first = executor.submit(() -> {
                startLatch.countDown();
                startLatch.await();
                return mealPlanRecipeRepository.reclaim(mealPlan.id, recipeA.id, recipeC.id, "ACCEPTED");
            });
            Future<Boolean> second = executor.submit(() -> {
                startLatch.countDown();
                startLatch.await();
                return mealPlanRecipeRepository.reclaim(mealPlan.id, recipeB.id, recipeC.id, "ACCEPTED");
            });

            // Neither call should throw — both must resolve to a boolean,
            // exactly one true (the winner) and one false (the loser, which
            // the caller is expected to retry with another candidate).
            boolean firstResult = first.get(30, TimeUnit.SECONDS);
            boolean secondResult = second.get(30, TimeUnit.SECONDS);

            assertTrue(firstResult ^ secondResult, "exactly one of the two concurrent reclaims should win");

            List<MealPlanRecipe> rows = mealPlanRecipeRepository.listByMealPlan(mealPlan.id);
            assertEquals(2, rows.size());
            long claimedAsRecipeC = rows.stream().filter(r -> r.id.recipeId.equals(recipeC.id)).count();
            assertEquals(1, claimedAsRecipeC, "recipeC should be claimed by exactly one row, never duplicated");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void deleteByPlanAndRecipeRemovesTheRow() {
        mealPlanRecipeRepository.claim(mealPlan.id, recipeA.id, "OFFERED");

        mealPlanRecipeRepository.deleteByPlanAndRecipe(mealPlan.id, recipeA.id);

        assertEquals(0, mealPlanRecipeRepository.listByMealPlan(mealPlan.id).size());
    }

    @Test
    public void countAcceptedByUserGroupsCountsByPlanAndIgnoresOfferedRows() {
        MealPlan secondPlan = MealPlan.Builder.builder()
                .userId(user.id)
                .recipeSource("all")
                .status("ACTIVE")
                .createdAt(new java.util.Date())
                .build();
        QuarkusTransaction.requiringNew().run(secondPlan::persistAndFlush);

        mealPlanRecipeRepository.claim(mealPlan.id, recipeA.id, "ACCEPTED");
        mealPlanRecipeRepository.claim(mealPlan.id, recipeB.id, "ACCEPTED");
        // Merely offered, not yet decided — must not be counted.
        mealPlanRecipeRepository.claim(mealPlan.id, recipeC.id, "OFFERED");
        mealPlanRecipeRepository.claim(secondPlan.id, recipeA.id, "ACCEPTED");

        Map<UUID, Long> counts = mealPlanRecipeRepository.countAcceptedByUser(user.id);

        assertEquals(2L, counts.get(mealPlan.id));
        assertEquals(1L, counts.get(secondPlan.id));
    }

    @Test
    public void countAcceptedByUserOmitsPlansWithNoAcceptedRows() {
        Map<UUID, Long> counts = mealPlanRecipeRepository.countAcceptedByUser(user.id);

        assertFalse(counts.containsKey(mealPlan.id));
    }

    @Test
    public void countAcceptedByUserExcludesOtherUsersPlans() {
        User otherUser = User.Builder.builder()
                .id(UUID.randomUUID())
                .email(UUID.randomUUID() + "@test.com")
                .name("Other User")
                .createdAt(new java.util.Date())
                .build();
        MealPlan otherPlan = MealPlan.Builder.builder()
                .userId(otherUser.id)
                .recipeSource("all")
                .status("ACTIVE")
                .createdAt(new java.util.Date())
                .build();
        QuarkusTransaction.requiringNew().run(() -> {
            otherUser.persistAndFlush();
            otherPlan.persistAndFlush();
        });

        mealPlanRecipeRepository.claim(otherPlan.id, recipeA.id, "ACCEPTED");

        Map<UUID, Long> counts = mealPlanRecipeRepository.countAcceptedByUser(user.id);

        assertFalse(counts.containsKey(otherPlan.id));
        // otherPlan/otherUser are cleaned up by the shared @AfterEach.
    }
}
