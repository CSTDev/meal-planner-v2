package uk.co.cstdev;

import java.time.Instant;
import java.util.UUID;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

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

    @POST
    public Response ScrapeRecipe(ScrapeRequest url) {
        scrapeRequestEmitter.send(new RecipeScrapeRequested(
                UUID.randomUUID().toString(), // eventId
                Instant.now(),
                new EventMetadata( // metadata
                        "recipe-service",
                        UUID.randomUUID().toString(),
                        "user-1"),
                url.url(), "user-1"));
        return Response.ok().build();
    }

}
