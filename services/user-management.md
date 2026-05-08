# User Management: Supabase → Local DB Sync

## Problem

The `meal_plans` table has a FK `meal_plans_user_id_fkey` referencing the local `users` table.
Authenticated users come from Supabase Auth, so their IDs are not present in the local `users` table until explicitly provisioned.

**Error seen:**
```
org.hibernate.exception.ConstraintViolationException: could not execute statement
[ERROR: insert or update on table "meal_plans" violates foreign key constraint "meal_plans_user_id_fkey"
  Detail: Key (user_id)=(d080544b-3979-444f-a2ef-148b0fcd7817) is not present in table "users".]
```

The call stack originates in `MealPlanService.createMealPlan` → `MealPlanResource.createMealPlan`.

---

## Chosen Approach: Supabase Webhook + Edge Function

When a user is created/updated/deleted in Supabase Auth, a **Supabase Database Webhook** (or Postgres trigger on `auth.users`) fires and calls a **Supabase Edge Function**, which forwards the event to the recipe-service to keep the local `users` table in sync.

---

## Implementation Plan

### 1. Supabase Edge Function

Create an Edge Function (e.g. `sync-user`) that:
- Receives the Supabase Auth webhook payload
- Validates a shared secret header to prevent unauthenticated calls
- Calls the recipe-service `POST /api/users` (or `DELETE /api/users/{id}`) with the user data

Webhook payload shape (from `auth.users`):
```json
{
  "type": "INSERT" | "UPDATE" | "DELETE",
  "table": "users",
  "schema": "auth",
  "record": { "id": "uuid", "email": "...", "raw_user_meta_data": { "name": "..." } },
  "old_record": null | { ... }
}
```

### 2. Recipe Service: UserResource

Add a new `UserResource` (internal endpoint) in the recipe-service:

```
POST   /internal/users        — upsert a user from auth event
DELETE /internal/users/{id}   — remove user on account deletion
```

- Secured with a shared secret header (not JWT — this is a server-to-server call)
- Idempotent: use `INSERT ... ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email`
- Extract `name` from `raw_user_meta_data` if present, fall back to email

### 3. Supabase Database Webhook

Configure in Supabase Dashboard → Database → Webhooks:
- Table: `auth.users`
- Events: `INSERT`, `UPDATE`, `DELETE`
- URL: Edge Function URL for `sync-user`
- HTTP header: `Authorization: Bearer <shared-secret>`

### 4. Local Dev

The Edge Function needs to reach the recipe-service. In the devcontainer setup:
- Use `host.docker.internal` as the recipe-service host from within the Edge Function
- Or expose via an ngrok tunnel if testing webhooks end-to-end locally
- Alternatively, seed the local `users` table manually for dev (the upsert-on-first-use approach works as a local dev shim — see below)

---

## Fallback / Short-term Shim (not chosen for prod)

The alternative considered was **upsert on first use**: in `MealPlanService.createMealPlan`, check if the user exists in the local DB and insert if not, using `sub` and `email` claims from the JWT.

Rejected for production because:
- User data (email changes, deletions) won't propagate without upsert logic on every request
- Logic would need to be duplicated at every FK entry point
- No `name` available without user metadata in the JWT

This shim can be kept as a **local dev convenience** or a safety net, but the webhook is the authoritative sync mechanism.

---

## Files to Change

| File | Change |
|------|--------|
| `recipie-service/src/main/java/uk/co/cstdev/UserResource.java` | New — internal upsert/delete endpoint |
| `recipie-service/src/main/java/uk/co/cstdev/service/UserService.java` | New — upsert/delete logic |
| `recipie-service/src/main/java/uk/co/cstdev/data/User.java` | Possibly add `persistOrUpdate` helper |
| `recipie-service/src/main/resources/application.properties` | Add shared secret config key |
| `recipie-service/src/test/java/uk/co/cstdev/UserResourceTest.java` | New — test upsert and delete |
| `supabase/functions/sync-user/index.ts` | New — Edge Function |
| Supabase Dashboard | Configure Database Webhook (manual step) |

---

## Security Notes

- The `/internal/users` endpoint must **not** require a Supabase JWT — it's called by the Edge Function, not a browser
- Use a dedicated `INTERNAL_API_SECRET` env var (set in both the Edge Function and recipe-service)
- Validate the secret on every request to `/internal/*`
- Consider IP allowlisting if the infra supports it
