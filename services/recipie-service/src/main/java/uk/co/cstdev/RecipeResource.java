package uk.co.cstdev;

import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import uk.co.cstdev.data.RecipeDTO;
import uk.co.cstdev.service.RecipeService;

@Path("/api/recipes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Recipes", description = "Operations for managing user recipes")
public class RecipeResource {

    @Inject
    RecipeService recipeService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Operation(summary = "Get my recipes", description = "Returns all recipes scraped by the authenticated user")
    @APIResponse(responseCode = "200", description = "List of recipes")
    @APIResponse(responseCode = "401", description = "Unauthorized")
    public Response getMyRecipes() {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<RecipeDTO> recipes = recipeService.getRecipesForUser(userId)
                .stream()
                .map(RecipeDTO::from)
                .toList();
        return Response.ok(recipes).build();
    }
}
