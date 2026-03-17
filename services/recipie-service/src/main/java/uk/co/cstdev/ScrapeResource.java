package uk.co.cstdev;

import java.time.Instant;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import uk.co.cstdev.data.ScrapeRequest;
import uk.co.cstdev.data.messaging.EventMetadata;
import uk.co.cstdev.data.messaging.RecipeScrapeRequested;

@Path("/api/scrape")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ScrapeResource {

    @Channel("scrape-requests")
    Emitter<RecipeScrapeRequested> scrapeRequestEmitter;

    @Inject
    JsonWebToken jwt;

    @POST
    @RolesAllowed("authenticated")
    public Response ScrapeRecipe(ScrapeRequest url) {
        String userId = jwt.getSubject();
        scrapeRequestEmitter.send(new RecipeScrapeRequested(
                UUID.randomUUID().toString(),
                Instant.now(),
                new EventMetadata("recipe-service", UUID.randomUUID().toString(), userId),
                url.url(), userId));
        return Response.ok().build();
    }

}
