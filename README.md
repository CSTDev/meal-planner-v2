# Meal Planner v2

A meal planning application with recipe scraping, a Quarkus backend, and a Next.js frontend.

## Services

| Service | Path | Language | Port |
|---|---|---|---|
| `recipe-service` | `services/recipie-service/` | Java 21 / Quarkus | 8080 |
| `scraper` | `services/scraper/` | Python 3.12 | 8000 (metrics) |
| `menu-planner-ui` | `services/menu-planner-ui/` | Next.js (TypeScript) | 3000 |

Dependencies: PostgreSQL (via Supabase), Kafka.

## Building locally

The easiest way to run it all together locally is to use devcontainers for the java and pythong services and then run the UI on the host PC.

Run the top level `.devcontainer/docker-compose.yml` this will start all the required containers. 
Connect VSCode to the ones that you want to run inside.

Start supabase on the host PC (presuming it's installed): `supabase start`

### recipe-service

Requires Java 21 and Maven (or use the wrapper).

```bash
cd services/recipie-service
./mvnw package -DskipTests        # build JAR
./mvnw quarkus:dev                # dev mode with live reload
```

The packaged app lands in `target/quarkus-app/`. The Dockerfile at
`src/main/docker/Dockerfile.jvm` expects this directory to exist before
the image is built.

### scraper

Requires Python 3.12+.

```bash
cd services/scraper
pip install -r requirements.txt
python main.py
```

Prometheus metrics are exposed on port 8000 (`/metrics`).
The Kafka bootstrap server is configured via `KAFKA_BOOTSTRAP_SERVERS`.
Rate limiting delay is configurable via `SCRAPER_RATE_LIMIT_SECONDS` (default: 2s).

### menu-planner-ui

Requires Node 20 and yarn.

```bash
cd services/menu-planner-ui
yarn install
yarn dev        # development server on :3000
yarn build      # production build (standalone output)
yarn start      # serve the production build
```

Setup the `.env.local` to point at the right addresses for the other services. If running everything in a container then use `host.docker.internal` as the hostname of the supabase services, and the service name in the docker-compose for the backend.

```
# Server-side URL used by Next.js server and middleware (Docker internal address when running in a container)
SUPABASE_URL=http://host.docker.internal:54321
# Browser-accessible URL returned by /api/config — use localhost for local dev
SUPABASE_PUBLIC_URL=http://localhost:54321
SUPABASE_ANON_KEY=<your-anon-key>

# Backend services
API_GATEWAY_URL=http://java:8080
```

When the UI is running on the host and the others in containers:
```
# Server-side URL used by Next.js server and middleware (Docker internal address when running in a container)
SUPABASE_URL=http://localhost:54321
# Browser-accessible URL returned by /api/config — use localhost for local dev
SUPABASE_PUBLIC_URL=http://localhost:54321
SUPABASE_ANON_KEY=<your-anon-key>

# Backend services
API_GATEWAY_URL=http://localhost:8080

```

## Docker images

Each service has a `Dockerfile` at its root (recipe-service uses
`src/main/docker/Dockerfile.jvm`). Images are built and pushed to GHCR
automatically by CI — see [CI](#ci) below.

To build manually:

```bash
# recipe-service — must package first
cd services/recipie-service && ./mvnw package -DskipTests
docker build -f src/main/docker/Dockerfile.jvm -t meal-planner-recipe-service .

# scraper
docker build -t meal-planner-scraper services/scraper/

# ui
docker build -t meal-planner-ui services/menu-planner-ui/
```

## CI

Three GitHub Actions workflows in `.github/workflows/` build and push images
to the GitHub Container Registry (GHCR) on every push to `main`. Each workflow
is path-filtered so it only runs when its own service changes.

| Workflow | Image |
|---|---|
| `ci-recipe-service.yml` | `ghcr.io/cstdev/meal-planner-recipe-service` |
| `ci-scraper.yml` | `ghcr.io/cstdev/meal-planner-scraper` |
| `ci-ui.yml` | `ghcr.io/cstdev/meal-planner-ui` |

Images are tagged with the git SHA and `latest` (main branch only).
Authentication uses the built-in `GITHUB_TOKEN` — no additional secrets needed.

PRs trigger a build-only run (no push) to catch errors early.

## Running with Docker Compose

`docker-compose.yml` at the repo root starts PostgreSQL and Kafka for local
development. The application services are run separately (see above).

```bash
docker compose up -d
```

## Deployment

The app is deployed to Kubernetes via ArgoCD. The Helm chart lives in the
[homelab repo](https://github.com/CSTDev/homelab) under `services/meal-planner/`.
ArgoCD picks it up automatically via the `home` ApplicationSet when changes
are merged to the homelab `main` branch.

Each CI workflow pushes its image digest straight to `services/meal-planner/values.yaml`
in the homelab repo after a successful build on `main` (see the "Update homelab digest"
step, using the `HOMELAB_DEPLOY_PAT` secret), so a merge to `main` here results in an
automatic deploy with no manual digest editing.
