package uk.co.cstdev;

import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;

import jakarta.annotation.security.RolesAllowed;
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
@RolesAllowed("authenticated")
public class RecipeResource {

    @Inject
    RecipeService recipeService;

    @Inject
    JsonWebToken jwt;

    @GET
    public Response getMyRecipes() {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<RecipeDTO> recipes = recipeService.getRecipesForUser(userId)
                .stream()
                .map(RecipeDTO::from)
                .toList();
        return Response.ok(recipes).build();
    }
}
