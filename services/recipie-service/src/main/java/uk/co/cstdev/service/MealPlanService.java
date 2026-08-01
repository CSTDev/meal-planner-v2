package uk.co.cstdev.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import uk.co.cstdev.data.FeedbackRepository;
import uk.co.cstdev.data.MealPlan;
import uk.co.cstdev.data.mealplan.MealPlanRequest;
import uk.co.cstdev.data.mealplan.MealPlanSummaryResponse;

@ApplicationScoped
public class MealPlanService {

    static final int MAX_RECENT_PLANS = 10;
    static final String OFFERED = "OFFERED";

    @Inject
    FeedbackRepository feedbackRepository;

    @Inject
    MealPlanRecipeClaimService claimService;

    @Transactional
    public MealPlan createMealPlan(MealPlanRequest request, String userId) {
        UUID userUuid = UUID.fromString(userId);
        MealPlan mealPlan = MealPlan.Builder.builder().userId(userUuid).createdAt(new Date())
                .recipeSource(request.recipeSource()).status("ACTIVE").build();
        mealPlan.persistAndFlush();

        // Claim the plan's initial recipes in the same request — the client
        // always loads full state via GET /api/meal-plans/{id} afterwards,
        // whether right after creation or after a refresh.
        claimService.claimNew(mealPlan.id, userUuid, request.numRecipes(), OFFERED);

        return mealPlan;
    }

    /**
     * Returns up to {@value MAX_RECENT_PLANS} of the user's most recent meal
     * plans, newest first, excluding plans with no effectively-accepted
     * recipes (latest-interaction-wins).
     */
    public List<MealPlanSummaryResponse> getRecentMealPlans(UUID userId) {
        // id is a deterministic tie-break for plans sharing the same createdAt
        List<MealPlan> plans = MealPlan.list("userId = ?1 ORDER BY createdAt DESC, id DESC", userId);

        List<MealPlanSummaryResponse> summaries = new ArrayList<>();
        for (MealPlan plan : plans) {
            int acceptedRecipeCount = feedbackRepository.findAcceptedInteractions(plan.id, userId).size();
            if (acceptedRecipeCount == 0) {
                continue;
            }
            summaries.add(new MealPlanSummaryResponse(plan.id.toString(), plan.createdAt,
                    plan.recipeSource, acceptedRecipeCount));
            if (summaries.size() == MAX_RECENT_PLANS) {
                break;
            }
        }
        return summaries;
    }
}
