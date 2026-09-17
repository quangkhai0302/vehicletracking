#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${VEHICLETRACKING_DIR:-$HOME/vehicletracking}"
ENV_FILE="$PROJECT_DIR/.env.production"
COMPOSE_FILE="$PROJECT_DIR/compose.production.yaml"
LOCK_FILE="/tmp/vehicletracking-production-deploy.lock"

exec 9>"$LOCK_FILE"
if ! flock -w 1800 9; then
  echo "Another deployment still holds $LOCK_FILE" >&2
  exit 1
fi

cd "$PROJECT_DIR"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing production environment file: $ENV_FILE" >&2
  exit 1
fi

if [[ "$(stat -c '%a' "$ENV_FILE")" != "600" ]]; then
  echo "Refusing to deploy because .env.production must have permission 600" >&2
  exit 1
fi

if grep -q 'REPLACE_WITH' "$ENV_FILE"; then
  echo "Refusing to deploy while .env.production still contains placeholders" >&2
  exit 1
fi

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
"${compose[@]}" config --quiet
"${compose[@]}" build
"${compose[@]}" up -d --remove-orphans

healthy=false
for _ in $(seq 1 60); do
  if curl --fail --silent --show-error \
      http://localhost/api/v1/telemetry/snapshot >/dev/null; then
    healthy=true
    break
  fi
  sleep 3
done

if [[ "$healthy" != "true" ]]; then
  echo "Application health check failed after 180 seconds" >&2
  "${compose[@]}" ps >&2 || true
  "${compose[@]}" logs --tail=150 backend frontend postgres >&2 || true
  exit 1
fi

docker image prune --force
"${compose[@]}" ps
echo "VehicleTracking deployment is healthy."
