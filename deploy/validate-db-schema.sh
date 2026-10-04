#!/usr/bin/env bash
# Local disposable PostgreSQL validation only. Never connects to a VPS or PROD.
set -euo pipefail
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
container="arenaops-schema-validation-$$"
trap 'docker rm -f "$container" >/dev/null 2>&1 || true' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
docker run -d --rm --name "$container" -p 127.0.0.1::5432 \
  -e POSTGRES_USER=validation -e POSTGRES_PASSWORD=validation postgres:16-alpine >/dev/null
ready=false
for ((attempt = 0; attempt < 30; attempt++)); do
  if docker exec "$container" pg_isready -h 127.0.0.1 -U validation >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
[[ "$ready" == true ]] || { echo 'Disposable PostgreSQL did not become ready' >&2; exit 1; }
for database in arena_dev arena_sit arena arena_legacy; do
  docker exec "$container" psql -U validation -d validation -v ON_ERROR_STOP=1 \
    -c "CREATE DATABASE $database" >/dev/null
  # Prove the cluster starts with no pre-created application schema.
  count="$(docker exec "$container" psql -U validation -d "$database" -At \
    -c "SELECT count(*) FROM pg_namespace WHERE nspname = '$database'")"
  [[ "$count" == 0 ]] || { echo 'Application schema unexpectedly pre-exists' >&2; exit 1; }
done
port="$(docker port "$container" 5432/tcp | sed -n 's/^127\.0\.0\.1://p')"
[[ "$port" =~ ^[0-9]+$ ]] || { echo 'Cannot determine isolated PostgreSQL port' >&2; exit 1; }
ARENA_SCHEMA_TEST_DB_URL="jdbc:postgresql://127.0.0.1:$port/" \
ARENA_BILLING_DB_URL="jdbc:postgresql://127.0.0.1:$port/arena_sit" \
ARENA_BILLING_DB_USER=validation ARENA_BILLING_DB_PASSWORD=validation \
  mvn --batch-mode -f "$root_dir/arena-core/pom.xml" verify "$@"
