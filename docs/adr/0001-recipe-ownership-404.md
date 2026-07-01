# Recipe ownership is enforced by 404, not 403

A Recipe is private to the user who scraped it (`scraped_by_user_id`). Single-recipe
lookups (`GET /api/recipes/{id}`) scope the query to the authenticated user, so a
request for a recipe scraped by someone else is indistinguishable from one that
does not exist — both return **404**.

We chose 404 over 403 deliberately: a 403 would confirm that a given recipe id
exists for another user, leaking information across accounts. With 404 the API never
reveals the existence of recipes the caller does not own. The cost is that a genuine
"you're not allowed" case is reported as "not found", which is acceptable here since
recipes are strictly single-owner and there is no sharing model.

**Considered and rejected:** returning 403 for not-owned. Clearer semantics, but
leaks existence.
