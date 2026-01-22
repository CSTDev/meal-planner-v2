package uk.co.cstdev;

import org.jboss.logging.Logger;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import uk.co.cstdev.data.mealplan.MealPlanRequest;
import uk.co.cstdev.data.mealplan.MealPlanResponse;

@Path("/api/meal-plans")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MealPlanResource {

    private static final Logger LOGGER = Logger.getLogger(MealPlanResource.class);

    @POST
    public Response createMealPlan(MealPlanRequest request) {
        // Placeholder implementation
        return Response.ok(new MealPlanResponse("123", "test-user-123", "all", "active")).build();
    }

    @GET
    @Path("/{id}/recommendations")
    public Response getMealPlanRecommendations(@PathParam("id") String id, @QueryParam("num_recipes") int numRecipes) {
        LOGGER.infof("Fetching %d recommendations for meal plan ID: %s", numRecipes, id);
        // Placeholder implementation
        return Response.ok().build();
    }

}
