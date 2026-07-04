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
}
