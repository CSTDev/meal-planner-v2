package uk.co.cstdev.service;

import java.util.List;

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

    public void addRecipe(Recipe recipe) {
        recipeRepository.persist(recipe);
    }

}
