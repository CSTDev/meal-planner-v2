package uk.co.cstdev;

import static org.awaitility.Awaitility.await;

import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.inject.Inject;
import uk.co.cstdev.data.ScrapeRequest;
import uk.co.cstdev.data.messaging.RecipeScrapeRequested;
import uk.co.cstdev.utils.KafkaTestResourceLifecycleManager;

import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(KafkaTestResourceLifecycleManager.class)
public class ScrapeResourceTest {

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @Test
    public void testScrapeRequestCreatesMessage() {
        String url = "http://example.com/recipe";

        ScrapeRequest request = new ScrapeRequest(url);

        given()
                .contentType("application/json")
                .body(request)
                .when()
                .post("/api/scrape")
                .then()
                .statusCode(200);

        connector.sink("scrape-requests");

        await().untilAsserted(() -> {
            Object received = connector.sink("scrape-requests").received().get(0).getPayload();
            assert received instanceof RecipeScrapeRequested;
            RecipeScrapeRequested actual = (RecipeScrapeRequested) received;
            assert actual.url().equals(url);
        });

    }

}
