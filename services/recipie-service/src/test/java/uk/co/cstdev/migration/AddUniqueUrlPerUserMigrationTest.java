package uk.co.cstdev.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
 * Exercises the literal SQL of V6__add_unique_url_per_user.sql (loaded from
 * the classpath rather than re-typed here, so the test can't drift from what
 * Flyway actually runs) against a recipes table seeded with pre-existing
 * duplicate (url, scraped_by_user_id) rows — simulating a production
 * database where nothing has prevented duplicates until now.
 * <p>
 * The %test schema already has {@code recipes_url_user_unique} (Hibernate
 * creates it from the {@code Recipe} entity's {@code @Table} mapping), so
 * each test drops it first to reproduce the pre-migration state, then
 * restores it afterwards for the rest of the suite.
 */
@QuarkusTest
public class AddUniqueUrlPerUserMigrationTest {

    private static final String CONSTRAINT_NAME = "recipes_url_user_unique";

    @Inject
    EntityManager entityManager;

    @BeforeEach
    public void dropExistingConstraint() {
        QuarkusTransaction.requiringNew().run(() -> {
            if (constraintExists()) {
                entityManager.createNativeQuery("ALTER TABLE recipes DROP CONSTRAINT " + CONSTRAINT_NAME)
                        .executeUpdate();
            }
        });
    }

    @AfterEach
    public void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            UserRecipeInteraction.deleteAll();
            MealPlanRecipe.deleteAll();
        });
        QuarkusTransaction.requiringNew().run(() -> {
            Recipe.deleteAll();
            MealPlan.deleteAll();
            User.deleteAll();
        });
        QuarkusTransaction.requiringNew().run(() -> {
            if (!constraintExists()) {
                entityManager.createNativeQuery("ALTER TABLE recipes ADD CONSTRAINT " + CONSTRAINT_NAME
                        + " UNIQUE (url, scraped_by_user_id)").executeUpdate();
            }
        });
    }

    private boolean constraintExists() {
        List<?> rows = entityManager
                .createNativeQuery("SELECT 1 FROM pg_constraint WHERE conname = '" + CONSTRAINT_NAME + "'")
                .getResultList();
        return !rows.isEmpty();
    }

    private UUID insertRecipe(String url, UUID userId, Instant createdAt) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                INSERT INTO recipes
                    (id, url, scraped_by_user_id, createdat, title, servings, preptimeminutes, cooktimeminutes)
                VALUES
                    (?1, ?2, ?3, ?4, 'Test Recipe', 2, 0, 0)
                """)
                .setParameter(1, id)
                .setParameter(2, url)
                .setParameter(3, userId)
                .setParameter(4, Timestamp.from(createdAt))
                .executeUpdate());
        return id;
    }

    private UUID insertUser() {
        User user = User.Builder.builder().id(UUID.randomUUID()).email(UUID.randomUUID() + "@test.com")
                .name("Test User").createdAt(new java.util.Date()).build();
        QuarkusTransaction.requiringNew().run(user::persistAndFlush);
        return user.id;
    }

    private UUID insertMealPlan(UUID userId) {
        MealPlan mealPlan = MealPlan.Builder.builder().userId(userId).recipeSource("own").status("active")
                .createdAt(new java.util.Date()).build();
        QuarkusTransaction.requiringNew().run(mealPlan::persistAndFlush);
        return mealPlan.id;
    }

    private void insertMealPlanRecipe(UUID mealPlanId, UUID recipeId, String status) {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                INSERT INTO meal_plan_recipes (meal_plan_id, recipe_id, status)
                VALUES (?1, ?2, ?3)
                """)
                .setParameter(1, mealPlanId)
                .setParameter(2, recipeId)
                .setParameter(3, status)
                .executeUpdate());
    }

    private void insertUserRecipeInteraction(UUID userId, UUID recipeId, UUID mealPlanId, String interactionType) {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                INSERT INTO user_recipe_interactions
                    (id, user_id, recipe_id, meal_plan_id, interaction_type, interaction_at)
                VALUES
                    (gen_random_uuid(), ?1, ?2, ?3, ?4, now())
                """)
                .setParameter(1, userId)
                .setParameter(2, recipeId)
                .setParameter(3, mealPlanId)
                .setParameter(4, interactionType)
                .executeUpdate());
    }

    @SuppressWarnings("unchecked")
    private List<UUID> mealPlanRecipeIdsFor(UUID mealPlanId) {
        return entityManager.createNativeQuery("SELECT recipe_id FROM meal_plan_recipes WHERE meal_plan_id = ?1")
                .setParameter(1, mealPlanId)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<UUID> userRecipeInteractionRecipeIdsFor(UUID userId) {
        return entityManager.createNativeQuery("SELECT recipe_id FROM user_recipe_interactions WHERE user_id = ?1")
                .setParameter(1, userId)
                .getResultList();
    }

    private void runV6Migration() {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V6__add_unique_url_per_user.sql")) {
            assertTrue(in != null, "V6__add_unique_url_per_user.sql must exist on the classpath");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                entityManager.createNativeQuery(trimmed).executeUpdate();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<UUID> remainingIds(String url, UUID userId) {
        return entityManager.createNativeQuery(
                "SELECT id FROM recipes WHERE url IS NOT DISTINCT FROM ?1 AND scraped_by_user_id IS NOT DISTINCT FROM ?2")
                .setParameter(1, url)
                .setParameter(2, userId)
                .getResultList();
    }

    @Test
    public void keepsOnlyTheEarliestOfDuplicateUrlAndUserRowsThenAddsTheConstraint() {
        UUID userId = UUID.randomUUID();
        String url = "https://example.com/pre-existing-duplicate";
        Instant t0 = Instant.now().minusSeconds(120);

        UUID earliest = insertRecipe(url, userId, t0);
        insertRecipe(url, userId, t0.plusSeconds(60));
        insertRecipe(url, userId, t0.plusSeconds(120));

        QuarkusTransaction.requiringNew().run(this::runV6Migration);

        assertEquals(List.of(earliest), remainingIds(url, userId));
        assertTrue(constraintExists(), "Migration must (re-)add the unique constraint");
    }

    @Test
    public void leavesRowsWithANullUrlUntouched() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        insertRecipe(null, userId, now);
        insertRecipe(null, userId, now);

        QuarkusTransaction.requiringNew().run(this::runV6Migration);

        assertEquals(2, remainingIds(null, userId).size());
    }

    @Test
    public void leavesRowsWithANullScrapedByUserIdUntouched() {
        String url = "https://example.com/legacy-unowned-recipe";
        Instant now = Instant.now();

        insertRecipe(url, null, now);
        insertRecipe(url, null, now);

        QuarkusTransaction.requiringNew().run(this::runV6Migration);

        assertEquals(2, remainingIds(url, null).size());
    }

    @Test
    public void rePointsAMealPlanRecipesRowReferencingTheDeletedDuplicateToTheSurvivor() {
        UUID scrapedByUserId = UUID.randomUUID();
        String url = "https://example.com/referenced-by-meal-plan-recipes";
        Instant t0 = Instant.now().minusSeconds(120);

        UUID earliest = insertRecipe(url, scrapedByUserId, t0);
        UUID later = insertRecipe(url, scrapedByUserId, t0.plusSeconds(60));

        UUID mealPlanId = insertMealPlan(null);
        insertMealPlanRecipe(mealPlanId, later, "OFFERED");

        // Must not throw a foreign key violation.
        QuarkusTransaction.requiringNew().run(this::runV6Migration);

        assertEquals(List.of(earliest), remainingIds(url, scrapedByUserId));
        assertEquals(List.of(earliest), mealPlanRecipeIdsFor(mealPlanId),
                "meal_plan_recipes row must be re-pointed to the surviving recipe, not left dangling or deleted");
        assertTrue(constraintExists(), "Migration must (re-)add the unique constraint");
    }

    @Test
    public void rePointsAUserRecipeInteractionsRowReferencingTheDeletedDuplicateToTheSurvivor() {
        UUID scrapedByUserId = UUID.randomUUID();
        String url = "https://example.com/referenced-by-user-recipe-interactions";
        Instant t0 = Instant.now().minusSeconds(120);

        UUID earliest = insertRecipe(url, scrapedByUserId, t0);
        UUID later = insertRecipe(url, scrapedByUserId, t0.plusSeconds(60));

        UUID interactingUserId = insertUser();
        insertUserRecipeInteraction(interactingUserId, later, null, "VIEWED");

        // Must not throw a foreign key violation.
        QuarkusTransaction.requiringNew().run(this::runV6Migration);

        assertEquals(List.of(earliest), remainingIds(url, scrapedByUserId));
        assertEquals(List.of(earliest), userRecipeInteractionRecipeIdsFor(interactingUserId),
                "user_recipe_interactions row must be re-pointed to the surviving recipe, not left dangling or deleted");
    }

    @Test
    public void dropsTheRedundantMealPlanRecipesRowWhenRePointingWouldCollideWithAnExistingRow() {
        UUID scrapedByUserId = UUID.randomUUID();
        String url = "https://example.com/collides-in-the-same-meal-plan";
        Instant t0 = Instant.now().minusSeconds(120);

        UUID earliest = insertRecipe(url, scrapedByUserId, t0);
        UUID later = insertRecipe(url, scrapedByUserId, t0.plusSeconds(60));

        // The same meal plan already has a row for the survivor *and* a row
        // for the duplicate that's about to be deleted -- re-pointing the
        // second row naively would collide with the first on the
        // (meal_plan_id, recipe_id) primary key.
        UUID mealPlanId = insertMealPlan(null);
        insertMealPlanRecipe(mealPlanId, earliest, "ACCEPTED");
        insertMealPlanRecipe(mealPlanId, later, "OFFERED");

        // Must not throw a primary key violation.
        QuarkusTransaction.requiringNew().run(this::runV6Migration);

        assertEquals(List.of(earliest), remainingIds(url, scrapedByUserId));
        assertEquals(List.of(earliest), mealPlanRecipeIdsFor(mealPlanId),
                "Only one meal_plan_recipes row should remain for the plan, pointing at the survivor");
        assertTrue(constraintExists(), "Migration must (re-)add the unique constraint");
    }

    @Test
    public void dropsTheRedundantUserRecipeInteractionsRowWhenRePointingWouldCollideWithAnExistingRow() {
        UUID scrapedByUserId = UUID.randomUUID();
        String url = "https://example.com/collides-in-the-same-interaction-group";
        Instant t0 = Instant.now().minusSeconds(120);

        UUID earliest = insertRecipe(url, scrapedByUserId, t0);
        UUID later = insertRecipe(url, scrapedByUserId, t0.plusSeconds(60));

        // Same user, same interaction type, same meal plan recorded against
        // both the survivor and the duplicate about to be deleted --
        // re-pointing the second row naively would collide with the first
        // on the unique (user_id, recipe_id, meal_plan_id, interaction_type)
        // constraint. (A null meal_plan_id would never collide under
        // standard SQL null semantics, so a real meal plan id is required
        // to reproduce the collision.)
        UUID interactingUserId = insertUser();
        UUID mealPlanId = insertMealPlan(interactingUserId);
        insertUserRecipeInteraction(interactingUserId, earliest, mealPlanId, "VIEWED");
        insertUserRecipeInteraction(interactingUserId, later, mealPlanId, "VIEWED");

        // Must not throw a unique constraint violation.
        QuarkusTransaction.requiringNew().run(this::runV6Migration);

        assertEquals(List.of(earliest), remainingIds(url, scrapedByUserId));
        assertEquals(List.of(earliest), userRecipeInteractionRecipeIdsFor(interactingUserId),
                "Only one user_recipe_interactions row should remain for the group, pointing at the survivor");
        assertTrue(constraintExists(), "Migration must (re-)add the unique constraint");
    }
}
