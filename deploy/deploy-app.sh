#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
deployment_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
: "${ARENAOPS_APP_ENV_FILE:?ARENAOPS_APP_ENV_FILE is required}"
exports="$(python3 "$deployment_dir/config.py" "$ARENAOPS_APP_ENV_FILE")"
eval "$exports"
[[ "$deployment_dir" == "$ARENAOPS_TARGET/app/deploy" ]] || { echo 'Unexpected application deployment directory' >&2; exit 1; }
state_dir="$ARENAOPS_TARGET/state"
[[ -f "$state_dir/$ARENA_ENV-infrastructure-applied" ]] || { echo 'Selected environment infrastructure must be applied first' >&2; exit 1; }
docker network inspect "$ARENAOPS_NETWORK" >/dev/null
compose() {
  docker compose --env-file /dev/null --project-name "$COMPOSE_PROJECT_NAME" \
    -f "$deployment_dir/docker-compose.app.yaml" "$@"
}
compose config --quiet
compose pull
compose up -d --wait --wait-timeout 360 --remove-orphans
# Record successful releases only; failed deployments retain the last successful state.
current_release_file="$state_dir/current-app-release"
previous_release_file="$state_dir/previous-app-release"
new_release_file="$(mktemp "$state_dir/current-app-release.XXXXXX")"
trap 'rm -f "$new_release_file"' EXIT
release_coordinates="$IMAGE_REPOSITORY|$IMAGE_TAG"
if [[ -f "$current_release_file" && "$(cat "$current_release_file")" != "$release_coordinates" ]]; then
  cp "$current_release_file" "$previous_release_file"
fi
printf '%s\n' "$release_coordinates" > "$new_release_file"
mv "$new_release_file" "$current_release_file"
echo "$ARENA_ENV application release $IMAGE_TAG is healthy"
