package uk.co.cstdev;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import uk.co.cstdev.data.User;

@QuarkusTest
public class UserResourceTest {

    private static final String VALID_SECRET = "test-secret";
    private static final String WRONG_SECRET = "wrong-secret";

    @AfterEach
    @Transactional
    public void cleanUp() {
        User.deleteAll();
    }

    // --- POST /internal/users (upsert) ---

    @Test
    public void testUpsertCreatesNewUser() {
        String id = UUID.randomUUID().toString();

        given()
                .contentType("application/json")
                .header("X-Internal-Secret", VALID_SECRET)
                .body("""
                        {"id": "%s", "email": "alice@example.com", "name": "Alice"}
                        """.formatted(id))
                .post("/internal/users")
                .then()
                .statusCode(200);

        QuarkusTransaction.requiringNew().run(() -> {
            User user = User.findById(UUID.fromString(id));
            assertNotNull(user);
            assertEquals("alice@example.com", user.email);
            assertEquals("Alice", user.name);
        });
    }

    @Test
    public void testUpsertUpdatesExistingUser() {
        String id = UUID.randomUUID().toString();

        // Create initial
        given()
                .contentType("application/json")
                .header("X-Internal-Secret", VALID_SECRET)
                .body("""
                        {"id": "%s", "email": "alice@example.com", "name": "Alice"}
                        """.formatted(id))
                .post("/internal/users")
                .then()
                .statusCode(200);

        // Update email and name
        given()
                .contentType("application/json")
                .header("X-Internal-Secret", VALID_SECRET)
                .body("""
                        {"id": "%s", "email": "alice-new@example.com", "name": "Alice Updated"}
                        """.formatted(id))
                .post("/internal/users")
                .then()
                .statusCode(200);

        QuarkusTransaction.requiringNew().run(() -> {
            User user = User.findById(UUID.fromString(id));
            assertNotNull(user);
            assertEquals("alice-new@example.com", user.email);
            assertEquals("Alice Updated", user.name);
        });
    }

    @Test
    public void testUpsertFallsBackToEmailWhenNameIsAbsent() {
        String id = UUID.randomUUID().toString();

        given()
                .contentType("application/json")
                .header("X-Internal-Secret", VALID_SECRET)
                .body("""
                        {"id": "%s", "email": "bob@example.com"}
                        """.formatted(id))
                .post("/internal/users")
                .then()
                .statusCode(200);

        QuarkusTransaction.requiringNew().run(() -> {
            User user = User.findById(UUID.fromString(id));
            assertNotNull(user);
            assertEquals("bob@example.com", user.name);
        });
    }

    @Test
    public void testUpsertRejectsWrongSecret() {
        given()
                .contentType("application/json")
                .header("X-Internal-Secret", WRONG_SECRET)
                .body("""
                        {"id": "%s", "email": "eve@example.com", "name": "Eve"}
                        """.formatted(UUID.randomUUID()))
                .post("/internal/users")
                .then()
                .statusCode(401);
    }

    @Test
    public void testUpsertRejectsMissingSecret() {
        given()
                .contentType("application/json")
                .body("""
                        {"id": "%s", "email": "eve@example.com", "name": "Eve"}
                        """.formatted(UUID.randomUUID()))
                .post("/internal/users")
                .then()
                .statusCode(401);
    }

    @Test
    public void testUpsertReturnsBadRequestForInvalidUuid() {
        given()
                .contentType("application/json")
                .header("X-Internal-Secret", VALID_SECRET)
                .body("""
                        {"id": "not-a-uuid", "email": "bad@example.com", "name": "Bad"}
                        """)
                .post("/internal/users")
                .then()
                .statusCode(400);
    }

    @Test
    public void testUpsertReturnsBadRequestWhenEmailMissing() {
        given()
                .contentType("application/json")
                .header("X-Internal-Secret", VALID_SECRET)
                .body("""
                        {"id": "%s"}
                        """.formatted(UUID.randomUUID()))
                .post("/internal/users")
                .then()
                .statusCode(400);
    }

    // --- DELETE /internal/users/{id} ---

    @Test
    public void testDeleteRemovesExistingUser() {
        String id = UUID.randomUUID().toString();

        // Seed user directly
        QuarkusTransaction.requiringNew().run(() -> {
            User user = User.Builder.builder()
                    .id(UUID.fromString(id))
                    .email("delete-me@example.com")
                    .name("Delete Me")
                    .createdAt(new java.util.Date())
                    .build();
            user.persistAndFlush();
        });

        given()
                .header("X-Internal-Secret", VALID_SECRET)
                .delete("/internal/users/" + id)
                .then()
                .statusCode(204);

        QuarkusTransaction.requiringNew().run(() -> {
            assertNull(User.findById(UUID.fromString(id)));
        });
    }

    @Test
    public void testDeleteIsIdempotentForNonExistentUser() {
        given()
                .header("X-Internal-Secret", VALID_SECRET)
                .delete("/internal/users/" + UUID.randomUUID())
                .then()
                .statusCode(204);
    }

    @Test
    public void testDeleteRejectsWrongSecret() {
        given()
                .header("X-Internal-Secret", WRONG_SECRET)
                .delete("/internal/users/" + UUID.randomUUID())
                .then()
                .statusCode(401);
    }

    @Test
    public void testDeleteReturnsBadRequestForInvalidUuid() {
        given()
                .header("X-Internal-Secret", VALID_SECRET)
                .delete("/internal/users/not-a-uuid")
                .then()
                .statusCode(400);
    }
}
