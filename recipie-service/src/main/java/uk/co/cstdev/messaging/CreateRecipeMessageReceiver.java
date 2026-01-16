package uk.co.cstdev.messaging;

import java.util.logging.Logger;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import uk.co.cstdev.data.messaging.RecipeEvent;
import uk.co.cstdev.data.messaging.RecipeScrapeCompleted;
import uk.co.cstdev.service.RecipeService;

@ApplicationScoped
public class CreateRecipeMessageReceiver {

    private static final Logger LOGGER = Logger.getLogger(CreateRecipeMessageReceiver.class.getName());

    @Inject
    RecipeService recipeService;

    @Incoming("recipes")
    public void receiveRecipe(RecipeEvent recipeEvent) {
        LOGGER.info("Received recipe event: " + recipeEvent);

        switch (recipeEvent) {
            case RecipeScrapeCompleted req -> {
                recipeService.addRecipe(req.recipeData());
            }
            default -> LOGGER.warning("Unhandled recipe event type: " + recipeEvent.getClass().getName());
        }

    }
}
