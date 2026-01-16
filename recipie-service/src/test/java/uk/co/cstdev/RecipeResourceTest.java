package uk.co.cstdev;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import uk.co.cstdev.data.Recipe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class RecipeResourceTest {

    @BeforeEach
    @Transactional
    void setup() {
        Recipe.deleteAll();
    }

    @Test
    void testGetRecipes() {
        createRecipe(
                Recipe.Builder.recipe()
                        .title("Pancakes")
                        .description("Delicious fluffy pancakes")
                        .prepTime(10)
                        .cookTime(10)
                        .servings(4));

        given()
                .when().get("/api/recipes")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].title", is("Pancakes"))
                .body("[0].description", is("Delicious fluffy pancakes"));
    }

    @Test
    void testMultipleRecipes() {
        createRecipe(Recipe.Builder.recipe().title("Pancakes").servings(4));
        createRecipe(Recipe.Builder.recipe().title("Waffles").servings(6));
        createRecipe(Recipe.Builder.recipe().title("French Toast").servings(2));

        given()
                .when().get("/api/recipes")
                .then()
                .statusCode(200)
                .body("size()", is(3));
    }

    @Transactional
    void createRecipe(Recipe.Builder builder) {
        Recipe recipe = builder.build();
        recipe.persist();
    }

}