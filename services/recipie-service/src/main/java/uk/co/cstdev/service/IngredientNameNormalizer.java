package uk.co.cstdev.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalises ingredient names for deduplication purposes:
 * - lowercase + whitespace trim
 * - singular/plural folding (simple suffix-based heuristic)
 * - word-order normalisation (token-sort)
 *
 * Edit-distance / synonym matching is explicitly out of scope.
 */
public final class IngredientNameNormalizer {

    private static final String RESOURCE_DIR = "/ingredient-matching/";

    /** word -> singular form (identity mapping for words that must stay unchanged). */
    private static final Map<String, String> SINGULAR_EXCEPTIONS = loadSingularExceptions();

    private IngredientNameNormalizer() {
    }

    /** Reads a data file, dropping blank lines and # comments. */
    private static List<String> readDataLines(String fileName) {
        try (InputStream in = IngredientNameNormalizer.class.getResourceAsStream(RESOURCE_DIR + fileName)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE_DIR + fileName);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim().toLowerCase();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    lines.add(trimmed);
                }
            }
            return lines;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, String> loadSingularExceptions() {
        Map<String, String> map = new HashMap<>();
        for (String line : readDataLines("singulariser-exceptions.txt")) {
            int eq = line.indexOf('=');
            if (eq < 0) {
                map.put(line, line);
            } else {
                map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        return map;
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
        String exception = SINGULAR_EXCEPTIONS.get(word);
        if (exception != null) {
            return exception;
        }
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
