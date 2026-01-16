package uk.co.cstdev.data.messaging;

import java.time.Instant;

import uk.co.cstdev.data.Recipe;

public record RecipeScrapeCompleted(
                String eventId,
                Instant timestamp,
                EventMetadata metadata,
                String userId,
                String url,
                Recipe recipeData) implements RecipeEvent {
}
