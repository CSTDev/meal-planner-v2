package uk.co.cstdev.service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.RecipeRepository;

@ApplicationScoped
public class RecipeService {

    @Inject
    public RecipeRepository recipeRepository;

    // Service methods to manage recipes would go here

    public List<Recipe> getAllRecipes() {
        return recipeRepository.listAll();
    }

    public void addRecipe(Recipe recipe, UUID userId) {
        recipe.scrapedByUserId = userId;
        recipe.createdAt = new Date();
        recipe.scrapedAt = new Date();
        recipeRepository.persist(recipe);
    }

    public List<Recipe> getRecipesForUser(UUID userId) {
        return recipeRepository.findByUserId(userId);
    }

    public List<Recipe> getRecommendations(int numRecipes, String mealPlanId, UUID userId) {
        return recipeRepository.findRecommendations(numRecipes, UUID.fromString(mealPlanId), userId);
    }

}
