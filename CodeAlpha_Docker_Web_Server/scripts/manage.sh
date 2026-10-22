#!/usr/bin/env bash
# Helper for the container lifecycle: build -> run -> stop/start -> inspect -> remove.
# Every command is a plain docker CLI call, printed before it runs.
set -euo pipefail

IMAGE="${IMAGE:-codealpha-web-server:1.0.0}"
NAME="${NAME:-codealpha-web}"
PORT="${WEB_PORT:-8080}"

cd "$(dirname "$0")/.."

run() {
  echo "+ $*"
  "$@"
}

usage() {
  cat <<EOF
Usage: $(basename "$0") <command>

  build     Build the image ($IMAGE)
  run       Create and start the container on port $PORT
  stop      Stop the container (SIGTERM, then SIGKILL after 10s)
  start     Start a stopped container
  restart   Restart the container
  status    Show container state and health
  logs      Follow the container logs
  stats     One-off CPU / memory / network usage
  metrics   Nginx connection metrics (stub_status)
  test      Run the smoke tests against http://localhost:$PORT
  reload    Check the nginx config and reload it without downtime
  shell     Open a shell inside the container
  rm        Remove the container
  clean     Remove the container and the image
EOF
}

case "${1:-}" in
  build)
    run docker build -t "$IMAGE" .
    ;;
  run)
    run docker run -d \
      --name "$NAME" \
      -p "$PORT:8080" \
      --restart unless-stopped \
      --read-only --tmpfs /tmp \
      --cap-drop ALL \
      --security-opt no-new-privileges:true \
      --memory 128m --cpus 0.5 \
      "$IMAGE"
    ;;
  stop)
    run docker stop "$NAME"
    ;;
  start)
    run docker start "$NAME"
    ;;
  restart)
    run docker restart "$NAME"
    ;;
  status)
    run docker ps -a --filter "name=^${NAME}$" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
    run docker inspect --format 'state={{.State.Status}} health={{.State.Health.Status}} restarts={{.RestartCount}} started={{.State.StartedAt}}' "$NAME"
    ;;
  logs)
    run docker logs -f --tail 100 "$NAME"
    ;;
  stats)
    run docker stats --no-stream "$NAME"
    ;;
  metrics)
    run docker exec "$NAME" wget -qO- http://127.0.0.1:8080/nginx_status
    ;;
  test)
    run ./scripts/smoke-test.sh "http://localhost:$PORT"
    ;;
  reload)
    run docker exec "$NAME" nginx -t
    run docker exec "$NAME" nginx -s reload
    ;;
  shell)
    run docker exec -it "$NAME" sh
    ;;
  rm)
    run docker rm -f "$NAME"
    ;;
  clean)
    docker rm -f "$NAME" >/dev/null 2>&1 || true
    run docker rmi "$IMAGE"
    ;;
  *)
    usage
    exit 1
    ;;
esac
