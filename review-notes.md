
## Review cycle 1 — 2026-10-20

STATUS: APPROVED

CRITICAL:
- none

WARNINGS:
- none

SUGGESTIONS:
- Corpus parser in IngredientNameNormalizerTest.corpus() does parts[1] with no check, so a malformed line (no "|") fails with ArrayIndexOutOfBounds instead of a clear message. Validate parts.length == 2.
- Exception-file additions beyond the seed (halves, pies, brownies, brioches, chilies, loaves) have no corpus entries, though the file header says every change needs one. Add e.g. "pecan halves | pecan half", "pies | pie", "loaves | loaf".
- Synonym matching needs the alias tokens contiguous after descriptor stripping; "ground cumin" is a phrase and does not match "cumin seed" (correct, and covered by a must-not-merge pair). Worth a one-line note in synonyms.txt that adding a single-token alias (e.g. "ground") would be unsafe.
- Further singulariser review (spec asked for one) is not documented. Rules look fine for common words (tomatoes, peaches, cheeses, anchovies), but consider a short corpus of real ingredient words as a regression test.
- ShoppingListResourceTest eggs check uses endsWith("egg"), which depends on which display name wins in a merged line. It is fine today but fragile.

Verified against spec: allow-list only (seed words, none of ground/dried/smoked/frozen/tinned/canned/dark/white/fresh); explicit synonyms in resource file with longest-alias-first, single pass, applied before sort; singulariser exceptions in a resource file with all seed entries; same-core-token equality only; cleanForDisplay untouched; all required must-not-merge and should-merge pairs present in the corpus; singulariser unit test and ShoppingListResourceTest case added. IngredientNameNormalizerTest ran locally: 42 tests, 0 failures. Quarkus tests not run (no Docker).

SUMMARY: Looks good.

---json
{
  "status": "APPROVED",
  "critical": [],
  "warnings": [],
  "summary": "Looks good."
}
---
