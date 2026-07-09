package uk.co.cstdev;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.inject.Inject;
import uk.co.cstdev.data.ScrapeRequest;
import uk.co.cstdev.data.messaging.RecipeScrapeRequested;
import uk.co.cstdev.utils.KafkaTestResourceLifecycleManager;

import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(KafkaTestResourceLifecycleManager.class)
public class ScrapeResourceTest {

    static final String USER_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @BeforeEach
    public void setup() {
        connector.sink("scrape-requests").clear();
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID),
            @Claim(key = "email", value = "me@test.com")
    })
    public void testScrapeRequestPublishesMessageWithAuthenticatedUserId() {
        String url = "http://example.com/recipe";

        given()
                .contentType("application/json")
                .body(new ScrapeRequest(url))
                .when()
                .post("/api/scrape")
                .then()
                .statusCode(200);

        await().untilAsserted(() -> {
            RecipeScrapeRequested actual = (RecipeScrapeRequested) connector.sink("scrape-requests")
                    .received().get(0).getPayload();
            assertEquals(url, actual.url());
            assertEquals(USER_ID, actual.userId());
            assertEquals(USER_ID, actual.metadata().userId());
        });
    }

    @Test
    public void testScrapeRequiresAuthentication() {
        given()
                .contentType("application/json")
                .body(new ScrapeRequest("http://example.com/recipe"))
                .when()
                .post("/api/scrape")
                .then()
                .statusCode(401);
    }

}
