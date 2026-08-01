package uk.co.cstdev.service;

import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import uk.co.cstdev.data.FeedbackAction;
import uk.co.cstdev.data.FeedbackRepository;
import uk.co.cstdev.data.MealPlanRecipeRepository;
import uk.co.cstdev.data.MealPlanRecipeStatus;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.RecipeDTO;
import uk.co.cstdev.data.mealplan.FeedbackResponse;

@ApplicationScoped
public class FeedbackService {

    @Inject
    private FeedbackRepository feedbackRepository;

    @Inject
    MealPlanRecipeRepository mealPlanRecipeRepository;

    @Inject
    MealPlanRecipeClaimService claimService;

    public void processFeedback(UUID userId, UUID recipeId, UUID mealPlanId, FeedbackAction action) {
        feedbackRepository.saveFeedback(userId, recipeId, mealPlanId, action);
    }

    /**
     * Submits feedback for the slot currently occupied by {@code recipeId}
     * in this plan. Looks the row up by (mealPlanId, recipeId) — that pair
     * is always enough to find the right row, no slot identifier needed
     * from the client.
     *
     * @throws StaleFeedbackException if no such row exists — a stale or
     *                                 duplicate submission (e.g. a
     *                                 double-click, or a retried request
     *                                 after a dropped response) that must
     *                                 not silently no-op.
     */
    @Transactional
    public FeedbackResponse submitFeedback(UUID userId, UUID mealPlanId, UUID recipeId, FeedbackAction action,
            UUID replacementRecipeId) {
        mealPlanRecipeRepository.findByPlanAndRecipe(mealPlanId, recipeId)
                .orElseThrow(() -> new StaleFeedbackException(
                        "No pending/offered row for meal plan %s, recipe %s".formatted(mealPlanId, recipeId)));

        if (action == FeedbackAction.ACCEPTED) {
            return accept(userId, mealPlanId, recipeId);
        }

        if (replacementRecipeId != null) {
            return replaceWithSpecific(userId, mealPlanId, recipeId, replacementRecipeId);
        }

        return rejectWithRandomReplacement(userId, mealPlanId, recipeId);
    }

    private FeedbackResponse accept(UUID userId, UUID mealPlanId, UUID recipeId) {
        feedbackRepository.saveFeedback(userId, recipeId, mealPlanId, FeedbackAction.ACCEPTED);
        boolean updated = mealPlanRecipeRepository.updateStatus(mealPlanId, recipeId,
                MealPlanRecipeStatus.ACCEPTED.name());
        if (!updated) {
            // Backstop: the row existed at the lookup in submitFeedback but
            // is gone by the time we write — e.g. a concurrent reject
            // deleted it (pool exhaustion). Must not silently report 200
            // ACCEPTED with no live row behind it.
            throw new StaleFeedbackException(
                    "No pending/offered row for meal plan %s, recipe %s".formatted(mealPlanId, recipeId));
        }
        return new FeedbackResponse(RecipeDTO.from((Recipe) Recipe.findById(recipeId)),
                MealPlanRecipeStatus.ACCEPTED.name());
    }

    private FeedbackResponse replaceWithSpecific(UUID userId, UUID mealPlanId, UUID oldRecipeId,
            UUID replacementRecipeId) {
        feedbackRepository.saveFeedback(userId, oldRecipeId, mealPlanId, FeedbackAction.REJECTED);
        feedbackRepository.saveFeedback(userId, replacementRecipeId, mealPlanId, FeedbackAction.ACCEPTED);

        boolean moved = mealPlanRecipeRepository.reclaim(mealPlanId, oldRecipeId, replacementRecipeId,
                MealPlanRecipeStatus.ACCEPTED.name());
        if (!moved) {
            // Backstop: the unique constraint means the replacement is
            // already claimed elsewhere in this plan, even though the
            // search endpoint should have excluded it.
            throw new StaleFeedbackException(
                    "Replacement recipe %s is already claimed in meal plan %s".formatted(replacementRecipeId,
                            mealPlanId));
        }
        return new FeedbackResponse(RecipeDTO.from((Recipe) Recipe.findById(replacementRecipeId)),
                MealPlanRecipeStatus.ACCEPTED.name());
    }

    private FeedbackResponse rejectWithRandomReplacement(UUID userId, UUID mealPlanId, UUID recipeId) {
        feedbackRepository.saveFeedback(userId, recipeId, mealPlanId, FeedbackAction.REJECTED);

        Optional<Recipe> replacement = claimService.claimReplacement(mealPlanId, userId, recipeId,
                MealPlanRecipeStatus.OFFERED.name());

        if (replacement.isPresent()) {
            return new FeedbackResponse(RecipeDTO.from(replacement.get()), MealPlanRecipeStatus.OFFERED.name());
        }

        // Pool exhausted — nothing left to offer in this slot.
        mealPlanRecipeRepository.deleteByPlanAndRecipe(mealPlanId, recipeId);
        return new FeedbackResponse(null, "REMOVED");
    }
}
