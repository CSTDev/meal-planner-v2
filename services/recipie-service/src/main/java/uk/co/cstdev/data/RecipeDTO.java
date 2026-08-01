package uk.co.cstdev.data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class RecipeDTO {
    public UUID id;
    public String url;
    public String title;
    public String description;
    public List<Ingredient> ingredients;
    public List<String> instructions;
    public int prepTimeMinutes;
    public int cookTimeMinutes;
    public int servings;
    public List<String> tags;
    public String imageUrl;
    public Date createdAt;
    public Date scrapedAt;

    public static RecipeDTO from(Recipe recipe) {
        RecipeDTO dto = new RecipeDTO();
        dto.id = recipe.id;
        dto.url = recipe.url;
        dto.title = recipe.title;
        dto.description = recipe.description;
        // Copy lazily-loaded collections into plain ArrayLists here, while
        // still inside an active persistence context: callers that build
        // this DTO inside an explicit @Transactional method (whose session
        // closes when the method returns, before Jackson serializes the
        // response) would otherwise hit a LazyInitializationException.
        dto.ingredients = recipe.ingredients == null ? null : new ArrayList<>(recipe.ingredients);
        dto.instructions = recipe.instructions == null ? null : new ArrayList<>(recipe.instructions);
        dto.prepTimeMinutes = recipe.prepTimeMinutes;
        dto.cookTimeMinutes = recipe.cookTimeMinutes;
        dto.servings = recipe.servings;
        dto.tags = recipe.tags;
        dto.imageUrl = recipe.imageUrl;
        dto.createdAt = recipe.createdAt;
        dto.scrapedAt = recipe.scrapedAt;
        return dto;
    }
}
