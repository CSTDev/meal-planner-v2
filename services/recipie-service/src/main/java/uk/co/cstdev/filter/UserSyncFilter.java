package uk.co.cstdev.filter;

import java.io.IOException;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import org.eclipse.microprofile.jwt.JsonWebToken;

import uk.co.cstdev.data.User;
import uk.co.cstdev.service.UserService;

@Provider
@Priority(Priorities.AUTHENTICATION + 1)
@ApplicationScoped
public class UserSyncFilter implements ContainerRequestFilter {

    @Inject
    JsonWebToken jwt;

    @Inject
    UserService userService;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String subject = jwt.getSubject();
        if (subject == null) {
            return;
        }

        UUID userId = UUID.fromString(subject);
        if (User.findById(userId) != null) {
            return;
        }

        String email = jwt.getClaim("email");
        String name = resolveName(email);
        userService.upsert(userId, email, name);
    }

    private String resolveName(String email) {
        JsonObject userMetadata = jwt.getClaim("user_metadata");
        if (userMetadata != null && userMetadata.containsKey("name")) {
            return userMetadata.getString("name");
        }
        return email;
    }
}
