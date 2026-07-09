package uk.co.cstdev.data.mealplan;

import java.util.Date;

public record MealPlanSummaryResponse(String id, Date createdAt, String recipeSource, int acceptedRecipeCount) {

}
