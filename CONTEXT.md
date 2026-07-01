# Menu Planner

Domain glossary shared across all services under `services/` (the Quarkus
`recipie-service`, the Python `scraper`, and the Next.js `menu-planner-ui`).

## Language

**Recipe**:
A dish a user has saved by scraping it from an external site. Carries a title,
description, image, ingredients, ordered cooking instructions, prep/cook times,
servings, tags, and the `canonicalUrl` it was scraped from.
_Avoid_: Meal (a Recipe placed into a Meal Plan is not itself a "meal").

**Ingredient**:
One line of a Recipe's ingredient list, modelled as `quantity` (number),
`unit`, and `name`. The scraper parses the source line into these three parts;
the original unparsed text is **not** part of the domain — it is discarded at
scrape time and no service stores it.
_Avoid_: originalText (a UI-only fiction that duplicated `name`; being removed).

**Scraped by**:
The ownership relation between a Recipe and the user who scraped it. A Recipe is
private to that user; the API exposes a user only their own recipes. A request
for a Recipe scraped by someone else is indistinguishable from one that does not
exist (both yield 404).
_Avoid_: Owned by, created by.

**Not specified**:
The absence of a numeric value (prep time, cook time, servings). Because these
fields are non-nullable integers, `0` is the encoding for "not specified" — the
domain has no concept of a genuinely zero-minute or zero-serving Recipe.
_Avoid_: Zero, empty, null.
