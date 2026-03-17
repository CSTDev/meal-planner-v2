package uk.co.cstdev.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.inject.Inject;
import uk.co.cstdev.data.Ingredient;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.messaging.EventMetadata;
import uk.co.cstdev.data.messaging.RecipeScrapeCompleted;
import uk.co.cstdev.data.messaging.RecipeScrapeFailed;
import uk.co.cstdev.service.RecipeService;
import uk.co.cstdev.utils.KafkaTestResourceLifecycleManager;

@QuarkusTest
@QuarkusTestResource(KafkaTestResourceLifecycleManager.class)
public class CreateRecipeMessageReceiverTest {

    static final String USER_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @InjectMock
    RecipeService recipeService;

    @Test
    public void testScrapeCompletedCallsAddRecipeWithUserId() {
        Recipe recipe = Recipe.Builder.recipe()
                .title("Test Recipe")
                .description("A test recipe")
                .ingredients(List.of(new Ingredient("ingredient1", 0, null), new Ingredient("ingredient2", 0, null)))
                .instructions(List.of("Mix ingredients and cook."))
                .prepTime(30)
                .cookTime(45)
                .build();
        RecipeScrapeCompleted event = new RecipeScrapeCompleted(
                "12345",
                Instant.parse("2024-06-01T12:00:00Z"),
                new EventMetadata("test-source", "corr-123", USER_ID),
                USER_ID,
                "https://example.com/recipe",
                recipe);

        connector.source("recipes").send(event);

        await().untilAsserted(() -> verify(recipeService).addRecipe(any(), eq(UUID.fromString(USER_ID))));
    }

    @Test
    public void testScrapeFailedDoesNotCallAddRecipe() {
        RecipeScrapeFailed event = new RecipeScrapeFailed(
                "12345",
                Instant.parse("2024-06-01T12:00:00Z"),
                new EventMetadata("test-source", "corr-123", USER_ID),
                "https://example.com/recipe",
                USER_ID,
                "Connection refused");

        connector.source("recipes").send(event);

        await().untilAsserted(() -> verify(recipeService, never()).addRecipe(any(), any()));
    }

}
