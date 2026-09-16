package uk.co.cstdev.data.mealplan;

import java.util.UUID;

/**
 * Request body for {@code POST /{id}/recipes} — adds a recipe directly to a
 * meal plan as ACCEPTED, bypassing the offer/feedback flow entirely (e.g.
 * the trailing "+" tile's manual search).
 */
public record AddRecipeRequest(UUID recipeId) {

}
