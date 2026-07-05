package uk.co.cstdev.service;

import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.RecipeRepository;

@ApplicationScoped
public class RecipeService {

    @Inject
    public RecipeRepository recipeRepository;

    @Inject
    MeterRegistry meterRegistry;

    private Counter recipesAddedCounter;
    private Timer listRecipesTimer;

    @jakarta.annotation.PostConstruct
    void initMetrics() {
        recipesAddedCounter = Counter.builder("recipes.added.total")
                .description("Total number of recipes added via scraping")
                .register(meterRegistry);
        listRecipesTimer = Timer.builder("recipes.list.duration")
                .description("Time taken to list recipes for a user")
                .register(meterRegistry);
    }

    public List<Recipe> getAllRecipes() {
        return recipeRepository.listAll();
    }

    public void addRecipe(Recipe recipe, UUID userId) {
        recipe.scrapedByUserId = userId;
        recipeRepository.persist(recipe);
        recipesAddedCounter.increment();
    }

    public List<Recipe> getRecipesForUser(UUID userId) {
        return listRecipesTimer.record(() -> recipeRepository.findByUserId(userId));
    }

    public List<Recipe> getRecommendations(int numRecipes, String mealPlanId, UUID userId) {
        return recipeRepository.findRecommendations(numRecipes, UUID.fromString(mealPlanId), userId);
    }

    public List<Recipe> searchRecipes(String q, String mealPlanId, UUID userId) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return recipeRepository.searchByTitle(q, UUID.fromString(mealPlanId), userId);
    }

}
