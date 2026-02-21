package uk.co.cstdev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.MediaType;
import uk.co.cstdev.data.FeedbackAction;
import uk.co.cstdev.data.MealPlan;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.RecipeDTO;
import uk.co.cstdev.data.User;
import uk.co.cstdev.data.UserRecipeInteraction;
import uk.co.cstdev.data.mealplan.MealPlanResponse;
import uk.co.cstdev.service.FeedbackService;
import uk.co.cstdev.service.MealPlanService;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class MealPlanResourceTest {

    @Inject
    MealPlanService mealPlanService;

    @Inject
    FeedbackService feedbackService;

    @Inject
    EntityManager entityManager;

    private User user;
    private MealPlan mealPlan;
    private static List<Recipe> recipes;

    @BeforeAll
    public static void init() {
        // Create some recipes
        recipes = Arrays.asList(
                Recipe.Builder.recipe().title("Pancakes").servings(4).build(),
                Recipe.Builder.recipe().title("Waffles").servings(6).build(),
                Recipe.Builder.recipe().title("French Toast").servings(2).build());

        QuarkusTransaction.requiringNew().run(() -> {
            Recipe.deleteAll();
            UserRecipeInteraction.deleteAll();
            MealPlan.deleteAll();
            User.deleteAll();
            recipes.forEach(recipe -> {
                recipe.persist();
            });
        });

    }

    @AfterAll
    public static void cleanDb() {
        QuarkusTransaction.requiringNew().run(() -> {
            UserRecipeInteraction.deleteAll();
            UserRecipeInteraction.flush();
            MealPlan.deleteAll();
            MealPlan.flush();
            recipes.forEach(recipe -> {
                recipe.delete();
            });
        });
    }

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
        UserRecipeInteraction.deleteAll();
        MealPlan.deleteAll();
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
                            "numRecipes": 2,
                            "recipeSource": "all"
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

    // Recommendation Tests
    @Test
    public void testGetRecommendationsForMealPlan() {
        // Create a Meal Plan
        MealPlanResponse response = given()
                .when()
                .contentType("application/json")
                .body("""
                        {
                            "userId": "%s",
                            "numRecipes": 2,
                            "recipeSource": "all"
                        }
                        """.formatted(user.id))
                .post("/api/meal-plans")
                .then()
                .statusCode(200)
                .extract()
                .as(MealPlanResponse.class);
        mealPlan = MealPlan.findById(UUID.fromString(response.id()));
        assertNotNull(mealPlan);

        // Get recommendations
        List<RecipeDTO> recommendations = given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .get("/api/meal-plans/%s/recommendations?num_recipes=2".formatted(response.id()))
                .then()
                .statusCode(200)
                .extract()
                .as(new TypeRef<List<RecipeDTO>>() {
                });
        assertEquals(2, recommendations.size());

    }

    @Test
    public void testGetRecommendationsForMealPlanReturnsAsManyAsItCan() {
        // Create a Meal Plan
        MealPlanResponse response = given()
                .when()
                .contentType("application/json")
                .body("""
                        {
                            "userId": "%s",
                            "numRecipes": 5,
                            "recipeSource": "all"
                        }
                        """.formatted(user.id))
                .post("/api/meal-plans")
                .then()
                .statusCode(200)
                .extract()
                .as(MealPlanResponse.class);
        mealPlan = MealPlan.findById(UUID.fromString(response.id()));
        assertNotNull(mealPlan);

        // Get recommendations
        List<RecipeDTO> recommendations = given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .get("/api/meal-plans/%s/recommendations?num_recipes=5".formatted(response.id()))
                .then()
                .statusCode(200)
                .extract()
                .as(new TypeRef<List<RecipeDTO>>() {
                });
        // Returns only 3 as that's how many recipes we have seeded
        assertEquals(3, recommendations.size());
    }

    @Test
    public void testGetRecommendationsIgnoresRejectedMeals() {
        // Create a Meal Plan
        MealPlanResponse response = given()
                .when()
                .contentType("application/json")
                .body("""
                        {
                            "userId": "%s",
                            "numRecipes": 5,
                            "recipeSource": "all"
                        }
                        """.formatted(user.id))
                .post("/api/meal-plans")
                .then()
                .statusCode(200)
                .extract()
                .as(MealPlanResponse.class);
        mealPlan = MealPlan.findById(UUID.fromString(response.id()));
        assertNotNull(mealPlan);

        feedbackService.processFeedback(user.id, recipes.getFirst().id, mealPlan.id, FeedbackAction.REJECTED);

        // Get recommendations
        List<RecipeDTO> recommendations = given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .get("/api/meal-plans/%s/recommendations?num_recipes=5".formatted(response.id()))
                .then()
                .statusCode(200)
                .extract()
                .as(new TypeRef<List<RecipeDTO>>() {
                });
        // Returns only 2 as that's how many recipes we have that aren't rejected
        assertEquals(2, recommendations.size());
    }

    @Test
    public void testGetRecommendationsIgnoresAcceptedMeals() {
        // Create a Meal Plan
        MealPlanResponse response = given()
                .when()
                .contentType("application/json")
                .body("""
                        {
                            "userId": "%s",
                            "numRecipes": 5,
                            "recipeSource": "all"
                        }
                        """.formatted(user.id))
                .post("/api/meal-plans")
                .then()
                .statusCode(200)
                .extract()
                .as(MealPlanResponse.class);
        mealPlan = MealPlan.findById(UUID.fromString(response.id()));
        assertNotNull(mealPlan);

        feedbackService.processFeedback(user.id, recipes.getFirst().id, mealPlan.id, FeedbackAction.ACCEPTED);
        feedbackService.processFeedback(user.id, recipes.getLast().id, mealPlan.id, FeedbackAction.ACCEPTED);

        // Get recommendations
        List<RecipeDTO> recommendations = given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .get("/api/meal-plans/%s/recommendations?num_recipes=5".formatted(response.id()))
                .then()
                .statusCode(200)
                .extract()
                .as(new TypeRef<List<RecipeDTO>>() {
                });
        // Returns only 1 as that's how many recipes we have that aren't accepted
        assertEquals(1, recommendations.size());
    }

    @Test
    public void testGetRecommendationsExcludesMealsUsedInLast90Days() {
        // Create a Meal Plan
        MealPlanResponse response = given()
                .when()
                .contentType("application/json")
                .body("""
                        {
                            "userId": "%s",
                            "numRecipes": 5,
                            "recipeSource": "all"
                        }
                        """.formatted(user.id))
                .post("/api/meal-plans")
                .then()
                .statusCode(200)
                .extract()
                .as(MealPlanResponse.class);
        mealPlan = MealPlan.findById(UUID.fromString(response.id()));
        assertNotNull(mealPlan);

        response = given()
                .when()
                .contentType("application/json")
                .body("""
                        {
                            "userId": "%s",
                            "numRecipes": 5,
                            "recipeSource": "all"
                        }
                        """.formatted(user.id))
                .post("/api/meal-plans")
                .then()
                .statusCode(200)
                .extract()
                .as(MealPlanResponse.class);
        MealPlan oldMealPlan = MealPlan.findById(UUID.fromString(response.id()));
        assertNotNull(oldMealPlan);

        // Simulate a meal being used in the last 90 days
        QuarkusTransaction.requiringNew().run(() -> {
            var interactionDate = Instant.now().minusSeconds(60 * 60 * 24 * 30); // 30 days ago
            entityManager
                    .createNativeQuery(
                            """
                                    INSERT INTO user_recipe_interactions (user_id, recipe_id, meal_plan_id, interaction_type, interaction_at)
                                    VALUES (?, ?, ?, 'ACCEPTED', ?)
                                    """)
                    .setParameter(1, user.id)
                    .setParameter(2, recipes.getFirst().id)
                    .setParameter(3, oldMealPlan.id)
                    .setParameter(4, interactionDate)
                    .executeUpdate();
            entityManager.flush();
        });

        // Get recommendations
        List<RecipeDTO> recommendations = given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .get("/api/meal-plans/%s/recommendations?num_recipes=5".formatted(response.id()))
                .then()
                .statusCode(200)
                .extract()
                .as(new TypeRef<List<RecipeDTO>>() {
                });
        // Returns only 2 as that's how many recipes we have that aren't accepted
        assertEquals(2, recommendations.size());

    }

    // TODO: Add test to ensure that interactions are only considered for the
    // relevant user.

    // End RecommendationTests
}
