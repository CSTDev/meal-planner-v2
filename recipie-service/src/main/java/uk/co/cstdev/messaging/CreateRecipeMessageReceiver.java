package uk.co.cstdev.messaging;

import java.util.logging.Logger;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.service.RecipeService;

@ApplicationScoped
public class CreateRecipeMessageReceiver {

    private static final Logger LOGGER = Logger.getLogger(CreateRecipeMessageReceiver.class.getName());

    @Inject
    RecipeService recipeService;

    @Incoming("recipes")
    public void receiveRecipe(Recipe recipe) {
        LOGGER.info("Received recipe: " + recipe);

        recipeService.addRecipe(recipe);
    }
}
