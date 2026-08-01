package uk.co.cstdev.data.mealplan;

import java.util.Date;
import java.util.List;

/**
 * The plan's full current live state: every row in meal_plan_recipes for
 * this plan, joined to its recipe, with its status. This is what makes the
 * plan survive a refresh — the client always loads state from here, whether
 * that's right after creation or after a reload.
 */
public record MealPlanFullResponse(String id, String userId, String recipeSource, String status, Date createdAt,
        List<MealPlanRecipeStateDTO> recipes) {

}
