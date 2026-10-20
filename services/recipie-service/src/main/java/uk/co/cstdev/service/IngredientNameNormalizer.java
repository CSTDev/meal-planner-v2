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
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

/**
 * Normalises ingredient names for deduplication purposes:
 * - lowercase + whitespace trim
 * - singular/plural folding (simple suffix-based heuristic)
 * - word-order normalisation (token-sort)
 *
 * - descriptor allow-list stripping and explicit synonym phrases, both loaded
 *   from resource files under /ingredient-matching/
 *
 * Edit-distance / fuzzy matching is explicitly out of scope.
 */
public final class IngredientNameNormalizer {

    private static final String RESOURCE_DIR = "/ingredient-matching/";

    /** word -> singular form (identity mapping for words that must stay unchanged). */
    private static final Map<String, String> SINGULAR_EXCEPTIONS = loadSingularExceptions();

    private static final Set<String> DESCRIPTORS = new HashSet<>(readDataLines("descriptors.txt"));

    /** Synonym entries (alias phrase tokens -> canonical tokens), longest alias first. */
    private static final List<Map.Entry<List<String>, List<String>>> SYNONYMS = loadSynonyms();

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

    private static List<String> singularTokens(String phrase) {
        List<String> tokens = new ArrayList<>(Arrays.asList(phrase.trim().split("\\s+")));
        tokens.replaceAll(IngredientNameNormalizer::singularize);
        return tokens;
    }

    private static List<Map.Entry<List<String>, List<String>>> loadSynonyms() {
        List<Map.Entry<List<String>, List<String>>> entries = new ArrayList<>();
        for (String line : readDataLines("synonyms.txt")) {
            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new IllegalStateException("Bad synonym line: " + line);
            }
            entries.add(Map.entry(singularTokens(line.substring(0, eq)), singularTokens(line.substring(eq + 1))));
        }
        entries.sort((a, b) -> Integer.compare(b.getKey().size(), a.getKey().size()));
        return List.copyOf(entries);
    }

    private static List<String> stripDescriptors(List<String> tokens) {
        List<String> kept = new ArrayList<>(tokens);
        kept.removeIf(DESCRIPTORS::contains);
        // Never strip a name down to nothing.
        return kept.isEmpty() ? tokens : kept;
    }

    private static List<String> applySynonyms(List<String> tokens) {
        List<String> result = new ArrayList<>();
        int i = 0;
        outer:
        while (i < tokens.size()) {
            for (Map.Entry<List<String>, List<String>> entry : SYNONYMS) {
                List<String> alias = entry.getKey();
                if (i + alias.size() <= tokens.size() && tokens.subList(i, i + alias.size()).equals(alias)) {
                    result.addAll(entry.getValue());
                    i += alias.size();
                    continue outer;
                }
            }
            result.add(tokens.get(i++));
        }
        return result;
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

        List<String> tokens = singularTokens(cleaned);
        tokens = applySynonyms(stripDescriptors(tokens));
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
