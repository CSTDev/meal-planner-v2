package uk.co.cstdev.data.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;

import uk.co.cstdev.data.Recipe;

/**
 * Verifies that the JSON key names emitted by the scraper service (Python
 * side) for prep/cook time and canonical URL are the exact field names
 * Jackson needs to populate the {@link Recipe} entity when deserializing a
 * {@link RecipeScrapeCompleted} Kafka message. No Quarkus context or Kafka
 * broker is needed - this exercises plain Jackson deserialization only.
 */
public class RecipeScrapeCompletedJacksonTest {

    private static final String JSON = """
            {
              "@type": "scrape-completed",
              "eventId": "evt-1",
              "timestamp": "2026-07-09T00:00:00Z",
              "metadata": {
                "sourceService": "scraper",
                "correlationId": "corr-1",
                "userId": "123e4567-e89b-12d3-a456-426614174000"
              },
              "userId": "123e4567-e89b-12d3-a456-426614174000",
              "url": "https://www.gousto.co.uk/cookbooks/recipe/original-request-url",
              "recipeData": {
                "title": "Test Recipe",
                "description": "A test recipe",
                "ingredients": [],
                "instructions": [],
                "prepTimeMinutes": 15,
                "cookTimeMinutes": 25,
                "servings": 4,
                "imageUrl": "https://example.com/image.jpg",
                "url": "https://www.gousto.co.uk/cookbooks/recipe/canonical-url"
              }
            }
            """;

    @Test
    public void deserializesPrepCookTimeAndUrlOntoRecipeEntity() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        RecipeScrapeCompleted event = mapper.readValue(JSON, RecipeScrapeCompleted.class);
        Recipe recipe = event.recipeData();

        assertEquals(15, recipe.prepTimeMinutes);
        assertEquals(25, recipe.cookTimeMinutes);
        assertEquals("https://www.gousto.co.uk/cookbooks/recipe/canonical-url", recipe.url);
    }
}
