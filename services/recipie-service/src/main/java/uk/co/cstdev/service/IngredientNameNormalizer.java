package uk.co.cstdev.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Normalises ingredient names for deduplication purposes:
 * - lowercase + whitespace trim
 * - singular/plural folding (simple suffix-based heuristic)
 * - word-order normalisation (token-sort)
 *
 * Edit-distance / synonym matching is explicitly out of scope.
 */
public final class IngredientNameNormalizer {

    private IngredientNameNormalizer() {
    }

    public static String normalize(String rawName) {
        if (rawName == null) {
            return "";
        }

        String cleaned = rawName.trim().toLowerCase().replaceAll("[,]+", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return "";
        }

        List<String> tokens = new ArrayList<>(Arrays.asList(cleaned.split(" ")));
        tokens.replaceAll(IngredientNameNormalizer::singularize);
        Collections.sort(tokens);
        return String.join(" ", tokens);
    }

    private static String singularize(String word) {
        if (word.length() < 4) {
            return word;
        }
        if (word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.endsWith("oes")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("ses") || word.endsWith("xes") || word.endsWith("ches") || word.endsWith("shes")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
