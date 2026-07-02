package uk.co.cstdev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.restassured.RestAssured.given;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import uk.co.cstdev.data.FeedbackAction;
import uk.co.cstdev.data.MealPlan;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.User;
import uk.co.cstdev.data.UserRecipeInteraction;
import uk.co.cstdev.service.FeedbackService;

@QuarkusTest
public class ShoppingListResourceTest {

    @Inject
    FeedbackService feedbackService;

    public static final String USER_ID_STRING = "223e4567-e89b-12d3-a456-426614174111";
    public static final UUID USER_ID = UUID.fromString(USER_ID_STRING);

    private User user;
    private MealPlan mealPlan;

    @BeforeEach
    @Transactional
    public void setup() {
        user = User.Builder.builder()
                .id(USER_ID)
                .email("shopping-list-test@test.com")
                .name("Shopping List Test User")
                .createdAt(new java.util.Date())
                .build();
        user.persistAndFlush();

        mealPlan = MealPlan.Builder.builder()
                .userId(USER_ID)
                .recipeSource("all")
                .createdAt(new java.util.Date())
                .status("ACTIVE")
                .build();
        mealPlan.persistAndFlush();
    }

    @AfterEach
    @Transactional
    public void cleanUp() {
        UserRecipeInteraction.deleteAll();
        MealPlan.deleteAll();
        Recipe.deleteAll();
        User.deleteAll();
    }

    private record IngredientSpec(String name, float quantity, String unit) {
    }

    private Recipe persistRecipe(String title, IngredientSpec... ingredients) {
        Recipe.Builder builder = Recipe.Builder.recipe().title(title).servings(4);
        for (IngredientSpec ingredient : ingredients) {
            builder.ingredient(ingredient.name(), ingredient.quantity(), ingredient.unit());
        }
        Recipe recipe = builder.build();
        QuarkusTransaction.requiringNew().run(recipe::persist);
        return recipe;
    }

    private void accept(Recipe recipe) {
        feedbackService.processFeedback(USER_ID, recipe.id, mealPlan.id, FeedbackAction.ACCEPTED);
    }

    private void reject(Recipe recipe) {
        feedbackService.processFeedback(USER_ID, recipe.id, mealPlan.id, FeedbackAction.REJECTED);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getShoppingList(String mealPlanId) {
        return given()
                .when()
                .get("/api/meal-plans/{id}/shopping-list", mealPlanId)
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> ingredientsOf(Map<String, Object> response) {
        return (List<Map<String, Object>>) response.get("ingredients");
    }

    private Map<String, Object> findIngredient(List<Map<String, Object>> ingredients, String name) {
        return ingredients.stream()
                .filter(i -> name.equals(i.get("name")))
                .findFirst()
                .orElse(null);
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testSameUnitIngredientsAreSummed() {
        Recipe recipeA = persistRecipe("Recipe A", new IngredientSpec("chicken breast", 340f, "g"));
        Recipe recipeB = persistRecipe("Recipe B", new IngredientSpec("chicken breast", 340f, "g"));
        accept(recipeA);
        accept(recipeB);

        Map<String, Object> response = getShoppingList(mealPlan.id.toString());
        List<Map<String, Object>> ingredients = ingredientsOf(response);

        Map<String, Object> chicken = findIngredient(ingredients, "chicken breast");
        assertNotNull(chicken, "Expected a normalised 'chicken breast' ingredient line");

        List<Map<String, Object>> amounts = (List<Map<String, Object>>) chicken.get("amounts");
        assertEquals(1, amounts.size());
        assertEquals(680.0f, ((Number) amounts.get(0).get("quantity")).floatValue());
        assertEquals("g", amounts.get(0).get("unit"));

        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) chicken.get("breakdown");
        assertEquals(2, breakdown.size());
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testConvertibleUnitsAreSummedAndConverted() {
        Recipe recipeA = persistRecipe("Recipe A", new IngredientSpec("flour", 500f, "g"));
        Recipe recipeB = persistRecipe("Recipe B", new IngredientSpec("flour", 1f, "kg"));
        accept(recipeA);
        accept(recipeB);

        Map<String, Object> response = getShoppingList(mealPlan.id.toString());
        List<Map<String, Object>> ingredients = ingredientsOf(response);

        Map<String, Object> flour = findIngredient(ingredients, "flour");
        assertNotNull(flour);

        List<Map<String, Object>> amounts = (List<Map<String, Object>>) flour.get("amounts");
        assertEquals(1, amounts.size(), "Convertible units should be merged into a single amount entry");
        assertEquals(1500.0f, ((Number) amounts.get(0).get("quantity")).floatValue());
        assertEquals("g", amounts.get(0).get("unit"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testIncompatibleUnitsAreKeptSeparate() {
        Recipe recipeA = persistRecipe("Recipe A", new IngredientSpec("flour", 1f, "cup"));
        Recipe recipeB = persistRecipe("Recipe B", new IngredientSpec("flour", 200f, "g"));
        accept(recipeA);
        accept(recipeB);

        Map<String, Object> response = getShoppingList(mealPlan.id.toString());
        List<Map<String, Object>> ingredients = ingredientsOf(response);

        Map<String, Object> flour = findIngredient(ingredients, "flour");
        assertNotNull(flour);

        List<Map<String, Object>> amounts = (List<Map<String, Object>>) flour.get("amounts");
        assertEquals(2, amounts.size(), "Incompatible units should not be merged");

        boolean hasCup = amounts.stream()
                .anyMatch(a -> "cup".equals(a.get("unit")) && ((Number) a.get("quantity")).floatValue() == 1.0f);
        boolean hasGrams = amounts.stream()
                .anyMatch(a -> "g".equals(a.get("unit")) && ((Number) a.get("quantity")).floatValue() == 200.0f);
        assertTrue(hasCup, "Expected a separate 1 cup entry");
        assertTrue(hasGrams, "Expected a separate 200 g entry");

        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) flour.get("breakdown");
        assertEquals(2, breakdown.size());
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testQuantityLessIngredientsAreDeduplicatedWithEmptyAmounts() {
        Recipe recipeA = persistRecipe("Recipe A", new IngredientSpec("salt", 0f, "to taste"));
        Recipe recipeB = persistRecipe("Recipe B", new IngredientSpec("salt", 0f, "to taste"));
        accept(recipeA);
        accept(recipeB);

        Map<String, Object> response = getShoppingList(mealPlan.id.toString());
        List<Map<String, Object>> ingredients = ingredientsOf(response);

        Map<String, Object> salt = findIngredient(ingredients, "salt");
        assertNotNull(salt);

        List<Map<String, Object>> amounts = (List<Map<String, Object>>) salt.get("amounts");
        assertTrue(amounts.isEmpty(), "Quantity-less ingredients should have an empty amounts array");

        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) salt.get("breakdown");
        assertEquals(2, breakdown.size());
        assertNull(breakdown.get(0).get("quantity"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testQuantityPresentWithBlankUnitUsesNullUnitInAmounts() {
        Recipe recipeA = persistRecipe("Recipe A", new IngredientSpec("egg", 3f, ""));
        accept(recipeA);

        Map<String, Object> response = getShoppingList(mealPlan.id.toString());
        List<Map<String, Object>> ingredients = ingredientsOf(response);

        Map<String, Object> egg = findIngredient(ingredients, "egg");
        assertNotNull(egg);

        List<Map<String, Object>> amounts = (List<Map<String, Object>>) egg.get("amounts");
        assertEquals(1, amounts.size());
        assertEquals(3.0f, ((Number) amounts.get(0).get("quantity")).floatValue());
        assertNull(amounts.get(0).get("unit"), "Blank unit should be represented as null, not an empty string");
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testRejectedInteractionsAreExcluded() {
        Recipe recipeA = persistRecipe("Recipe A", new IngredientSpec("chicken breast", 340f, "g"));
        Recipe recipeB = persistRecipe("Recipe B", new IngredientSpec("garlic", 2f, "clove"));
        accept(recipeA);
        reject(recipeB);

        Map<String, Object> response = getShoppingList(mealPlan.id.toString());
        List<Map<String, Object>> ingredients = ingredientsOf(response);

        assertNotNull(findIngredient(ingredients, "chicken breast"));
        assertEquals(null, findIngredient(ingredients, "garlic"));
        assertEquals(1, ingredients.size());
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testEmptyMealPlanReturnsEmptyIngredientsList() {
        Map<String, Object> response = getShoppingList(mealPlan.id.toString());
        List<Map<String, Object>> ingredients = ingredientsOf(response);
        assertTrue(ingredients.isEmpty());
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "999e4567-e89b-12d3-a456-426614174999"),
            @Claim(key = "email", value = "other-user@test.com")
    })
    public void testMealPlanBelongingToDifferentUserIsForbidden() {
        given()
                .when()
                .get("/api/meal-plans/{id}/shopping-list", mealPlan.id.toString())
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testUnknownMealPlanReturnsNotFound() {
        given()
                .when()
                .get("/api/meal-plans/{id}/shopping-list", UUID.randomUUID().toString())
                .then()
                .statusCode(404);
    }
}
