package uk.co.cstdev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

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
        for (String word : new String[] {"cress", "citrus", "lemongrass", "gas", "glass"}) {
            assertEquals(word, IngredientNameNormalizer.normalize(word));
        }
        assertEquals("leaf", IngredientNameNormalizer.normalize("leaves"));
        assertEquals("loaf", IngredientNameNormalizer.normalize("loaves"));
        assertEquals(IngredientNameNormalizer.normalize("cookie"), IngredientNameNormalizer.normalize("cookies"));
        assertEquals(IngredientNameNormalizer.normalize("quiche"), IngredientNameNormalizer.normalize("quiches"));
        assertEquals(IngredientNameNormalizer.normalize("chilli"), IngredientNameNormalizer.normalize("chillies"));
    }

    @TestFactory
    public List<DynamicTest> corpus() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        boolean shouldMerge = true;
        boolean sectionSeen = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/ingredient-matching-corpus.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.equals("[should-merge]")) {
                    shouldMerge = true;
                    sectionSeen = true;
                    continue;
                }
                if (line.equals("[must-not-merge]")) {
                    shouldMerge = false;
                    sectionSeen = true;
                    continue;
                }
                if (!sectionSeen) {
                    throw new IllegalStateException("Corpus line before section header: " + line);
                }
                String[] parts = line.split("\\|");
                if (parts.length != 2) {
                    throw new IllegalStateException("Corpus line must be 'a | b': " + line);
                }
                String a = parts[0].trim();
                String b = parts[1].trim();
                boolean merge = shouldMerge;
                tests.add(dynamicTest((merge ? "merge: " : "keep apart: ") + a + " / " + b, () -> {
                    String keyA = IngredientNameNormalizer.normalize(a);
                    String keyB = IngredientNameNormalizer.normalize(b);
                    if (merge) {
                        assertEquals(keyA, keyB);
                    } else {
                        assertNotEquals(keyA, keyB);
                    }
                }));
            }
        }
        assertNotEquals(0, tests.size());
        return tests;
    }
}
