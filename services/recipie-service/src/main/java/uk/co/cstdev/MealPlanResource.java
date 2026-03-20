package uk.co.cstdev;

import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

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
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.RecipeDTO;
import uk.co.cstdev.data.RecipeFeedback;
import uk.co.cstdev.data.mealplan.MealPlanRequest;
import uk.co.cstdev.data.mealplan.MealPlanResponse;
import uk.co.cstdev.service.FeedbackService;
import uk.co.cstdev.service.MealPlanService;
import uk.co.cstdev.service.RecipeService;

@Path("/api/meal-plans")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class MealPlanResource {

        private static final Logger LOGGER = Logger.getLogger(MealPlanResource.class);

        @Inject
        public RecipeService recipeService;

        @Inject
        public FeedbackService feedbackService;

        @Inject
        MealPlanService mealPlanService;

        @Inject
        JsonWebToken jwt;

        @POST
        public Response createMealPlan(MealPlanRequest request) {
                String userId = jwt.getSubject();

                MealPlan mealPlan = mealPlanService.createMealPlan(request, userId);
                MealPlanResponse response = new MealPlanResponse(mealPlan.id.toString(), userId,
                                mealPlan.recipeSource,
                                mealPlan.status);
                return Response.ok(response)
                                .build();
        }

        // TODO should this be here or it a RecommendationResource?
        @GET
        @Path("/{id}/recommendations")
        public Response getMealPlanRecommendations(@PathParam("id") String id,
                        @QueryParam("num_recipes") int numRecipes) {
                String userId = jwt.getSubject();
                LOGGER.infof("Fetching %d recommendations for user=%s meal_plan_id=%s", numRecipes, userId, id);
                List<Recipe> recommendations = recipeService.getRecommendations(numRecipes, id,
                                UUID.fromString(userId));
                List<RecipeDTO> dtos = recommendations.stream()
                                .map(RecipeDTO::from)
                                .toList();
                return Response.ok(dtos).build();
        }

        // TODO should this be here or it a FeedbackResource?
        @POST
        @Path("/{mealPlanId}/feedback")
        public Response submitFeedback(
                        @PathParam("mealPlanId") String mealPlanId,
                        RecipeFeedback request) {

                LOGGER.infof("Received feedback for meal plan ID: %s, recipe ID: %s, action: %s",
                                mealPlanId, request.recipe_id().toString(), request.action());

                UUID mealPlanUuid = UUID.fromString(mealPlanId);

                // For now, use a default user ID - in production this would come from
                // authentication
                UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

                feedbackService.processFeedback(userId, request.recipe_id(), mealPlanUuid, request.action());

                return Response.ok().build();
        }

}
