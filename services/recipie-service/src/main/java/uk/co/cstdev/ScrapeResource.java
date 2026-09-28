package uk.co.cstdev;

import java.time.Instant;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import io.quarkus.security.Authenticated;
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
import uk.co.cstdev.service.RecipeService;

@Path("/api/scrape")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Scraping", description = "Trigger asynchronous recipe scraping from a URL")
public class ScrapeResource {

    @Channel("scrape-requests")
    Emitter<RecipeScrapeRequested> scrapeRequestEmitter;

    @Inject
    JsonWebToken jwt;

    @Inject
    RecipeService recipeService;

    @POST
    @Authenticated
    @Operation(summary = "Request recipe scrape", description = "Publishes a scrape-requested event for the given URL. The recipe is stored asynchronously when scraping completes. If the requesting user has already scraped this URL, no event is published and the existing recipe is left as-is.")
    @APIResponse(responseCode = "200", description = "Scrape request accepted, or a duplicate was silently ignored")
    @APIResponse(responseCode = "401", description = "Unauthorized")
    public Response ScrapeRecipe(ScrapeRequest url) {
        String userId = jwt.getSubject();

        if (recipeService.findByUrlAndUserId(url.url(), UUID.fromString(userId)).isPresent()) {
            return Response.ok().build();
        }

        scrapeRequestEmitter.send(new RecipeScrapeRequested(
                UUID.randomUUID().toString(),
                Instant.now(),
                new EventMetadata("recipe-service", UUID.randomUUID().toString(), userId),
                url.url(), userId));
        return Response.ok().build();
    }

}
