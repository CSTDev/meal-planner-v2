package uk.co.cstdev.data.messaging;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RecipeScrapeRequested.class, name = "scrape-requested"),
        @JsonSubTypes.Type(value = RecipeScrapeCompleted.class, name = "scrape-completed"),
        @JsonSubTypes.Type(value = RecipeScrapeFailed.class, name = "scrape-failed")
})
public sealed interface RecipeEvent
        permits RecipeScrapeRequested,
        RecipeScrapeCompleted,
        RecipeScrapeFailed {

    String eventId();

    Instant timestamp();

    EventMetadata metadata();
}
