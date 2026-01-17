package uk.co.cstdev.data.messaging;

import java.time.Instant;

public record RecipeScrapeFailed(
        String eventId,
        Instant timestamp,
        EventMetadata metadata,
        String url,
        String userId,
        String errorMessage) implements RecipeEvent {
}
