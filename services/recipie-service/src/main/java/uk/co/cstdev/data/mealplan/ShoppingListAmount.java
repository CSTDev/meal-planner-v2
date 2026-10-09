package uk.co.cstdev.data.mealplan;

import java.util.List;

/**
 * A single mergeable quantity for an ingredient, e.g. { "quantity": 680, "unit": "g" },
 * with the split behind the total in {@code parts} (largest first). {@code parts} is
 * empty unless two or more contributions feed the total.
 */
public record ShoppingListAmount(Float quantity, String unit, List<ShoppingListPart> parts) {
}
