package uk.co.cstdev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class IngredientNameNormalizerTest {

    @Test
    public void singularizeDoesNotCorruptWordsEndingInBareUs() {
        assertEquals("hummus", IngredientNameNormalizer.normalize("hummus"));
        assertEquals("asparagus", IngredientNameNormalizer.normalize("asparagus"));
    }

    @Test
    public void singularizeStillFoldsRegularPlurals() {
        assertEquals("egg", IngredientNameNormalizer.normalize("eggs"));
        assertEquals("box", IngredientNameNormalizer.normalize("boxes"));
        assertEquals("berry", IngredientNameNormalizer.normalize("berries"));
    }

    @Test
    public void singulariserExceptionsAreRespected() {
        assertEquals("molasses", IngredientNameNormalizer.normalize("molasses"));
        assertEquals("couscous", IngredientNameNormalizer.normalize("couscous"));
        assertEquals("watercress", IngredientNameNormalizer.normalize("watercress"));
        assertEquals("swiss", IngredientNameNormalizer.normalize("swiss"));
        assertEquals("leaf", IngredientNameNormalizer.normalize("leaves"));
        assertEquals("loaf", IngredientNameNormalizer.normalize("loaves"));
        assertEquals(IngredientNameNormalizer.normalize("cookie"), IngredientNameNormalizer.normalize("cookies"));
        assertEquals(IngredientNameNormalizer.normalize("quiche"), IngredientNameNormalizer.normalize("quiches"));
        assertEquals(IngredientNameNormalizer.normalize("chilli"), IngredientNameNormalizer.normalize("chillies"));
    }
}
