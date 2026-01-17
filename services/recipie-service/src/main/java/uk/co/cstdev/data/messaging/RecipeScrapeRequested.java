package uk.co.cstdev.data.messaging;

import java.time.Instant;

public record RecipeScrapeRequested(
        String eventId,
        Instant timestamp,
        EventMetadata metadata,
        String url,
        String userId) implements RecipeEvent {
}
