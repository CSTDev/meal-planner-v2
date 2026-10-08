package uk.co.cstdev.data.mealplan;

/**
 * One distinct contribution to a shopping list total, in its original unit,
 * with the number of times that exact quantity and unit occurred.
 */
public record ShoppingListPart(Float quantity, String unit, int count) {
}
