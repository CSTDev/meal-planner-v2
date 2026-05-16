# menu-planner-ui

Next.js front-end for the meal planner, providing authentication (via Supabase) and UI for recipes, meal plans, and scraping.

## Environment variables

Create a `.env.local` file (never committed) with the following:

| Variable | Required | Description |
|---|---|---|
| `SUPABASE_URL` | Yes | Supabase project URL used by the **server** (middleware, API routes). When running inside Docker, use the Docker-internal address (e.g. `http://host.docker.internal:54321`). |
| `SUPABASE_PUBLIC_URL` | No | Supabase URL returned to the **browser** via `/api/config`. Defaults to `SUPABASE_URL` if not set. Set this when the server and browser reach Supabase via different hostnames (e.g. local dev with Docker). |
| `SUPABASE_ANON_KEY` | Yes | Supabase anonymous (public) key. |
| `API_GATEWAY_URL` | Yes | Base URL of the backend API gateway (e.g. `http://localhost:8080`). |

### Local dev example

```env
SUPABASE_URL=http://host.docker.internal:54321
SUPABASE_PUBLIC_URL=http://localhost:54321
SUPABASE_ANON_KEY=<your-anon-key>
API_GATEWAY_URL=http://localhost:8080
```

### Docker / production

Pass the vars at runtime — they are **not** baked into the image:

```bash
docker run \
  -e SUPABASE_URL=https://xxxx.supabase.co \
  -e SUPABASE_ANON_KEY=<anon-key> \
  -e API_GATEWAY_URL=https://api.example.com \
  -p 3000:3000 \
  ghcr.io/cstdev/meal-planner-ui:latest
```

`SUPABASE_PUBLIC_URL` is optional in production when the server and browser use the same Supabase host.

## Development

```bash
yarn install
yarn dev
```

Open [http://localhost:3000](http://localhost:3000).

## Build

```bash
yarn build
yarn start
```

The build requires **no** Supabase environment variables — Supabase config is fetched at runtime via `/api/config`.
