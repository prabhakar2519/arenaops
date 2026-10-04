#!/usr/bin/env bash
# SSH paths intentionally expand on the runner; environment and run identifiers are validated.
# shellcheck disable=SC2029
set -euo pipefail
set +x
umask 077
case "${TARGET_ENVIRONMENT:-}" in sit) export ARENA_ENV=sit ;; production) export ARENA_ENV=prod ;; *) echo 'Invalid deployment environment' >&2; exit 1 ;; esac
: "${VPS_HOST:?VPS_HOST is required}"
: "${VPS_USERNAME:?VPS_USERNAME is required}"
: "${VPS_SSH_KEY:?VPS_SSH_KEY is required}"
: "${VPS_SSH_HOST_KEY:?Pinned SSH host key is required}"
[[ "${GITHUB_RUN_ID:-}" =~ ^[0-9]+$ && "${GITHUB_RUN_ATTEMPT:-}" =~ ^[0-9]+$ ]] || { echo 'GitHub run identifiers are required' >&2; exit 1; }
runtime_dir="$(mktemp -d /dev/shm/arenaops-app-runner.XXXXXX)"
trap 'rm -rf "$runtime_dir"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP
printf '%s\n' "$VPS_SSH_KEY" > "$runtime_dir/key"
printf '%s\n' "$VPS_SSH_HOST_KEY" > "$runtime_dir/known_hosts"
export IMAGE_REPOSITORY="ghcr.io/${GITHUB_REPOSITORY,,}"
python3 deploy/write-runtime-env.py "$runtime_dir/runtime.env"
ssh_options=(-i "$runtime_dir/key" -o BatchMode=yes -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$runtime_dir/known_hosts")
remote="$VPS_USERNAME@$VPS_HOST"
staging="/tmp/arenaops-$ARENA_ENV-app-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
target="/opt/arenaops/$ARENA_ENV/app"
# No host/network/infrastructure preparation here; the operator owns VPS setup.
tar --exclude='deploy/tests' --exclude='*/__pycache__' -czf - deploy | ssh "${ssh_options[@]}" "$remote" \
  "set -eu; umask 077; mkdir -p '$staging'; tar -xzf - -C '$staging'"
ssh "${ssh_options[@]}" "$remote" \
  "set -eu; test -f '/opt/arenaops/$ARENA_ENV/state/$ARENA_ENV-infrastructure-applied'; install -d -m 0750 '$target'; cp -a '$staging/deploy' '$target/'; rm -rf '$staging'"
ssh "${ssh_options[@]}" "$remote" \
  "bash '$target/deploy/remote-deploy.sh' '$ARENA_ENV'" < "$runtime_dir/runtime.env"
