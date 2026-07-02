package uk.co.cstdev.data.mealplan;

import java.util.List;

/**
 * A single normalised ingredient line within a shopping list, with its aggregated
 * amounts (one entry per mergeable unit group) and the per-recipe breakdown.
 */
public record ShoppingListIngredient(String name, List<ShoppingListAmount> amounts,
        List<ShoppingListBreakdownEntry> breakdown) {
}
