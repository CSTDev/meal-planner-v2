package uk.co.cstdev;

import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import uk.co.cstdev.service.UserService;

@Path("/internal/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Internal - Users", description = "Internal endpoints for Supabase user synchronisation (requires X-Internal-Secret header)")
public class UserResource {

    private static final Logger LOGGER = Logger.getLogger(UserResource.class);

    @Inject
    UserService userService;

    @ConfigProperty(name = "internal.api.secret")
    String internalApiSecret;

    public record UpsertRequest(String id, String email, String name) {
    }

    @POST
    @Operation(summary = "Upsert user", description = "Creates or updates a user record from a Supabase auth event")
    @APIResponse(responseCode = "200", description = "User upserted")
    @APIResponse(responseCode = "401", description = "Missing or invalid X-Internal-Secret header")
    public Response upsertUser(@HeaderParam("X-Internal-Secret") String authHeader, UpsertRequest request) {
        if (!isAuthorized(authHeader)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (request == null || request.id() == null || request.email() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("id and email are required").build();
        }

        UUID id;
        try {
            id = UUID.fromString(request.id());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid UUID").build();
        }

        String name = (request.name() != null && !request.name().isBlank()) ? request.name() : request.email();

        LOGGER.infof("Upserting user id=%s email=%s", id, request.email());
        userService.upsert(id, request.email(), name);

        return Response.ok().build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete user", description = "Removes a user record when the corresponding Supabase auth user is deleted")
    @APIResponse(responseCode = "204", description = "User deleted")
    @APIResponse(responseCode = "401", description = "Missing or invalid X-Internal-Secret header")
    public Response deleteUser(@HeaderParam("X-Internal-Secret") String authHeader, @PathParam("id") String id) {
        if (!isAuthorized(authHeader)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid UUID").build();
        }

        LOGGER.infof("Deleting user id=%s", uuid);
        userService.delete(uuid);

        return Response.noContent().build();
    }

    private boolean isAuthorized(String authHeader) {
        return internalApiSecret.equals(authHeader);
    }
}
