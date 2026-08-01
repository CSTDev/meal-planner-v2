package uk.co.cstdev.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import uk.co.cstdev.data.MealPlan;
import uk.co.cstdev.data.MealPlanRecipe;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.User;
import uk.co.cstdev.data.UserRecipeInteraction;

/**
 * Exercises the literal SQL of V5__backfill_meal_plan_recipes.sql (loaded
 * from the classpath rather than re-typed here, so the test can't drift
 * from what Flyway actually runs) directly against seeded historical
 * user_recipe_interactions data, on a plan with no meal_plan_recipes rows —
 * simulating a plan that predates the parent ticket's live-table.
 */
@QuarkusTest
public class BackfillMealPlanRecipesMigrationTest {

    @Inject
    EntityManager entityManager;

    private User user;
    private MealPlan mealPlan;
    private Recipe acceptedThenRejected;
    private Recipe rejectedThenAccepted;
    private Recipe acceptedOnly;

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

            mealPlan = MealPlan.Builder.builder()
                    .userId(user.id)
                    .recipeSource("all")
                    .status("ACTIVE")
                    .createdAt(new java.util.Date())
                    .build();
            mealPlan.persistAndFlush();

            acceptedThenRejected = Recipe.Builder.recipe().title("Accepted Then Rejected").servings(2).build();
            acceptedThenRejected.persist();
            rejectedThenAccepted = Recipe.Builder.recipe().title("Rejected Then Accepted").servings(2).build();
            rejectedThenAccepted.persist();
            acceptedOnly = Recipe.Builder.recipe().title("Accepted Only").servings(2).build();
            acceptedOnly.persist();
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

    private void insertInteraction(UUID recipeId, String type, Instant at) {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                INSERT INTO user_recipe_interactions
                    (id, user_id, recipe_id, meal_plan_id, interaction_type, interaction_at)
                VALUES
                    (gen_random_uuid(), ?1, ?2, ?3, ?4, ?5)
                """)
                .setParameter(1, user.id)
                .setParameter(2, recipeId)
                .setParameter(3, mealPlan.id)
                .setParameter(4, type)
                .setParameter(5, Timestamp.from(at))
                .executeUpdate());
    }

    private void runBackfillMigration() {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V5__backfill_meal_plan_recipes.sql")) {
            assertTrue(in != null, "V5__backfill_meal_plan_recipes.sql must exist on the classpath");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        entityManager.createNativeQuery(sql).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> currentMealPlanRecipes() {
        return entityManager.createNativeQuery(
                "SELECT recipe_id, status FROM meal_plan_recipes WHERE meal_plan_id = ?1")
                .setParameter(1, mealPlan.id)
                .getResultList();
    }

    @Test
    public void backfillsOnlyRecipesWhoseLatestInteractionIsAccepted() {
        Instant t0 = Instant.now().minus(10, ChronoUnit.MINUTES);

        // Accepted, then later rejected — must NOT be backfilled.
        insertInteraction(acceptedThenRejected.id, "ACCEPTED", t0);
        insertInteraction(acceptedThenRejected.id, "REJECTED", t0.plusSeconds(60));

        // Rejected, then later accepted — must be backfilled.
        insertInteraction(rejectedThenAccepted.id, "REJECTED", t0);
        insertInteraction(rejectedThenAccepted.id, "ACCEPTED", t0.plusSeconds(60));

        // Accepted, never touched again — must be backfilled.
        insertInteraction(acceptedOnly.id, "ACCEPTED", t0);

        QuarkusTransaction.requiringNew().run(this::runBackfillMigration);

        List<Object[]> rows = currentMealPlanRecipes();
        Set<UUID> backfilledRecipeIds = rows.stream().map(row -> (UUID) row[0]).collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.of(rejectedThenAccepted.id, acceptedOnly.id), backfilledRecipeIds);
        rows.forEach(row -> assertEquals("ACCEPTED", row[1]));
    }

    @Test
    public void isANoOpForAPlanThatAlreadyHasAMealPlanRecipesRow() {
        // Simulates a plan created after the parent ticket shipped: it
        // already has a live meal_plan_recipes row, kept up to date by the
        // normal accept/reject code paths, independent of the interaction
        // log below.
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                INSERT INTO meal_plan_recipes (meal_plan_id, recipe_id, status)
                VALUES (?1, ?2, ?3)
                """)
                .setParameter(1, mealPlan.id)
                .setParameter(2, acceptedOnly.id)
                .setParameter(3, "OFFERED")
                .executeUpdate());

        insertInteraction(acceptedOnly.id, "ACCEPTED", Instant.now());

        // Must not throw (ON CONFLICT DO NOTHING) and must leave the
        // existing row untouched.
        QuarkusTransaction.requiringNew().run(this::runBackfillMigration);

        List<Object[]> rows = currentMealPlanRecipes();
        assertEquals(1, rows.size());
        assertEquals(acceptedOnly.id, rows.get(0)[0]);
        assertEquals("OFFERED", rows.get(0)[1], "Pre-existing row must be left untouched by the backfill");
    }
}
