package uk.co.cstdev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

import io.restassured.common.mapper.TypeRef;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.MediaType;
import uk.co.cstdev.data.FeedbackAction;
import uk.co.cstdev.data.MealPlan;
import uk.co.cstdev.data.MealPlanRecipe;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.RecipeDTO;
import uk.co.cstdev.data.User;
import uk.co.cstdev.data.UserRecipeInteraction;
import uk.co.cstdev.data.mealplan.MealPlanFullResponse;
import uk.co.cstdev.data.mealplan.MealPlanResponse;
import uk.co.cstdev.data.mealplan.MealPlanSummaryResponse;
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
        public static final String USER_ID_STRING = "123e4567-e89b-12d3-a456-426614174000";
        public static final UUID USER_ID = UUID.fromString(USER_ID_STRING);

        @BeforeAll
        public static void init() {
                // Create some recipes
                recipes = Arrays.asList(
                                Recipe.Builder.recipe().title("Pancakes").servings(4).build(),
                                Recipe.Builder.recipe().title("Waffles").servings(6).build(),
                                Recipe.Builder.recipe().title("French Toast").servings(2).build());

                QuarkusTransaction.requiringNew().run(() -> {
                        MealPlanRecipe.deleteAll();
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
                        MealPlanRecipe.deleteAll();
                        MealPlanRecipe.flush();
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
                                .id(USER_ID)
                                .email("me@test.com")
                                .name("Test User")
                                .createdAt(new java.util.Date())
                                .build();
                user.persistAndFlush();
        }

        @AfterEach
        @Transactional
        public void cleanUp() {
                MealPlanRecipe.deleteAll();
                UserRecipeInteraction.deleteAll();
                MealPlan.deleteAll();
                user.delete();
                User.deleteAll();
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
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
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testGetRecommendationsForMealPlan() {
                // Create a Meal Plan
                MealPlanResponse response = given()
                                .when()
                                .contentType("application/json")
                                .body("""
                                                {
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
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testGetRecommendationsForMealPlanReturnsAsManyAsItCan() {
                // Create a Meal Plan
                MealPlanResponse response = given()
                                .when()
                                .contentType("application/json")
                                .body("""
                                                {
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
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testGetRecommendationsIgnoresRejectedMeals() {
                // Create a Meal Plan
                MealPlanResponse response = given()
                                .when()
                                .contentType("application/json")
                                .body("""
                                                {
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
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testGetRecommendationsIgnoresAcceptedMeals() {
                // Create a Meal Plan
                MealPlanResponse response = given()
                                .when()
                                .contentType("application/json")
                                .body("""
                                                {
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
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testGetRecommendationsExcludesMealsUsedInLast90Days() {
                // Create a Meal Plan
                MealPlanResponse response = given()
                                .when()
                                .contentType("application/json")
                                .body("""
                                                {
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
                                                                        VALUES (?, ?, ?, ?, ?)
                                                                        """)
                                        .setParameter(1, user.id)
                                        .setParameter(2, recipes.getFirst().id)
                                        .setParameter(3, oldMealPlan.id)
                                        .setParameter(4, FeedbackAction.ACCEPTED.name())
                                        .setParameter(5, interactionDate)
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

        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        @Test
        public void testOnlyInteractionsForCurrentUserAreConsidered() {
                // Create interaction in last 90 days for another user
                User secondUser = createSecondUser();
                MealPlanResponse response = given()
                                .when()
                                .contentType("application/json")
                                .body("""
                                                {
                                                    "userId": "%s",
                                                    "numRecipes": 5,
                                                    "recipeSource": "all"
                                                }
                                                """.formatted(secondUser.id))
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
                                                                        VALUES (?, ?, ?, ?, ?)
                                                                        """)
                                        .setParameter(1, secondUser.id)
                                        .setParameter(2, recipes.getFirst().id)
                                        .setParameter(3, oldMealPlan.id)
                                        .setParameter(4, FeedbackAction.ACCEPTED.name())
                                        .setParameter(5, interactionDate)
                                        .executeUpdate();
                        entityManager.flush();
                });

                // Create new meal plan
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
                // Returns all 3 as they're available for this user
                assertEquals(3, recommendations.size());

        }

        @Transactional
        User createSecondUser() {
                User user = User.Builder.builder()
                                .id(UUID.randomUUID())
                                .email("second@test.com")
                                .name("Second Test User")
                                .createdAt(new java.util.Date())
                                .build();
                user.persistAndFlush();
                return user;
        }

        @Transactional
        MealPlan createMealPlanForUser(UUID userId) {
                return createMealPlanForUser(userId, new java.util.Date());
        }

        @Transactional
        MealPlan createMealPlanForUser(UUID userId, java.util.Date createdAt) {
                MealPlan plan = MealPlan.Builder.builder()
                                .userId(userId)
                                .recipeSource("all")
                                .status("ACTIVE")
                                .createdAt(createdAt)
                                .build();
                plan.persistAndFlush();
                return plan;
        }

        // End RecommendationTests

        // Recipe Search Tests

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testRecipeSearchMatchesPartialTitleCaseInsensitive() {
                MealPlanResponse response = given()
                                .when()
                                .contentType("application/json")
                                .body("""
                                                {"numRecipes": 5, "recipeSource": "all"}
                                                """)
                                .post("/api/meal-plans")
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(MealPlanResponse.class);

                // "PAN" (uppercase) should match "Pancakes"
                List<RecipeDTO> results = given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s/recipe-search?q=PAN".formatted(response.id()))
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(new TypeRef<List<RecipeDTO>>() {
                                });

                assertEquals(1, results.size());
                assertEquals("Pancakes", results.get(0).title);
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testRecipeSearchNoMatchReturnsEmpty() {
                MealPlanResponse response = given()
                                .when()
                                .contentType("application/json")
                                .body("""
                                                {"numRecipes": 5, "recipeSource": "all"}
                                                """)
                                .post("/api/meal-plans")
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(MealPlanResponse.class);

                List<RecipeDTO> results = given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s/recipe-search?q=xyz999notexist".formatted(response.id()))
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(new TypeRef<List<RecipeDTO>>() {
                                });

                assertEquals(0, results.size());
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testRecipeSearchExcludesAcceptedRecipes() {
                MealPlanResponse response = given()
                                .when()
                                .contentType("application/json")
                                .body("""
                                                {"numRecipes": 5, "recipeSource": "all"}
                                                """)
                                .post("/api/meal-plans")
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(MealPlanResponse.class);
                MealPlan plan = MealPlan.findById(UUID.fromString(response.id()));

                feedbackService.processFeedback(user.id, recipes.getFirst().id, plan.id, FeedbackAction.ACCEPTED);

                List<RecipeDTO> results = given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s/recipe-search?q=pan".formatted(response.id()))
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(new TypeRef<List<RecipeDTO>>() {
                                });

                assertEquals(0, results.size());
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testRecipeSearchIncludesRejectedRecipes() {
                MealPlanResponse response = given()
                                .when()
                                .contentType("application/json")
                                .body("""
                                                {"numRecipes": 5, "recipeSource": "all"}
                                                """)
                                .post("/api/meal-plans")
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(MealPlanResponse.class);
                MealPlan plan = MealPlan.findById(UUID.fromString(response.id()));

                feedbackService.processFeedback(user.id, recipes.getFirst().id, plan.id, FeedbackAction.REJECTED);

                List<RecipeDTO> results = given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s/recipe-search?q=pan".formatted(response.id()))
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(new TypeRef<List<RecipeDTO>>() {
                                });

                assertEquals(1, results.size());
                assertEquals("Pancakes", results.get(0).title);
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testRecipeSearchLimitsToTenResultsOrderedByTitle() {
                List<Recipe> extraRecipes = new ArrayList<>();
                QuarkusTransaction.requiringNew().run(() -> {
                        for (int i = 1; i <= 12; i++) {
                                Recipe r = Recipe.Builder.recipe()
                                                .title("ZZSearchTest %02d".formatted(i))
                                                .servings(1)
                                                .build();
                                r.persist();
                                extraRecipes.add(r);
                        }
                });

                try {
                        MealPlanResponse response = given()
                                        .when()
                                        .contentType("application/json")
                                        .body("""
                                                        {"numRecipes": 5, "recipeSource": "all"}
                                                        """)
                                        .post("/api/meal-plans")
                                        .then()
                                        .statusCode(200)
                                        .extract()
                                        .as(MealPlanResponse.class);

                        List<RecipeDTO> results = given()
                                        .when()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .get("/api/meal-plans/%s/recipe-search?q=ZZSearchTest".formatted(response.id()))
                                        .then()
                                        .statusCode(200)
                                        .extract()
                                        .as(new TypeRef<List<RecipeDTO>>() {
                                        });

                        assertEquals(10, results.size());
                        assertEquals("ZZSearchTest 01", results.get(0).title);
                        assertEquals("ZZSearchTest 10", results.get(9).title);
                } finally {
                        QuarkusTransaction.requiringNew().run(() -> {
                                // Plan creation may have claimed some of these into
                                // meal_plan_recipes — clear those rows first so the FK
                                // doesn't block deleting the recipes themselves.
                                MealPlanRecipe.delete("id.recipeId in ?1",
                                                extraRecipes.stream().map(r -> r.id).toList());
                                extraRecipes.forEach(r -> Recipe.deleteById(r.id));
                        });
                }
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testRecipeSearchWithBlankQReturnsEmpty() {
                MealPlanResponse response = given()
                                .when()
                                .contentType("application/json")
                                .body("""
                                                {"numRecipes": 5, "recipeSource": "all"}
                                                """)
                                .post("/api/meal-plans")
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(MealPlanResponse.class);

                List<RecipeDTO> results = given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s/recipe-search?q=".formatted(response.id()))
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(new TypeRef<List<RecipeDTO>>() {
                                });

                assertEquals(0, results.size());
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testRecipeSearchWithMissingQReturnsEmpty() {
                MealPlanResponse response = given()
                                .when()
                                .contentType("application/json")
                                .body("""
                                                {"numRecipes": 5, "recipeSource": "all"}
                                                """)
                                .post("/api/meal-plans")
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(MealPlanResponse.class);

                List<RecipeDTO> results = given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s/recipe-search".formatted(response.id()))
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(new TypeRef<List<RecipeDTO>>() {
                                });

                assertEquals(0, results.size());
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testRecipeSearchUnknownPlanReturns404() {
                String unknownId = UUID.randomUUID().toString();

                given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s/recipe-search?q=pan".formatted(unknownId))
                                .then()
                                .statusCode(404);
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testRecipeSearchPlanOwnedByOtherUserReturns403() {
                User secondUser = createSecondUser();
                MealPlan otherPlan = createMealPlanForUser(secondUser.id);

                given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s/recipe-search?q=pan".formatted(otherPlan.id))
                                .then()
                                .statusCode(403);
        }

        // End RecipeSearch Tests

        // List Meal Plans Tests

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testListMealPlansReturnsAtMostTenNewestFirst() {
                List<MealPlan> plans = new ArrayList<>();
                long now = System.currentTimeMillis();
                // Plan 0 is the newest, plan 11 the oldest.
                for (int i = 0; i < 12; i++) {
                        MealPlan plan = createMealPlanForUser(USER_ID,
                                        new java.util.Date(now - i * 60_000L));
                        feedbackService.processFeedback(USER_ID, recipes.getFirst().id, plan.id,
                                        FeedbackAction.ACCEPTED);
                        plans.add(plan);
                }

                List<MealPlanSummaryResponse> results = given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans")
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(new TypeRef<List<MealPlanSummaryResponse>>() {
                                });

                assertEquals(10, results.size());
                for (int i = 0; i < 10; i++) {
                        assertEquals(plans.get(i).id.toString(), results.get(i).id(),
                                        "Plans should be ordered newest first");
                }
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testListMealPlansExcludesPlansWithZeroAcceptedRecipes() {
                long now = System.currentTimeMillis();
                MealPlan planWithAccepted = createMealPlanForUser(USER_ID, new java.util.Date(now));
                feedbackService.processFeedback(USER_ID, recipes.getFirst().id, planWithAccepted.id,
                                FeedbackAction.ACCEPTED);

                // Plan with no interactions at all
                createMealPlanForUser(USER_ID, new java.util.Date(now - 60_000L));

                // Plan whose only recipe was accepted then later rejected — its
                // effective accepted count is 0, so it must be excluded too.
                MealPlan acceptThenRejectPlan = createMealPlanForUser(USER_ID,
                                new java.util.Date(now - 120_000L));
                feedbackService.processFeedback(USER_ID, recipes.getFirst().id, acceptThenRejectPlan.id,
                                FeedbackAction.ACCEPTED);
                feedbackService.processFeedback(USER_ID, recipes.getFirst().id, acceptThenRejectPlan.id,
                                FeedbackAction.REJECTED);

                List<MealPlanSummaryResponse> results = given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans")
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(new TypeRef<List<MealPlanSummaryResponse>>() {
                                });

                assertEquals(1, results.size());
                assertEquals(planWithAccepted.id.toString(), results.get(0).id());
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testListMealPlansAcceptedRecipeCountUsesLatestInteraction() {
                MealPlan plan = createMealPlanForUser(USER_ID);

                // Accepted, stays accepted — counts.
                feedbackService.processFeedback(USER_ID, recipes.get(0).id, plan.id, FeedbackAction.ACCEPTED);
                // Accepted then later rejected — must NOT count.
                feedbackService.processFeedback(USER_ID, recipes.get(1).id, plan.id, FeedbackAction.ACCEPTED);
                feedbackService.processFeedback(USER_ID, recipes.get(1).id, plan.id, FeedbackAction.REJECTED);
                // Rejected then later accepted — counts.
                feedbackService.processFeedback(USER_ID, recipes.get(2).id, plan.id, FeedbackAction.REJECTED);
                feedbackService.processFeedback(USER_ID, recipes.get(2).id, plan.id, FeedbackAction.ACCEPTED);

                List<MealPlanSummaryResponse> results = given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans")
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(new TypeRef<List<MealPlanSummaryResponse>>() {
                                });

                assertEquals(1, results.size());
                assertEquals(plan.id.toString(), results.get(0).id());
                assertEquals(2, results.get(0).acceptedRecipeCount(),
                                "Accept-then-reject must not be counted; reject-then-accept must be");
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testListMealPlansExcludesOtherUsersPlans() {
                User secondUser = createSecondUser();
                MealPlan otherPlan = createMealPlanForUser(secondUser.id);
                feedbackService.processFeedback(secondUser.id, recipes.getFirst().id, otherPlan.id,
                                FeedbackAction.ACCEPTED);

                List<MealPlanSummaryResponse> results = given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans")
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(new TypeRef<List<MealPlanSummaryResponse>>() {
                                });

                assertEquals(0, results.size());
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testListMealPlansWithIdenticalCreatedAtHaveDeterministicOrder() {
                // Three plans created at exactly the same instant — createdAt alone
                // cannot order them, so the id tie-break must.
                java.util.Date sameInstant = new java.util.Date();
                List<MealPlan> plans = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                        MealPlan plan = createMealPlanForUser(USER_ID, sameInstant);
                        feedbackService.processFeedback(USER_ID, recipes.getFirst().id, plan.id,
                                        FeedbackAction.ACCEPTED);
                        plans.add(plan);
                }

                // Postgres orders uuids byte-wise, which matches lexicographic order
                // of their canonical string form.
                List<String> expectedIds = plans.stream()
                                .map(plan -> plan.id.toString())
                                .sorted(java.util.Comparator.reverseOrder())
                                .toList();

                List<MealPlanSummaryResponse> results = given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans")
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(new TypeRef<List<MealPlanSummaryResponse>>() {
                                });

                assertEquals(expectedIds,
                                results.stream().map(MealPlanSummaryResponse::id).toList(),
                                "Plans with identical createdAt should be ordered by id descending");
        }

        // End List Meal Plans Tests

        // Get Meal Plan Full State Tests

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testGetMealPlanReturnsFullCurrentState() {
                MealPlanResponse createResponse = given()
                                .when()
                                .contentType("application/json")
                                .body("""
                                                {"numRecipes": 2, "recipeSource": "all"}
                                                """)
                                .post("/api/meal-plans")
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(MealPlanResponse.class);

                MealPlanFullResponse fullState = given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s".formatted(createResponse.id()))
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(MealPlanFullResponse.class);

                assertEquals(createResponse.id(), fullState.id());
                assertEquals(2, fullState.recipes().size());
                fullState.recipes().forEach(r -> assertEquals("OFFERED", r.status()));
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testGetMealPlanUnknownPlanReturns404() {
                given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s".formatted(UUID.randomUUID()))
                                .then()
                                .statusCode(404);
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testGetMealPlanOwnedByOtherUserReturns403() {
                User secondUser = createSecondUser();
                MealPlan otherPlan = createMealPlanForUser(secondUser.id);

                given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s".formatted(otherPlan.id))
                                .then()
                                .statusCode(403);
        }

        // End Get Meal Plan Full State Tests

        // Accepted Recipes Tests

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testAcceptedRecipesUnknownPlanReturns404() {
                given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s/accepted-recipes".formatted(UUID.randomUUID()))
                                .then()
                                .statusCode(404);
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testAcceptedRecipesPlanOwnedByOtherUserReturns403() {
                User secondUser = createSecondUser();
                MealPlan otherPlan = createMealPlanForUser(secondUser.id);

                given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s/accepted-recipes".formatted(otherPlan.id))
                                .then()
                                .statusCode(403);
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testAcceptedRecipesOrderedMostRecentlyAcceptedFirst() {
                MealPlan plan = createMealPlanForUser(USER_ID);

                // Accept in a known order: first, then second, then third.
                feedbackService.processFeedback(USER_ID, recipes.get(0).id, plan.id, FeedbackAction.ACCEPTED);
                feedbackService.processFeedback(USER_ID, recipes.get(1).id, plan.id, FeedbackAction.ACCEPTED);
                feedbackService.processFeedback(USER_ID, recipes.get(2).id, plan.id, FeedbackAction.ACCEPTED);

                List<RecipeDTO> results = given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s/accepted-recipes".formatted(plan.id))
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(new TypeRef<List<RecipeDTO>>() {
                                });

                assertEquals(3, results.size());
                assertEquals(recipes.get(2).id, results.get(0).id,
                                "Most recently accepted recipe should come first");
                assertEquals(recipes.get(1).id, results.get(1).id);
                assertEquals(recipes.get(0).id, results.get(2).id);
        }

        @Test
        @TestSecurity(user = "testuser", roles = "authenticated")
        @JwtSecurity(claims = {
                        @Claim(key = "sub", value = USER_ID_STRING),
                        @Claim(key = "email", value = "me@test.com")
        })
        public void testAcceptedRecipesExcludesAcceptThenReject() {
                MealPlan plan = createMealPlanForUser(USER_ID);

                feedbackService.processFeedback(USER_ID, recipes.get(0).id, plan.id, FeedbackAction.ACCEPTED);
                // Accepted then later rejected — must not be listed.
                feedbackService.processFeedback(USER_ID, recipes.get(1).id, plan.id, FeedbackAction.ACCEPTED);
                feedbackService.processFeedback(USER_ID, recipes.get(1).id, plan.id, FeedbackAction.REJECTED);

                List<RecipeDTO> results = given()
                                .when()
                                .contentType(MediaType.APPLICATION_JSON)
                                .get("/api/meal-plans/%s/accepted-recipes".formatted(plan.id))
                                .then()
                                .statusCode(200)
                                .extract()
                                .as(new TypeRef<List<RecipeDTO>>() {
                                });

                assertEquals(1, results.size());
                assertEquals(recipes.get(0).id, results.get(0).id);
        }

        // End Accepted Recipes Tests
}
