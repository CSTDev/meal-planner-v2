package uk.co.cstdev.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import uk.co.cstdev.data.MealPlanRecipeRepository;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.RecipeRepository;

/**
 * Claim-with-retry: picks a random eligible candidate and attempts to write
 * it into meal_plan_recipes. If the write loses the race for that exact
 * recipe (the (meal_plan_id, recipe_id) constraint is already taken),
 * retries with the next candidate. If candidates run out, the caller gets
 * fewer recipes than requested rather than an error.
 */
@ApplicationScoped
public class MealPlanRecipeClaimService {

    // Fetch a buffer of extra candidates beyond what's needed, so a few
    // conflicts can be absorbed by retrying without a second round trip.
    private static final int CANDIDATE_BUFFER = 10;

    @Inject
    RecipeRepository recipeRepository;

    @Inject
    MealPlanRecipeRepository mealPlanRecipeRepository;

    /**
     * Attempts to claim up to {@code count} new recipes into the plan.
     * Iterates {@code candidates} in order, skipping any that lose the
     * claim race, until {@code count} are claimed or candidates run out.
     */
    public List<Recipe> claimFromCandidates(UUID mealPlanId, List<Recipe> candidates, int count, String status) {
        List<Recipe> claimed = new ArrayList<>();
        for (Recipe candidate : candidates) {
            if (claimed.size() >= count) {
                break;
            }
            if (mealPlanRecipeRepository.claim(mealPlanId, candidate.id, status)) {
                claimed.add(candidate);
            }
        }
        return claimed;
    }

    /**
     * Claims up to {@code count} new recipes into the plan, sourcing
     * candidates from the eligible pool for this user/plan.
     */
    public List<Recipe> claimNew(UUID mealPlanId, UUID userId, int count, String status) {
        List<Recipe> candidates = recipeRepository.findEligibleCandidates(mealPlanId, userId, count + CANDIDATE_BUFFER);
        return claimFromCandidates(mealPlanId, candidates, count, status);
    }

    /**
     * Claims a single replacement recipe into the row currently occupied by
     * {@code oldRecipeId}, moving it in place. Returns empty if the
     * eligible pool is exhausted.
     */
    public Optional<Recipe> claimReplacement(UUID mealPlanId, UUID userId, UUID oldRecipeId, String status) {
        List<Recipe> candidates = recipeRepository.findEligibleCandidates(mealPlanId, userId, CANDIDATE_BUFFER);
        for (Recipe candidate : candidates) {
            if (mealPlanRecipeRepository.reclaim(mealPlanId, oldRecipeId, candidate.id, status)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
