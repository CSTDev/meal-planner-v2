package uk.co.cstdev.messaging;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.inject.Inject;
import uk.co.cstdev.data.Ingredient;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.messaging.EventMetadata;
import uk.co.cstdev.data.messaging.RecipeScrapeCompleted;
import uk.co.cstdev.utils.KafkaTestResourceLifecycleManager;

/**
 * Exercises the real (unmocked) production call path — Kafka message ->
 * {@link CreateRecipeMessageReceiver#receiveRecipe} (its own outer
 * {@code @Transactional}) -> {@code RecipeService.addRecipe()} (a joined,
 * non-owning transaction there, not the standalone top-level transaction
 * used in {@code RecipeServiceTest}). Two {@code RecipeScrapeCompleted}
 * messages for the same URL/user are sent through the real connector so the
 * unique-constraint swallow behaviour is verified in the same nested-
 * transaction shape it runs in in production.
 */
@QuarkusTest
@QuarkusTestResource(KafkaTestResourceLifecycleManager.class)
public class CreateRecipeMessageReceiverDuplicateHandlingTest {

    static final String USER_ID = "323e4567-e89b-12d3-a456-426614174000";
    static final String URL = "https://example.com/nested-transaction-duplicate";

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @Inject
    MeterRegistry meterRegistry;

    @BeforeEach
    @jakarta.transaction.Transactional
    void setup() {
        Recipe.deleteAll();
    }

    @AfterEach
    @jakarta.transaction.Transactional
    void cleanup() {
        Recipe.deleteAll();
    }

    @Test
    void duplicateScrapeCompletedEventsThroughTheRealConsumerPersistExactlyOneRecipeAndDoNotBreakTheConsumer() {
        double completedBefore = scrapeCompletedCount();
        String canaryUrl = "https://example.com/canary-after-duplicate-" + UUID.randomUUID();

        connector.source("recipes").send(newEvent(URL));
        connector.source("recipes").send(newEvent(URL));
        // A third, distinct message sent straight after the duplicate pair.
        // If the constraint violation from message 2 were left to surface
        // at commit time uncaught (e.g. the flush() safety net were
        // removed), it would blow up receiveRecipe()'s transaction after
        // the fact and, in a real Kafka consumer, would either redeliver
        // message 2 forever or take the whole channel's subscription down
        // with it — either way, this canary would never get processed.
        connector.source("recipes").send(newEvent(canaryUrl));

        // All three messages must be fully processed (no exception escaping
        // receiveRecipe(), which would stop the counter increment that
        // follows addRecipe() in the switch branch, and would otherwise
        // surface as a failed/retried Kafka message rather than an ack).
        await().untilAsserted(() -> assertEquals(completedBefore + 3, scrapeCompletedCount()));

        assertEquals(1, countRecipesForUrlAndUser(URL), "The duplicate pair must collapse to a single recipe");
        assertEquals(1, countRecipesForUrlAndUser(canaryUrl),
                "The canary sent after the duplicate pair must still be persisted, proving the consumer survived");
    }

    private double scrapeCompletedCount() {
        return meterRegistry.counter("scrape.events.completed.total").count();
    }

    private long countRecipesForUrlAndUser(String url) {
        return QuarkusTransaction.requiringNew()
                .call(() -> Recipe.find("url = ?1 and scrapedByUserId = ?2", url, UUID.fromString(USER_ID)).count());
    }

    private RecipeScrapeCompleted newEvent(String url) {
        Recipe recipe = Recipe.Builder.recipe()
                .title("Nested Transaction Duplicate Recipe")
                .url(url)
                .ingredients(List.of(new Ingredient("ingredient1", 0, null)))
                .instructions(List.of("Mix and cook."))
                .prepTime(10)
                .cookTime(20)
                .build();
        return new RecipeScrapeCompleted(
                UUID.randomUUID().toString(),
                Instant.now(),
                new EventMetadata("test-source", "corr-dup", USER_ID),
                USER_ID,
                url,
                recipe);
    }
}
