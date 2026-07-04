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
        // Descending alphabetical order, per the spec's worked example:
        // "breast, chicken" -> "chicken breast". Any input token order for a given
        // set of words normalises to the same canonical string (that's what makes
        // token-sort useful for dedup); descending order is the specific rule that
        // reproduces the spec's example and response-shape sample verbatim.
        tokens.sort(Collections.reverseOrder());
        return String.join(" ", tokens);
    }

    /**
     * Tidies a raw ingredient name for display purposes only: trims, converts
     * commas to spaces and collapses whitespace. Unlike {@link #normalize(String)}
     * this does NOT reorder or singularise the words, and preserves original
     * casing, so the result reads naturally (e.g. "brown rice" stays
     * "brown rice", not "rice brown").
     */
    public static String cleanForDisplay(String rawName) {
        if (rawName == null) {
            return "";
        }

        return rawName.trim().replaceAll("[,]+", " ").replaceAll("\\s+", " ").trim();
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
        if (word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
