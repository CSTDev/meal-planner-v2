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
import uk.co.cstdev.data.Recipe;

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
        QuarkusTransaction.requiringNew().run(() -> Recipe.deleteAll());
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
}
