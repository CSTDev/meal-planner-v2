package uk.co.cstdev.data.mealplan;

import uk.co.cstdev.data.RecipeDTO;

/**
 * The result of submitting feedback for one slot. {@code recipe} is the
 * recipe now occupying the slot (unchanged on accept, the replacement on
 * reject/replace), or {@code null} when a reject exhausted the eligible
 * pool and the slot was removed entirely.
 */
public record FeedbackResponse(RecipeDTO recipe, String status) {

}
