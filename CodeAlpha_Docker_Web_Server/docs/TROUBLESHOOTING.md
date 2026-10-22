# Troubleshooting

A short checklist for the problems you are most likely to hit with this container.

## First things to look at

```bash
docker ps -a --filter name=codealpha-web           # is it running? what's the status?
docker logs --tail 50 codealpha-web                # nginx access + error log (both go to stdout/stderr)
docker inspect --format '{{json .State}}' codealpha-web   # exit code, OOMKilled, health log
```

## Container exits right after starting

Check the exit code and the logs:

```bash
docker inspect --format 'exit={{.State.ExitCode}} oom={{.State.OOMKilled}} error={{.State.Error}}' codealpha-web
docker logs codealpha-web
```

| Symptom in logs | Cause | Fix |
|---|---|---|
| `[emerg] unknown directive` / `unexpected "}"` | Syntax error in `nginx/default.conf` | Run `docker run --rm codealpha-web-server:1.0.0 nginx -t` and fix the line it points to |
| `bind() to 0.0.0.0:80 failed (13: Permission denied)` | Config listens on a port below 1024 as a non-root user | Keep `listen 8080;` and map the host port instead (`-p 80:8080`) |
| `open() "/var/cache/nginx/..." failed (30: Read-only file system)` | Read-only root filesystem without a writable temp dir | Add `--tmpfs /tmp` (the unprivileged image keeps its temp files there) |
| `OOMKilled=true` | Memory limit too low | Raise `--memory` / `deploy.resources.limits.memory` |

## "Port is already allocated"

```
Bind for 0.0.0.0:8080 failed: port is already allocated
```

Something else is using 8080. Find it or pick another port:

```bash
docker ps --filter publish=8080        # another container?
netstat -ano | findstr :8080           # Windows
sudo lsof -i :8080                     # Linux / macOS

WEB_PORT=9090 docker compose up -d
```

## Container is "unhealthy"

```bash
docker inspect --format '{{range .State.Health.Log}}{{.End}} exit={{.ExitCode}} {{.Output}}{{"\n"}}{{end}}' codealpha-web
docker exec codealpha-web wget -qO- http://127.0.0.1:8080/health
```

- If `wget` inside the container fails, nginx is not serving: check `docker logs`.
- If it works inside but the page doesn't load in the browser, the problem is the port mapping or a firewall, not the container.

## 403 Forbidden on a page

- The file doesn't exist and there's no `index.html` in that folder, or
- the file isn't readable by UID 101. Files copied by the Dockerfile are world-readable by default; if you mount a volume, check its permissions with `docker exec codealpha-web ls -l /usr/share/nginx/html`.

## Changes to the site or config don't show up

The site and config are baked into the image at build time. Rebuild and recreate:

```bash
docker compose up -d --build
```

Because the container runs with a read-only filesystem, `docker cp` into it will fail on purpose: rebuilding is the way to ship a change. To try a config change before rebuilding, validate it in a throwaway container first:

```bash
docker run --rm -v "$PWD/nginx/default.conf:/etc/nginx/conf.d/default.conf:ro" codealpha-web-server:1.0.0 nginx -t
```

Also hard-refresh the browser (Ctrl+F5): assets under `/assets/` are cached for 7 days.

## Getting a shell inside the container

```bash
docker exec -it codealpha-web sh
# useful inside:
nginx -T                  # print the full effective config
ps aux                    # nginx master + worker processes, running as nginx
wget -S -O /dev/null http://127.0.0.1:8080/   # request with response headers
```

## Cleaning up

```bash
docker compose down                 # stop and remove the container + network
docker image prune                  # remove dangling images left from rebuilds
docker system df                    # see how much disk Docker is using
```
