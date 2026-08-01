package uk.co.cstdev.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import uk.co.cstdev.data.MealPlan;
import uk.co.cstdev.data.MealPlanRecipe;
import uk.co.cstdev.data.MealPlanRecipeRepository;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.RecipeDTO;
import uk.co.cstdev.data.mealplan.MealPlanFullResponse;
import uk.co.cstdev.data.mealplan.MealPlanRecipeStateDTO;
import uk.co.cstdev.data.mealplan.MealPlanRequest;
import uk.co.cstdev.data.mealplan.MealPlanSummaryResponse;

@ApplicationScoped
public class MealPlanService {

    private static final Logger LOGGER = Logger.getLogger(MealPlanService.class);

    static final int MAX_RECENT_PLANS = 10;
    static final String OFFERED = "OFFERED";

    @Inject
    MealPlanRecipeClaimService claimService;

    @Inject
    MealPlanRecipeRepository mealPlanRecipeRepository;

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
     * plans, newest first, excluding plans with no currently-accepted
     * recipes. Accepted counts for all of the user's plans are fetched in a
     * single grouped query rather than one query per plan.
     */
    public List<MealPlanSummaryResponse> getRecentMealPlans(UUID userId) {
        // id is a deterministic tie-break for plans sharing the same createdAt
        List<MealPlan> plans = MealPlan.list("userId = ?1 ORDER BY createdAt DESC, id DESC", userId);
        Map<UUID, Long> acceptedCountsByPlan = mealPlanRecipeRepository.countAcceptedByUser(userId);

        List<MealPlanSummaryResponse> summaries = new ArrayList<>();
        for (MealPlan plan : plans) {
            long acceptedRecipeCount = acceptedCountsByPlan.getOrDefault(plan.id, 0L);
            if (acceptedRecipeCount == 0) {
                continue;
            }
            summaries.add(new MealPlanSummaryResponse(plan.id.toString(), plan.createdAt,
                    plan.recipeSource, (int) acceptedRecipeCount));
            if (summaries.size() == MAX_RECENT_PLANS) {
                break;
            }
        }
        return summaries;
    }

    /**
     * The plan's full current live state: every row in meal_plan_recipes for
     * this plan, joined to its recipe, with its status. This is what the
     * client loads on mount, whether that's right after creation or after a
     * refresh.
     */
    public MealPlanFullResponse getMealPlanState(MealPlan mealPlan) {
        List<MealPlanRecipe> rows = mealPlanRecipeRepository.listByMealPlan(mealPlan.id);

        List<UUID> recipeIds = rows.stream().map(row -> row.id.recipeId).toList();
        Map<UUID, Recipe> recipesById = recipeIds.isEmpty()
                ? Map.of()
                : Recipe.<Recipe>list("id in ?1", recipeIds)
                        .stream()
                        .collect(Collectors.toMap(recipe -> recipe.id, recipe -> recipe));

        List<MealPlanRecipeStateDTO> recipeStates = new ArrayList<>();
        for (MealPlanRecipe row : rows) {
            Recipe recipe = recipesById.get(row.id.recipeId);
            if (recipe == null) {
                // Should be impossible while the recipe_id FK holds
                LOGGER.warnf("meal_plan_recipes row references missing recipe %s in meal plan %s",
                        row.id.recipeId, mealPlan.id);
                continue;
            }
            recipeStates.add(new MealPlanRecipeStateDTO(RecipeDTO.from(recipe), row.status));
        }

        return new MealPlanFullResponse(mealPlan.id.toString(), mealPlan.userId.toString(), mealPlan.recipeSource,
                mealPlan.status, mealPlan.createdAt, recipeStates);
    }
}
