# Deploying to Supabase

## Data API settings (Project Settings → Data API)

| Setting | Recommendation | Why |
|---|---|---|
| Enable Data API | Disable | recipe-service talks to Postgres directly via JDBC; the UI only uses Supabase for Auth. PostgREST is never used. |
| Automatically expose new tables | Disable | Moot with Data API off, but keeps future tables from being accidentally exposed if Data API is ever re-enabled. |
| Enable automatic RLS | Leave enabled | Cheap defense-in-depth — recipe-service connects with a privileged role and bypasses RLS anyway, but this protects you if Data API is ever turned back on. |

The Project URL and `anon`/`service_role` API keys are shown on the same Data API settings page regardless of whether the toggle above is enabled.

## 1. recipe-service (Quarkus) — Helm chart env / secret

| Variable | Value source | Notes |
|---|---|---|
| `DATABASE_URL` | Project Settings → Database → Connection string → JDBC, pooler variant | Format `jdbc:postgresql://<pooler-host>:6543/postgres` |
| `DATABASE_USERNAME` | Same connection string | Usually `postgres` (or `postgres.<project-ref>` for the pooler) |
| `DATABASE_PASSWORD` | The DB password set when creating the project | Helm secret, not values.yaml |
| `SUPABASE_JWKS_URL` | `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json` | |
| `SUPABASE_JWT_ISSUER` | `https://<project-ref>.supabase.co/auth/v1` | |
| `INTERNAL_API_SECRET` | Self-generated secret, e.g. `openssl rand -hex 32` | Guards the `/internal/users` admin endpoints; not required for normal operation since user records are synced lazily from the JWT on first authenticated request |

## 2. menu-planner-ui — Helm chart env / secret

| Variable | Value source | Notes |
|---|---|---|
| `SUPABASE_URL` | Project Settings → Data API → Project URL | |
| `SUPABASE_ANON_KEY` | Project Settings → Data API → Project API keys → `anon` | Secret |
| `API_GATEWAY_URL` | Internal/public URL of recipe-service in the cluster | e.g. `http://recipe-service.meal-planner.svc.cluster.local:8080` |

`SUPABASE_PUBLIC_URL` is an optional alias also checked by `api/config/route.ts` — not needed if `SUPABASE_URL` is set.

## 3. User sync

recipe-service is on a home network with no public ingress, so Supabase can't reach it via a push webhook. Instead, `UserSyncFilter` checks the local `users` table on every authenticated request and upserts from the JWT's `sub`/`email`/`user_metadata.name` claims if the user isn't present yet — no inbound exposure needed.

## 4. Supabase dashboard — manual steps

- Confirm Auth → Providers has email/password enabled.

## Remaining checklist

- [ ] Set the 6 recipe-service vars + 3 UI vars in the Helm chart (secrets vs plain values as noted above)
- [x] Confirm Auth → Providers has email/password enabled