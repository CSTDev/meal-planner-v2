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
