#!/usr/bin/env bash
set -euo pipefail

deployment_dir="/opt/arenaops/app/deploy"
compose_file="$deployment_dir/docker-compose.prod.yaml"
state_dir="/opt/arenaops/state"
previous_release_file="$state_dir/previous-app-release"
current_release_file="$state_dir/current-app-release"

: "${IMAGE_REPOSITORY:?IMAGE_REPOSITORY is required}"
: "${IMAGE_TAG:?IMAGE_TAG is required}"
: "${ARENAOPS_APP_ENV_FILE:?GitHub-provided runtime environment file is required}"
test -f "$ARENAOPS_APP_ENV_FILE"
mkdir -p "$state_dir"

if [[ -f "$current_release_file" ]]; then
  cp "$current_release_file" "$previous_release_file"
fi
printf '%s|%s\n' "$IMAGE_REPOSITORY" "$IMAGE_TAG" > "$current_release_file"

export IMAGE_REPOSITORY IMAGE_TAG
docker compose -f "$compose_file" pull
docker compose -f "$compose_file" up -d --remove-orphans

for attempt in $(seq 1 18); do
  if curl -fsS http://127.0.0.1:8081/ >/dev/null; then
    echo "ArenaOps release $IMAGE_TAG is healthy"
    exit 0
  fi
  sleep 5
done

echo "ArenaOps UI health check failed for release $IMAGE_TAG" >&2
docker compose -f "$compose_file" ps >&2
exit 1
