package uk.co.cstdev.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.hibernate.exception.ConstraintViolationException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.RecipeRepository;

@ApplicationScoped
public class RecipeService {

    private static final Logger LOGGER = Logger.getLogger(RecipeService.class.getName());

    /** Postgres SQLState for a unique/primary key violation. */
    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

    @Inject
    public RecipeRepository recipeRepository;

    @Inject
    MeterRegistry meterRegistry;

    private Counter recipesAddedCounter;
    private Timer listRecipesTimer;

    @jakarta.annotation.PostConstruct
    void initMetrics() {
        recipesAddedCounter = Counter.builder("recipes.added.total")
                .description("Total number of recipes added via scraping")
                .register(meterRegistry);
        listRecipesTimer = Timer.builder("recipes.list.duration")
                .description("Time taken to list recipes for a user")
                .register(meterRegistry);
    }

    public List<Recipe> getAllRecipes() {
        return recipeRepository.listAll();
    }

    /**
     * Persists a scraped recipe. If two identical scrape requests for the
     * same URL slip past {@code ScrapeResource}'s pre-check and both reach
     * here, the second hits the unique constraint on
     * (url, scraped_by_user_id) — that case is swallowed rather than
     * propagated, so the Kafka message is acked instead of retried forever.
     * <p>
     * Hibernate defers the SQL INSERT until commit by default, so the
     * explicit {@code flush()} is required to surface a constraint
     * violation while still inside this method, where it can be caught.
     * {@code dontRollbackOn} then stops that caught exception from also
     * marking the surrounding JTA transaction for rollback.
     */
    @Transactional(dontRollbackOn = PersistenceException.class)
    public void addRecipe(Recipe recipe, UUID userId) {
        recipe.scrapedByUserId = userId;
        try {
            recipeRepository.persist(recipe);
            recipeRepository.flush();
        } catch (PersistenceException e) {
            if (isUniqueConstraintViolation(e)) {
                LOGGER.info(() -> "Duplicate recipe URL, ignoring: url=" + recipe.url + " userId=" + userId);
                return;
            }
            throw e;
        }
        recipesAddedCounter.increment();
    }

    /**
     * Unwraps the exception chain looking for a Hibernate
     * {@link ConstraintViolationException}, or a JDBC {@link SQLException}
     * with Postgres's unique-violation SQLState — either can be the
     * immediate cause depending on how the persistence provider wraps it.
     */
    private boolean isUniqueConstraintViolation(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException) {
                return true;
            }
            if (cause instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQLSTATE.equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    public Optional<Recipe> findByUrlAndUserId(String url, UUID userId) {
        return recipeRepository.findByUrlAndUserId(url, userId);
    }

    public List<Recipe> getRecipesForUser(UUID userId) {
        return listRecipesTimer.record(() -> recipeRepository.findByUserId(userId));
    }

    public List<Recipe> searchRecipes(String q, String mealPlanId, UUID userId) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return recipeRepository.searchByTitle(q, UUID.fromString(mealPlanId), userId);
    }

}
