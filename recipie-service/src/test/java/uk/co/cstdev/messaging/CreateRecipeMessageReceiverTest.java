package uk.co.cstdev.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.awaitility.Awaitility.await;

import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.inject.Inject;
import uk.co.cstdev.service.RecipeService;
import uk.co.cstdev.utils.KafkaTestResourceLifecycleManager;

@QuarkusTest
@QuarkusTestResource(KafkaTestResourceLifecycleManager.class)
public class CreateRecipeMessageReceiverTest {

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @InjectMock
    RecipeService recipeService;

    @Test
    public void testReceiveRecipeMessage() {
        // Given
        String testRecipeJson = "{\"title\":\"Test Recipe\",\"description\":\"A test recipe description.\"}";

        // When
        connector.source("recipes").send(testRecipeJson);

        // Then
        await().untilAsserted(() -> verify(recipeService).addRecipe(any()));
    }

}
