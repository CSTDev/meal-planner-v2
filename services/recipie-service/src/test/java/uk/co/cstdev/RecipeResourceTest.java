package uk.co.cstdev;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.transaction.Transactional;
import uk.co.cstdev.data.Recipe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class RecipeResourceTest {

    static final String USER_ID = "123e4567-e89b-12d3-a456-426614174000";
    static final String OTHER_USER_ID = "223e4567-e89b-12d3-a456-426614174000";

    @BeforeEach
    @Transactional
    void setup() {
        Recipe.deleteAll();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        Recipe.deleteAll();
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID),
            @Claim(key = "email", value = "me@test.com")
    })
    void testGetRecipesReturnsOnlyUsersRecipes() {
        createRecipe(Recipe.Builder.recipe()
                .title("Pancakes")
                .description("Delicious fluffy pancakes")
                .prepTime(10)
                .cookTime(10)
                .servings(4)
                .scrapedByUserId(UUID.fromString(USER_ID)));

        given()
                .when().get("/api/recipes")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].title", is("Pancakes"))
                .body("[0].description", is("Delicious fluffy pancakes"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID),
            @Claim(key = "email", value = "me@test.com")
    })
    void testGetRecipesDoesNotReturnOtherUsersRecipes() {
        createRecipe(Recipe.Builder.recipe()
                .title("My Recipe")
                .servings(2)
                .scrapedByUserId(UUID.fromString(USER_ID)));
        createRecipe(Recipe.Builder.recipe()
                .title("Someone Elses Recipe")
                .servings(2)
                .scrapedByUserId(UUID.fromString(OTHER_USER_ID)));

        given()
                .when().get("/api/recipes")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].title", is("My Recipe"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "authenticated")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_ID),
            @Claim(key = "email", value = "me@test.com")
    })
    void testGetRecipesReturnsMultipleUserRecipes() {
        createRecipe(Recipe.Builder.recipe().title("Pancakes").servings(4).scrapedByUserId(UUID.fromString(USER_ID)));
        createRecipe(Recipe.Builder.recipe().title("Waffles").servings(6).scrapedByUserId(UUID.fromString(USER_ID)));
        createRecipe(Recipe.Builder.recipe().title("French Toast").servings(2).scrapedByUserId(UUID.fromString(USER_ID)));

        given()
                .when().get("/api/recipes")
                .then()
                .statusCode(200)
                .body("size()", is(3));
    }

    @Test
    void testGetRecipesRequiresAuthentication() {
        given()
                .when().get("/api/recipes")
                .then()
                .statusCode(401);
    }

    @Transactional
    void createRecipe(Recipe.Builder builder) {
        Recipe recipe = builder.build();
        recipe.persist();
    }
}
