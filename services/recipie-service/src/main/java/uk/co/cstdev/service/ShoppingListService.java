package uk.co.cstdev.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import uk.co.cstdev.data.Ingredient;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.UserRecipeInteraction;
import uk.co.cstdev.data.mealplan.ShoppingListAmount;
import uk.co.cstdev.data.mealplan.ShoppingListBreakdownEntry;
import uk.co.cstdev.data.mealplan.ShoppingListIngredient;
import uk.co.cstdev.data.mealplan.ShoppingListResponse;

@ApplicationScoped
public class ShoppingListService {

    public ShoppingListResponse buildShoppingList(UUID mealPlanId, UUID userId) {
        List<UserRecipeInteraction> acceptedInteractions = UserRecipeInteraction
                .list("mealPlanId = ?1 and userId = ?2 and interactionType = ?3", mealPlanId, userId, "ACCEPTED");

        // Preserve first-seen order of normalised ingredient names.
        Map<String, List<IngredientContribution>> contributionsByName = new LinkedHashMap<>();

        for (UserRecipeInteraction interaction : acceptedInteractions) {
            Recipe recipe = Recipe.findById(interaction.recipeId);
            if (recipe == null || recipe.ingredients == null) {
                continue;
            }
            for (Ingredient ingredient : recipe.ingredients) {
                String normalizedName = IngredientNameNormalizer.normalize(ingredient.name);
                if (normalizedName.isEmpty()) {
                    continue;
                }
                contributionsByName
                        .computeIfAbsent(normalizedName, k -> new ArrayList<>())
                        .add(new IngredientContribution(recipe, ingredient));
            }
        }

        List<ShoppingListIngredient> ingredients = new ArrayList<>();
        for (Map.Entry<String, List<IngredientContribution>> entry : contributionsByName.entrySet()) {
            ingredients.add(buildIngredientLine(entry.getKey(), entry.getValue()));
        }

        return new ShoppingListResponse(ingredients);
    }

    private ShoppingListIngredient buildIngredientLine(String normalizedName,
            List<IngredientContribution> contributions) {

        List<ShoppingListBreakdownEntry> breakdown = new ArrayList<>();
        // Group quantifiable contributions by "mergeable unit group" - either a
        // matching normalised unit string, or a shared fixed-ratio SI family.
        Map<String, Double> totalsByGroup = new LinkedHashMap<>();
        Map<String, String> displayUnitByGroup = new LinkedHashMap<>();

        for (IngredientContribution contribution : contributions) {
            Ingredient ingredient = contribution.ingredient();
            Recipe recipe = contribution.recipe();

            boolean hasQuantity = ingredient.quantity > 0;
            String rawUnit = ingredient.unit;
            String normalizedUnit = UnitConverter.normalizeUnit(rawUnit);

            breakdown.add(new ShoppingListBreakdownEntry(
                    recipe.id.toString(),
                    recipe.title,
                    hasQuantity ? ingredient.quantity : null,
                    hasQuantity && !normalizedUnit.isEmpty() ? normalizedUnit : null));

            if (!hasQuantity) {
                continue;
            }

            UnitConverter.Family family = UnitConverter.familyFor(normalizedUnit);
            String groupKey;
            String displayUnit;
            double amountInBaseUnit;
            if (family != null) {
                groupKey = "family:" + family.name();
                displayUnit = UnitConverter.baseUnitSymbolFor(family.name());
                amountInBaseUnit = ingredient.quantity * family.ratioToBaseUnit();
            } else {
                groupKey = "unit:" + normalizedUnit;
                displayUnit = normalizedUnit.isEmpty() ? null : normalizedUnit;
                amountInBaseUnit = ingredient.quantity;
            }

            totalsByGroup.merge(groupKey, amountInBaseUnit, Double::sum);
            displayUnitByGroup.putIfAbsent(groupKey, displayUnit);
        }

        List<ShoppingListAmount> amounts = new ArrayList<>();
        for (Map.Entry<String, Double> group : totalsByGroup.entrySet()) {
            String unit = displayUnitByGroup.get(group.getKey());
            amounts.add(new ShoppingListAmount((float) (double) group.getValue(), unit));
        }

        return new ShoppingListIngredient(normalizedName, amounts, breakdown);
    }

    private record IngredientContribution(Recipe recipe, Ingredient ingredient) {
    }
}
