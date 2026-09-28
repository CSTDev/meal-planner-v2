package uk.co.cstdev.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import uk.co.cstdev.data.Recipe;

@QuarkusTest
public class RecipeServiceTest {

    @Inject
    RecipeService recipeService;

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
    void addRecipeSwallowsAUniqueConstraintViolationOnADuplicateUrlForTheSameUser() {
        UUID userId = UUID.randomUUID();
        String url = "https://example.com/duplicate-recipe";

        recipeService.addRecipe(newRecipe(url), userId);

        assertDoesNotThrow(() -> recipeService.addRecipe(newRecipe(url), userId));

        assertEquals(1, recipeService.getRecipesForUser(userId).size());
    }

    private Recipe newRecipe(String url) {
        return Recipe.Builder.recipe()
                .title("Duplicate Recipe")
                .url(url)
                .servings(2)
                .build();
    }
}
