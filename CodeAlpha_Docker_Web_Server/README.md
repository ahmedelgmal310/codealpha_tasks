# Task 4 - Web Server using Docker

A static website served by **Nginx** inside a **Docker** container. The container runs as a non-root user, has a built-in health check, and can be managed through its full lifecycle with plain `docker` commands, Docker Compose or the helper script in `scripts/`.

## What's in this folder

```
CodeAlpha_Docker_Web_Server/
├── Dockerfile               # image definition (nginx-unprivileged + site + health check)
├── docker-compose.yml       # same container with limits, hardening and log rotation
├── nginx/default.conf       # server block: security headers, gzip, /health, 404 page
├── site/                    # the website (HTML, CSS, JS)
├── scripts/
│   ├── manage.sh            # lifecycle helper: build, run, stop, start, logs, ...
│   ├── smoke-test.sh        # HTTP checks used locally and in CI
│   └── monitor.sh           # health/resource monitor with auto-restart
└── docs/TROUBLESHOOTING.md  # common problems and how to debug them
```

CI for this task lives in [`.github/workflows/task4-docker-web-server.yml`](../.github/workflows/task4-docker-web-server.yml). On every push it lints the Dockerfile, builds the image, validates the nginx config, starts the container, waits for it to become healthy and runs the smoke tests.

## Quick start

Requirements: Docker Desktop (Windows/macOS) or Docker Engine (Linux).

```bash
cd CodeAlpha_Docker_Web_Server

# Option 1: Docker Compose
docker compose up -d --build
docker compose ps

# Option 2: plain docker
docker build -t codealpha-web-server:1.0.0 .
docker run -d --name codealpha-web -p 8080:8080 codealpha-web-server:1.0.0
```

Open http://localhost:8080. The page polls `/health` and shows the server status.

Port 8080 already taken? Use another one: `WEB_PORT=9090 docker compose up -d`.

## How the image is built

| Decision | Why |
|---|---|
| `nginxinc/nginx-unprivileged:1.28-alpine` base | Small Alpine image, Nginx already configured to run as UID 101 on port 8080 |
| Site files copied as root-owned | Nginx can read the files but can't modify them, even if compromised |
| `server_tokens off` | Hides the Nginx version from headers and error pages |
| Security headers | `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Content-Security-Policy` |
| gzip for text assets | Smaller responses for HTML/CSS/JS/JSON |
| `/health` endpoint | Cheap check that doesn't touch the disk, used by `HEALTHCHECK` |
| `/nginx_status` (localhost only) | Active connections and request counters for monitoring |

## Container lifecycle

```
Dockerfile ──docker build──▶ image ──docker run──▶ running
running    ──docker stop───▶ exited ──docker start──▶ running
running    ──docker restart──────────────────────────▶ running
exited     ──docker rm─────▶ removed
```

Every step with the helper script (it prints the real `docker` command before running it):

```bash
./scripts/manage.sh build      # docker build -t codealpha-web-server:1.0.0 .
./scripts/manage.sh run        # docker run -d --name codealpha-web -p 8080:8080 --read-only ...
./scripts/manage.sh status     # docker ps + state, health and restart count
./scripts/manage.sh logs       # docker logs -f --tail 100 codealpha-web
./scripts/manage.sh stop       # docker stop codealpha-web
./scripts/manage.sh start      # docker start codealpha-web
./scripts/manage.sh restart    # docker restart codealpha-web
./scripts/manage.sh reload     # nginx -t && nginx -s reload (no downtime)
./scripts/manage.sh shell      # docker exec -it codealpha-web sh
./scripts/manage.sh rm         # docker rm -f codealpha-web
./scripts/manage.sh clean      # remove container and image
```

On Windows run the scripts from **Git Bash** or WSL.

## Health checks and monitoring

The `HEALTHCHECK` in the Dockerfile requests `/health` every 15 seconds. After 3 failures in a row Docker marks the container `unhealthy`.

```bash
# current health and the last few check results
docker inspect --format '{{json .State.Health}}' codealpha-web

# live CPU / memory / network / IO
docker stats codealpha-web

# Nginx metrics (active connections, accepted/handled requests)
./scripts/manage.sh metrics

# events from the Docker daemon (start, die, health_status, ...)
docker events --filter container=codealpha-web
```

Plain Docker reports an unhealthy container but does **not** restart it (only Swarm/Kubernetes do). `scripts/monitor.sh` fills that gap: it prints state, health, CPU and memory on an interval and restarts the container after 3 unhealthy checks in a row.

```bash
./scripts/monitor.sh codealpha-web 10 3
```

## Testing

```bash
./scripts/smoke-test.sh http://localhost:8080
```

It checks status codes (`/`, `/health`, assets, 404 page, `/nginx_status` blocked from outside), the security headers, caching headers, gzip and that the Nginx version is hidden.

## Best practices used

- Pinned base image version instead of `latest`
- Non-root user, read-only root filesystem with a `tmpfs` for `/tmp`
- All Linux capabilities dropped, `no-new-privileges` enabled
- CPU and memory limits so one container can't starve the host
- Log rotation (`max-size` / `max-file`) for the json-file driver
- `.dockerignore` to keep the build context small
- `restart: unless-stopped` so the server comes back after a reboot or crash
- Config validated with `nginx -t` in CI before anything is deployed

See [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) for debugging common problems.
