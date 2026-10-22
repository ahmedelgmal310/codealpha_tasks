#!/usr/bin/env bash
# Watches the container's health status and resource usage.
#
# Plain Docker (without Swarm/Kubernetes) marks a container "unhealthy" but does not
# restart it. This script restarts the container after N unhealthy checks in a row.
#
# Usage: ./scripts/monitor.sh [container-name] [interval-seconds] [max-unhealthy]
set -uo pipefail

NAME="${1:-codealpha-web}"
INTERVAL="${2:-10}"
MAX_UNHEALTHY="${3:-3}"

unhealthy_count=0

echo "Monitoring '$NAME' every ${INTERVAL}s (restart after $MAX_UNHEALTHY unhealthy checks). Ctrl+C to stop."
printf '%-20s %-10s %-10s %-8s %-22s\n' "TIME" "STATE" "HEALTH" "CPU" "MEMORY"

while true; do
  now=$(date '+%Y-%m-%d %H:%M:%S')

  if ! state=$(docker inspect --format '{{.State.Status}}' "$NAME" 2>/dev/null); then
    echo "$now  container '$NAME' not found"
    sleep "$INTERVAL"
    continue
  fi

  health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$NAME")
  usage=$(docker stats --no-stream --format '{{.CPUPerc}}|{{.MemUsage}}' "$NAME" 2>/dev/null || echo "-|-")

  printf '%-20s %-10s %-10s %-8s %-22s\n' "$now" "$state" "$health" "${usage%%|*}" "${usage#*|}"

  if [ "$health" = "unhealthy" ]; then
    unhealthy_count=$((unhealthy_count + 1))
    if [ "$unhealthy_count" -ge "$MAX_UNHEALTHY" ]; then
      echo "$now  '$NAME' unhealthy $unhealthy_count times in a row, last health check output:"
      docker inspect --format '{{range .State.Health.Log}}{{.End}} exit={{.ExitCode}} {{.Output}}{{end}}' "$NAME" | tail -n 3
      echo "$now  restarting '$NAME'"
      docker restart "$NAME" >/dev/null
      unhealthy_count=0
    fi
  else
    unhealthy_count=0
  fi

  sleep "$INTERVAL"
done
