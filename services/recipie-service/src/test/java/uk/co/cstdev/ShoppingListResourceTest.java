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
import uk.co.cstdev.data.MealPlanRecipe;
import uk.co.cstdev.data.MealPlanRecipeRepository;
import uk.co.cstdev.data.Recipe;
import uk.co.cstdev.data.User;
import uk.co.cstdev.data.UserRecipeInteraction;

@QuarkusTest
public class ShoppingListResourceTest {

    @Inject
    MealPlanRecipeRepository mealPlanRecipeRepository;

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
        MealPlanRecipe.deleteAll();
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
        if (!mealPlanRecipeRepository.claim(mealPlan.id, recipe.id, "ACCEPTED")) {
            mealPlanRecipeRepository.updateStatus(mealPlan.id, recipe.id, "ACCEPTED");
        }
    }

    private void reject(Recipe recipe) {
        mealPlanRecipeRepository.deleteByPlanAndRecipe(mealPlan.id, recipe.id);
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
    public void testDisplayNameUsesTypedWordOrderNotTokenSortedKey() {
        Recipe recipeA = persistRecipe("Recipe A", new IngredientSpec("brown rice", 100f, "g"));
        Recipe recipeB = persistRecipe("Recipe B", new IngredientSpec("brown rice", 100f, "g"));
        accept(recipeA);
        accept(recipeB);

        Map<String, Object> response = getShoppingList(mealPlan.id.toString());
        List<Map<String, Object>> ingredients = ingredientsOf(response);

        Map<String, Object> rice = findIngredient(ingredients, "brown rice");
        assertNotNull(rice, "Expected the display name to read 'brown rice', not the token-sorted 'rice brown'");

        List<Map<String, Object>> amounts = (List<Map<String, Object>>) rice.get("amounts");
        assertEquals(1, amounts.size());
        assertEquals(200.0f, ((Number) amounts.get(0).get("quantity")).floatValue());
        assertEquals("g", amounts.get(0).get("unit"));

        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) rice.get("breakdown");
        assertEquals(2, breakdown.size());
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testDisplayNamePicksMostFrequentWordingAcrossReorderedVariants() {
        // "brown rice" and "rice, brown" both normalize (token-sorted, descending)
        // to the dedup key "rice brown", which differs from either's natural
        // reading order. This means the assertion below can only pass if the
        // display name is chosen by majority-frequency counting of the natural
        // wording, not by falling back to the token-sorted dedup key. The
        // minority variant is accepted first (and the majority wording added
        // afterwards) so that a naive "first non-empty candidate wins" stub
        // (which ignores frequency counting) would also fail this test.
        Recipe recipeA = persistRecipe("Recipe A", new IngredientSpec("rice, brown", 100f, "g"));
        Recipe recipeB = persistRecipe("Recipe B", new IngredientSpec("brown rice", 100f, "g"));
        Recipe recipeC = persistRecipe("Recipe C", new IngredientSpec("brown rice", 100f, "g"));
        accept(recipeA);
        accept(recipeB);
        accept(recipeC);

        Map<String, Object> response = getShoppingList(mealPlan.id.toString());
        List<Map<String, Object>> ingredients = ingredientsOf(response);

        Map<String, Object> rice = findIngredient(ingredients, "brown rice");
        assertNotNull(rice, "Expected the majority wording 'brown rice' to be displayed, not the minority "
                + "reordered variant or the token-sorted dedup key 'rice brown'");
        assertEquals(1, ingredients.size(), "Reordered variant should still merge into the same line");

        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) rice.get("breakdown");
        assertEquals(3, breakdown.size(), "All three contributing recipes should be present in the breakdown");
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
    public void testIngredientsAreSortedAlphabeticallyByName() {
        Recipe recipeA = persistRecipe("Recipe A", new IngredientSpec("onion", 2f, ""));
        Recipe recipeB = persistRecipe("Recipe B", new IngredientSpec("garlic", 4f, "clove"));
        Recipe recipeC = persistRecipe("Recipe C", new IngredientSpec("chicken breast", 340f, "g"));
        accept(recipeA);
        accept(recipeB);
        accept(recipeC);

        Map<String, Object> response = getShoppingList(mealPlan.id.toString());
        List<Map<String, Object>> ingredients = ingredientsOf(response);

        List<String> names = ingredients.stream().map(i -> (String) i.get("name")).toList();
        assertEquals(List.of("chicken breast", "garlic", "onion"), names,
                "Shopping list ingredients should be sorted alphabetically by name");
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

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testAcceptThenRejectIsAbsentFromShoppingList() {
        // Regression: previously the ACCEPTED row remained after a later REJECT,
        // so the recipe stayed in the shopping list (ghost bug).
        Recipe recipe = persistRecipe("Ghost Recipe", new IngredientSpec("butter", 100f, "g"));
        accept(recipe);
        reject(recipe);

        Map<String, Object> response = getShoppingList(mealPlan.id.toString());
        List<Map<String, Object>> ingredients = ingredientsOf(response);

        assertNull(findIngredient(ingredients, "butter"),
                "Recipe accepted then rejected must not appear in shopping list");
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testRejectThenAcceptIsPresentInShoppingList() {
        // Reject first, then re-accept: the latest ACCEPTED timestamp should win.
        Recipe recipe = persistRecipe("Reinstated Recipe", new IngredientSpec("olive oil", 50f, "ml"));
        reject(recipe);
        accept(recipe);

        Map<String, Object> response = getShoppingList(mealPlan.id.toString());
        List<Map<String, Object>> ingredients = ingredientsOf(response);

        assertNotNull(findIngredient(ingredients, "olive oil"),
                "Recipe rejected then accepted must appear in shopping list");
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testDoubleAcceptIsIdempotent() {
        // Submitting the same ACCEPTED feedback twice must return 200 both times
        // (not 500 from a unique constraint violation) and the recipe appears once.
        Recipe recipe = persistRecipe("Double Accept Recipe", new IngredientSpec("flour", 200f, "g"));
        // The feedback endpoint now requires a live meal_plan_recipes row —
        // simulate it having been offered into this plan first.
        mealPlanRecipeRepository.claim(mealPlan.id, recipe.id, "OFFERED");

        given()
                .contentType(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
                .body("{\"recipe_id\": \"" + recipe.id + "\", \"action\": \"" + FeedbackAction.ACCEPTED.name() + "\"}")
                .when()
                .post("/api/meal-plans/{id}/feedback", mealPlan.id.toString())
                .then()
                .statusCode(200);

        given()
                .contentType(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
                .body("{\"recipe_id\": \"" + recipe.id + "\", \"action\": \"" + FeedbackAction.ACCEPTED.name() + "\"}")
                .when()
                .post("/api/meal-plans/{id}/feedback", mealPlan.id.toString())
                .then()
                .statusCode(200);

        Map<String, Object> response = getShoppingList(mealPlan.id.toString());
        List<Map<String, Object>> ingredients = ingredientsOf(response);

        Map<String, Object> flour = findIngredient(ingredients, "flour");
        assertNotNull(flour, "Double-accepted recipe must appear in shopping list");
        assertEquals(1, ingredients.size(), "Recipe should appear exactly once");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> amounts = (List<Map<String, Object>>) flour.get("amounts");
        assertEquals(1, amounts.size(), "Ingredient should have a single amount entry");
        assertEquals(200.0f, ((Number) amounts.get(0).get("quantity")).floatValue(),
                "Quantity must not be doubled (expected 200 g, not 400 g — verifies ON CONFLICT idempotency)");
        assertEquals("g", amounts.get(0).get("unit"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> amountsOf(Map<String, Object> ingredient) {
        return (List<Map<String, Object>>) ingredient.get("amounts");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> partsOf(Map<String, Object> amount) {
        return (List<Map<String, Object>>) amount.get("parts");
    }

    private void assertPart(Map<String, Object> part, float quantity, String unit, int count) {
        assertEquals(quantity, ((Number) part.get("quantity")).floatValue());
        assertEquals(unit, part.get("unit"));
        assertEquals(count, ((Number) part.get("count")).intValue());
    }

    private Map<String, Object> shoppingLineFor(String name) {
        Map<String, Object> line = findIngredient(ingredientsOf(getShoppingList(mealPlan.id.toString())), name);
        assertNotNull(line);
        return line;
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testIdenticalContributionsCollapseIntoCountedPart() {
        accept(persistRecipe("A", new IngredientSpec("rice", 250f, "g")));
        accept(persistRecipe("B", new IngredientSpec("rice", 250f, "g")));

        Map<String, Object> amount = amountsOf(shoppingLineFor("rice")).get(0);
        assertEquals(500.0f, ((Number) amount.get("quantity")).floatValue());
        List<Map<String, Object>> parts = partsOf(amount);
        assertEquals(1, parts.size());
        assertPart(parts.get(0), 250f, "g", 2);
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testPartsAreSortedLargestFirst() {
        accept(persistRecipe("A", new IngredientSpec("rice", 250f, "g")));
        accept(persistRecipe("B", new IngredientSpec("rice", 250f, "g")));
        accept(persistRecipe("C", new IngredientSpec("rice", 300f, "g")));

        Map<String, Object> amount = amountsOf(shoppingLineFor("rice")).get(0);
        assertEquals(800.0f, ((Number) amount.get("quantity")).floatValue());
        List<Map<String, Object>> parts = partsOf(amount);
        assertEquals(2, parts.size());
        assertPart(parts.get(0), 300f, "g", 1);
        assertPart(parts.get(1), 250f, "g", 2);
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testEqualBaseValuePartsAreOrderedByUnitName() {
        accept(persistRecipe("A", new IngredientSpec("flour", 1f, "kg")));
        accept(persistRecipe("B", new IngredientSpec("flour", 1000f, "g")));

        Map<String, Object> amount = amountsOf(shoppingLineFor("flour")).get(0);
        List<Map<String, Object>> parts = partsOf(amount);
        assertEquals(2, parts.size());
        assertPart(parts.get(0), 1000f, "g", 1);
        assertPart(parts.get(1), 1f, "kg", 1);
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testMixedConvertibleUnitsKeepOriginalUnitsInParts() {
        accept(persistRecipe("A", new IngredientSpec("flour", 500f, "g")));
        accept(persistRecipe("B", new IngredientSpec("flour", 1f, "kg")));

        Map<String, Object> amount = amountsOf(shoppingLineFor("flour")).get(0);
        assertEquals(1500.0f, ((Number) amount.get("quantity")).floatValue());
        assertEquals("g", amount.get("unit"));
        List<Map<String, Object>> parts = partsOf(amount);
        assertEquals(2, parts.size());
        assertPart(parts.get(0), 1f, "kg", 1);
        assertPart(parts.get(1), 500f, "g", 1);
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testSingleContributorHasNoParts() {
        accept(persistRecipe("A", new IngredientSpec("rice", 250f, "g")));

        Map<String, Object> amount = amountsOf(shoppingLineFor("rice")).get(0);
        assertTrue(partsOf(amount).isEmpty(), "A single contributor should not show a split");
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testIncompatibleUnitsEachGetOwnParts() {
        accept(persistRecipe("A", new IngredientSpec("flour", 1f, "cup")));
        accept(persistRecipe("B", new IngredientSpec("flour", 1f, "cup")));
        accept(persistRecipe("C", new IngredientSpec("flour", 200f, "g")));

        List<Map<String, Object>> amounts = amountsOf(shoppingLineFor("flour"));
        Map<String, Object> cup = amounts.stream().filter(a -> "cup".equals(a.get("unit"))).findFirst().orElseThrow();
        Map<String, Object> grams = amounts.stream().filter(a -> "g".equals(a.get("unit"))).findFirst().orElseThrow();
        assertEquals(1, partsOf(cup).size());
        assertPart(partsOf(cup).get(0), 1f, "cup", 2);
        assertTrue(partsOf(grams).isEmpty());
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testQuantityLessContributionsNeverAppearInParts() {
        accept(persistRecipe("A", new IngredientSpec("salt", 0f, "to taste")));
        accept(persistRecipe("B", new IngredientSpec("salt", 0f, "to taste")));
        accept(persistRecipe("C", new IngredientSpec("pepper", 5f, "g")));
        accept(persistRecipe("D", new IngredientSpec("pepper", 0f, "to taste")));

        assertTrue(amountsOf(shoppingLineFor("salt")).isEmpty());
        Map<String, Object> amount = amountsOf(shoppingLineFor("pepper")).get(0);
        assertTrue(partsOf(amount).isEmpty(), "Only one quantified contribution, so no split");
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID_STRING),
            @Claim(key = "email", value = "shopping-list-test@test.com")
    })
    public void testDuplicateLinesInOneRecipeAreSeparateParts() {
        accept(persistRecipe("A", new IngredientSpec("onion", 2f, null), new IngredientSpec("onion", 1f, null)));

        Map<String, Object> amount = amountsOf(shoppingLineFor("onion")).get(0);
        assertEquals(3.0f, ((Number) amount.get("quantity")).floatValue());
        List<Map<String, Object>> parts = partsOf(amount);
        assertEquals(2, parts.size());
        assertPart(parts.get(0), 2f, null, 1);
        assertPart(parts.get(1), 1f, null, 1);
    }
}
