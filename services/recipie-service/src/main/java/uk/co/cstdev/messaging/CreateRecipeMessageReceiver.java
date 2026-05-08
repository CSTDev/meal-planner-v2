package uk.co.cstdev.messaging;

import java.util.UUID;
import java.util.logging.Logger;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import uk.co.cstdev.data.messaging.RecipeEvent;
import uk.co.cstdev.data.messaging.RecipeScrapeCompleted;
import uk.co.cstdev.data.messaging.RecipeScrapeFailed;
import uk.co.cstdev.service.RecipeService;

@ApplicationScoped
public class CreateRecipeMessageReceiver {

    private static final Logger LOGGER = Logger.getLogger(CreateRecipeMessageReceiver.class.getName());

    @Inject
    RecipeService recipeService;

    @Inject
    MeterRegistry meterRegistry;

    private Counter scrapeCompletedCounter;
    private Counter scrapeFailedCounter;

    @PostConstruct
    void initMetrics() {
        scrapeCompletedCounter = Counter.builder("scrape.events.completed.total")
                .description("Total number of successful scrape-completed events processed")
                .register(meterRegistry);
        scrapeFailedCounter = Counter.builder("scrape.events.failed.total")
                .description("Total number of scrape-failed events received")
                .register(meterRegistry);
    }

    @Incoming("recipes")
    @Transactional
    public void receiveRecipe(RecipeEvent recipeEvent) {
        LOGGER.info("Received recipe event: " + recipeEvent);

        switch (recipeEvent) {
            case RecipeScrapeCompleted req -> {
                recipeService.addRecipe(req.recipeData(), UUID.fromString(req.userId()));
                scrapeCompletedCounter.increment();
            }
            case RecipeScrapeFailed req -> {
                LOGGER.warning("Scrape failed for URL: " + req.url() + " - " + req.errorMessage());
                scrapeFailedCounter.increment();
            }
            default -> LOGGER.warning("Unhandled recipe event type: " + recipeEvent.getClass().getName());
        }

    }
}
