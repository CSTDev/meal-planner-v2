package uk.co.cstdev.data.mealplan;

/**
 * A single mergeable quantity for an ingredient, e.g. { "quantity": 680, "unit": "g" }.
 */
public record ShoppingListAmount(Float quantity, String unit) {
}
