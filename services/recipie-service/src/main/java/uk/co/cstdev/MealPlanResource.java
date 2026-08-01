package uk.co.cstdev;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import uk.co.cstdev.data.MealPlan;
import uk.co.cstdev.data.MealPlanRecipeRepository;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.RecipeDTO;
import uk.co.cstdev.data.RecipeFeedback;
import uk.co.cstdev.data.mealplan.FeedbackResponse;
import uk.co.cstdev.data.mealplan.MealPlanRequest;
import uk.co.cstdev.data.mealplan.MealPlanResponse;
import uk.co.cstdev.data.mealplan.MealPlanSummaryResponse;
import uk.co.cstdev.data.mealplan.ShoppingListResponse;
import uk.co.cstdev.service.FeedbackService;
import uk.co.cstdev.service.MealPlanService;
import uk.co.cstdev.service.RecipeService;
import uk.co.cstdev.service.ShoppingListService;
import uk.co.cstdev.service.StaleFeedbackException;

@Path("/api/meal-plans")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Meal Plans", description = "Operations for creating meal plans and retrieving recommendations")
public class MealPlanResource {

        private static final Logger LOGGER = Logger.getLogger(MealPlanResource.class);

        @Inject
        public RecipeService recipeService;

        @Inject
        public FeedbackService feedbackService;

        @Inject
        MealPlanService mealPlanService;

        @Inject
        ShoppingListService shoppingListService;

        @Inject
        MealPlanRecipeRepository mealPlanRecipeRepository;

        @Inject
        JsonWebToken jwt;

        @POST
        @Operation(summary = "Create a meal plan", description = "Creates a new meal plan for the authenticated user")
        @APIResponse(responseCode = "200", description = "Meal plan created")
        @APIResponse(responseCode = "401", description = "Unauthorized")
        public Response createMealPlan(MealPlanRequest request) {
                String userId = jwt.getSubject();

                MealPlan mealPlan = mealPlanService.createMealPlan(request, userId);
                MealPlanResponse response = new MealPlanResponse(mealPlan.id.toString(), userId,
                                mealPlan.recipeSource,
                                mealPlan.status);
                return Response.ok(response)
                                .build();
        }

        @GET
        @Operation(summary = "List recent meal plans", description = "Returns up to 10 of the authenticated user's most recent meal plans, newest first, excluding plans with no accepted recipes")
        @APIResponse(responseCode = "200", description = "List of recent meal plans")
        @APIResponse(responseCode = "401", description = "Unauthorized")
        public Response listMealPlans() {
                String userId = jwt.getSubject();
                List<MealPlanSummaryResponse> plans = mealPlanService.getRecentMealPlans(UUID.fromString(userId));
                return Response.ok(plans).build();
        }

        // TODO should this be here or it a FeedbackResource?
        @POST
        @Path("/{mealPlanId}/feedback")
        @Operation(summary = "Submit recipe feedback", description = "Records an accept/reject decision for a recipe within a meal plan, and where relevant returns the recipe now occupying that slot")
        @APIResponse(responseCode = "200", description = "Feedback recorded")
        @APIResponse(responseCode = "401", description = "Unauthorized")
        @APIResponse(responseCode = "409", description = "The recipe is no longer pending/offered in this meal plan")
        public Response submitFeedback(
                        @PathParam("mealPlanId") String mealPlanId,
                        RecipeFeedback request) {

                LOGGER.infof("Received feedback for meal plan ID: %s, recipe ID: %s, action: %s",
                                mealPlanId, request.recipe_id().toString(), request.action());

                UUID mealPlanUuid = UUID.fromString(mealPlanId);

                UUID userId = UUID.fromString(jwt.getSubject());

                try {
                        FeedbackResponse response = feedbackService.submitFeedback(userId, mealPlanUuid,
                                        request.recipe_id(), request.action(), request.replacement_recipe_id());
                        return Response.ok(response).build();
                } catch (StaleFeedbackException e) {
                        return Response.status(Response.Status.CONFLICT).build();
                }
        }

        @GET
        @Path("/{id}/recipe-search")
        @Operation(summary = "Search recipes for a meal plan", description = "Returns recipes whose title matches the query, excluding already-accepted recipes in this plan")
        @APIResponse(responseCode = "200", description = "List of matching recipes")
        @APIResponse(responseCode = "401", description = "Unauthorized")
        @APIResponse(responseCode = "403", description = "Meal plan does not belong to the authenticated user")
        @APIResponse(responseCode = "404", description = "Meal plan not found")
        public Response searchRecipes(@PathParam("id") String id,
                        @QueryParam("q") String q) {
                String userId = jwt.getSubject();

                MealPlan mealPlan;
                try {
                        mealPlan = MealPlan.findById(UUID.fromString(id));
                } catch (IllegalArgumentException e) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                }

                if (mealPlan == null) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                }

                if (mealPlan.userId == null || !mealPlan.userId.toString().equals(userId)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                }

                if (q == null || q.isBlank()) {
                        return Response.ok(List.of()).build();
                }

                List<Recipe> results = recipeService.searchRecipes(q, id, UUID.fromString(userId));
                List<RecipeDTO> dtos = results.stream()
                                .map(RecipeDTO::from)
                                .toList();
                return Response.ok(dtos).build();
        }

        @GET
        @Path("/{id}")
        @Operation(summary = "Get a meal plan's full current state", description = "Returns every recipe currently offered or accepted in this meal plan, with its status")
        @APIResponse(responseCode = "200", description = "Meal plan state")
        @APIResponse(responseCode = "401", description = "Unauthorized")
        @APIResponse(responseCode = "403", description = "Meal plan does not belong to the authenticated user")
        @APIResponse(responseCode = "404", description = "Meal plan not found")
        public Response getMealPlan(@PathParam("id") String id) {
                String userId = jwt.getSubject();

                MealPlan mealPlan;
                try {
                        mealPlan = MealPlan.findById(UUID.fromString(id));
                } catch (IllegalArgumentException e) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                }

                if (mealPlan == null) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                }

                if (mealPlan.userId == null || !mealPlan.userId.toString().equals(userId)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                }

                return Response.ok(mealPlanService.getMealPlanState(mealPlan)).build();
        }

        @GET
        @Path("/{id}/shopping-list")
        @Operation(summary = "Get shopping list for a meal plan", description = "Aggregates ingredients across all accepted recipes in the meal plan")
        @APIResponse(responseCode = "200", description = "Aggregated shopping list")
        @APIResponse(responseCode = "401", description = "Unauthorized")
        @APIResponse(responseCode = "403", description = "Meal plan does not belong to the authenticated user")
        @APIResponse(responseCode = "404", description = "Meal plan not found")
        public Response getShoppingList(@PathParam("id") String id) {
                String userId = jwt.getSubject();

                MealPlan mealPlan;
                try {
                        mealPlan = MealPlan.findById(UUID.fromString(id));
                } catch (IllegalArgumentException e) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                }

                if (mealPlan == null) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                }

                if (mealPlan.userId == null || !mealPlan.userId.toString().equals(userId)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                }

                ShoppingListResponse response = shoppingListService.buildShoppingList(mealPlan.id);
                return Response.ok(response).build();
        }

        @GET
        @Path("/{id}/accepted-recipes")
        @Operation(summary = "Get accepted recipes for a meal plan", description = "Returns the recipes currently accepted into the meal plan, sorted by title")
        @APIResponse(responseCode = "200", description = "List of accepted recipes")
        @APIResponse(responseCode = "401", description = "Unauthorized")
        @APIResponse(responseCode = "403", description = "Meal plan does not belong to the authenticated user")
        @APIResponse(responseCode = "404", description = "Meal plan not found")
        public Response getAcceptedRecipes(@PathParam("id") String id) {
                String userId = jwt.getSubject();

                MealPlan mealPlan;
                try {
                        mealPlan = MealPlan.findById(UUID.fromString(id));
                } catch (IllegalArgumentException e) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                }

                if (mealPlan == null) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                }

                if (mealPlan.userId == null || !mealPlan.userId.toString().equals(userId)) {
                        return Response.status(Response.Status.FORBIDDEN).build();
                }

                List<UUID> recipeIds = mealPlanRecipeRepository.listAcceptedRecipeIds(mealPlan.id);

                // Batch the recipe lookup rather than one findById per recipe id
                Map<UUID, Recipe> recipesById = recipeIds.isEmpty()
                                ? Map.of()
                                : Recipe.<Recipe>list("id in ?1", recipeIds)
                                                .stream()
                                                .collect(Collectors.toMap(recipe -> recipe.id,
                                                                recipe -> recipe));

                List<RecipeDTO> dtos = new ArrayList<>();
                for (UUID recipeId : recipeIds) {
                        Recipe recipe = recipesById.get(recipeId);
                        if (recipe == null) {
                                // Should be impossible while the recipe_id FK holds
                                LOGGER.warnf("meal_plan_recipes row references missing recipe %s in meal plan %s",
                                                recipeId, mealPlan.id);
                                continue;
                        }
                        dtos.add(RecipeDTO.from(recipe));
                }
                // meal_plan_recipes carries no timestamp, so there's no
                // "most recently accepted" ordering to derive any more —
                // sort by title for a stable, predictable order instead.
                dtos.sort(Comparator.comparing(dto -> dto.title,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
                return Response.ok(dtos).build();
        }

}
