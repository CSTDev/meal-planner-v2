package uk.co.cstdev.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import uk.co.cstdev.data.Ingredient;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.UserRecipeInteraction;
import uk.co.cstdev.data.mealplan.ShoppingListAmount;
import uk.co.cstdev.data.mealplan.ShoppingListBreakdownEntry;
import uk.co.cstdev.data.mealplan.ShoppingListIngredient;
import uk.co.cstdev.data.mealplan.ShoppingListResponse;

@ApplicationScoped
public class ShoppingListService {

    @Inject
    EntityManager em;

    public ShoppingListResponse buildShoppingList(UUID mealPlanId, UUID userId) {
        // Latest-interaction-wins: include a recipe only when its most-recent
        // interaction for this (user, recipe, meal_plan) is ACCEPTED, i.e. no
        // REJECTED row exists that is newer than the ACCEPTED row.
        @SuppressWarnings("unchecked")
        List<UserRecipeInteraction> acceptedInteractions = em.createQuery(
                "SELECT i FROM UserRecipeInteraction i " +
                "WHERE i.mealPlanId = :mealPlanId AND i.userId = :userId AND i.interactionType = 'ACCEPTED' " +
                "AND NOT EXISTS (" +
                "  SELECT r FROM UserRecipeInteraction r " +
                "  WHERE r.mealPlanId = :mealPlanId AND r.userId = :userId AND r.recipeId = i.recipeId " +
                "  AND r.interactionType = 'REJECTED' AND r.interactionAt > i.interactionAt" +
                ")",
                UserRecipeInteraction.class)
                .setParameter("mealPlanId", mealPlanId)
                .setParameter("userId", userId)
                .getResultList();

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

        String displayName = chooseDisplayName(contributions);
        if (displayName == null) {
            // Defensive fallback: should only happen if every contribution's raw
            // ingredient name was blank, which normalize() would already have
            // filtered out upstream.
            displayName = normalizedName;
        }
        return new ShoppingListIngredient(displayName, amounts, breakdown);
    }

    /**
     * Picks the most natural-reading name to display for a merged ingredient
     * line: the most frequent original wording (cleaned, but not reordered or
     * singularised) among the contributing recipes, tie-broken by whichever
     * variant was first encountered. Returns null if no display candidate
     * could be derived from any contribution.
     */
    private String chooseDisplayName(List<IngredientContribution> contributions) {
        Map<String, Integer> countsByLowerCase = new LinkedHashMap<>();
        Map<String, String> representativeByLowerCase = new LinkedHashMap<>();

        for (IngredientContribution contribution : contributions) {
            String cleaned = IngredientNameNormalizer.cleanForDisplay(contribution.ingredient().name);
            if (cleaned.isEmpty()) {
                continue;
            }
            String key = cleaned.toLowerCase();
            representativeByLowerCase.putIfAbsent(key, cleaned);
            countsByLowerCase.merge(key, 1, Integer::sum);
        }

        String bestKey = null;
        int bestCount = -1;
        for (Map.Entry<String, Integer> entry : countsByLowerCase.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestKey = entry.getKey();
            }
        }

        return bestKey == null ? null : representativeByLowerCase.get(bestKey);
    }

    private record IngredientContribution(Recipe recipe, Ingredient ingredient) {
    }
}
