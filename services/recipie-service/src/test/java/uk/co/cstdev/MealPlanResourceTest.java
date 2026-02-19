package uk.co.cstdev;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import uk.co.cstdev.data.MealPlan;
import uk.co.cstdev.data.User;
import uk.co.cstdev.data.mealplan.MealPlanResponse;
import uk.co.cstdev.service.MealPlanService;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class MealPlanResourceTest {

    @Inject
    MealPlanService mealPlanService;

    private User user;
    private MealPlan mealPlan;

    @BeforeEach
    @Transactional
    public void setup() {
        user = User.Builder.builder()
                .email("me@test.com")
                .name("Test User")
                .createdAt(new java.util.Date())
                .build();
        user.persistAndFlush();
    }

    @AfterEach
    @Transactional
    public void cleanUp() {
        mealPlan.delete();
        user.delete();
    }

    @Test
    public void testCreateMealPlan() {

        // Check the user exists
        User returnedUser = User.findById(user.id);
        assertNotNull(returnedUser);

        // Create the meal plan
        MealPlanResponse response = given()
                .when()
                .contentType("application/json")
                .body("""
                        {
                            "userId": "%s",
                            "dietaryPreferences": "all",
                            "status": "active"
                        }
                        """.formatted(user.id))
                .post("/api/meal-plans")
                .then()
                .statusCode(200)
                .extract()
                .as(MealPlanResponse.class);

        mealPlan = MealPlan.findById(UUID.fromString(response.id()));
        assertNotNull(mealPlan);
    }

}
