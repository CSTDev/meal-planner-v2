package uk.co.cstdev.service;

import java.util.Map;

import static java.util.Map.entry;

/**
 * Handles unit-string normalisation and merging of quantities that share the same
 * "family" (fixed-ratio SI conversions only, e.g. g &lt;-&gt; kg, ml &lt;-&gt; L).
 * Units that cannot be converted without ingredient-specific density data (e.g.
 * "cup" vs "g") are treated as incompatible and are never merged.
 */
public final class UnitConverter {

    private UnitConverter() {
    }

    // Maps a normalised unit string to a [family, ratio-to-base-unit] pair.
    private static final Map<String, Family> UNIT_FAMILIES = Map.ofEntries(
            entry("g", new Family("mass", 1d)),
            entry("gram", new Family("mass", 1d)),
            entry("grams", new Family("mass", 1d)),
            entry("kg", new Family("mass", 1000d)),
            entry("kilogram", new Family("mass", 1000d)),
            entry("kilograms", new Family("mass", 1000d)),
            entry("ml", new Family("volume", 1d)),
            entry("milliliter", new Family("volume", 1d)),
            entry("milliliters", new Family("volume", 1d)),
            entry("millilitre", new Family("volume", 1d)),
            entry("millilitres", new Family("volume", 1d)),
            entry("l", new Family("volume", 1000d)),
            entry("liter", new Family("volume", 1000d)),
            entry("liters", new Family("volume", 1000d)),
            entry("litre", new Family("volume", 1000d)),
            entry("litres", new Family("volume", 1000d)));

    public static String normalizeUnit(String unit) {
        if (unit == null) {
            return "";
        }
        return unit.trim().toLowerCase();
    }

    /**
     * Returns the SI base-unit family for a normalised unit string, or null if the
     * unit is not part of a known fixed-ratio conversion family.
     */
    public static Family familyFor(String normalizedUnit) {
        return UNIT_FAMILIES.get(normalizedUnit);
    }

    /**
     * The canonical/base unit symbol to display totals in for a given family.
     */
    public static String baseUnitSymbolFor(String familyName) {
        return switch (familyName) {
            case "mass" -> "g";
            case "volume" -> "ml";
            default -> null;
        };
    }

    public record Family(String name, double ratioToBaseUnit) {
    }
}
