package uk.co.cstdev.data.mealplan;

/**
 * A single recipe's contribution to an aggregated shopping list ingredient.
 */
public record ShoppingListBreakdownEntry(String recipeId, String recipeTitle, Float quantity, String unit) {
}
