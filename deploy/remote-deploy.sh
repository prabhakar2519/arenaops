#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
environment="${1:?sit or prod is required}"
case "$environment" in sit|prod) ;; *) exit 1 ;; esac
runtime_dir="$(mktemp -d /dev/shm/arenaops-app.XXXXXX)"
trap 'rm -rf "$runtime_dir"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP
export ARENAOPS_APP_ENV_FILE="$runtime_dir/runtime.env"
export DOCKER_CONFIG="$runtime_dir/docker"
mkdir -m 700 "$DOCKER_CONFIG"
cat > "$ARENAOPS_APP_ENV_FILE"
deployment_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Reject a mismatched environment before authenticating or changing running services.
python3 - "$ARENAOPS_APP_ENV_FILE" "$environment" "$deployment_dir" <<'PY'
import sys
from pathlib import Path
sys.path.insert(0, sys.argv[3])
from config import load_runtime, configuration
try:
    _, _, settings = configuration(load_runtime(sys.argv[1]))
    if settings['ARENA_ENV'] != sys.argv[2] or Path(sys.argv[3]) != Path(settings['ARENAOPS_TARGET']) / 'app/deploy':
        raise ValueError('Deployment environment or directory mismatch')
except Exception:
    print('Invalid application deployment target or configuration', file=sys.stderr)
    sys.exit(1)
PY
python3 "$deployment_dir/registry-login.py" "$ARENAOPS_APP_ENV_FILE"
bash "$deployment_dir/deploy-app.sh"
