#!/usr/bin/env bash
set -euo pipefail
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
bundle="$root_dir/arena-ui/dist/arena-ui/browser"
[[ -f "$bundle/index.html" ]] || { echo 'Build the UI before running container validation' >&2; exit 1; }
for environment in sit prod; do
  docker run --rm -i --network none --entrypoint sh \
    -e ARENA_ENV="$environment" -v "$bundle:/build:ro" \
    -v "$root_dir/arena-ui/docker/nginx.conf:/etc/nginx/conf.d/default.conf:ro" \
    -v "$root_dir/arena-ui/docker/40-arena-config.sh:/40-arena-config.sh:ro" nginx:alpine -s <<'TEST'
set -eu
cp -a /build/. /usr/share/nginx/html/
sh /40-arena-config.sh
nginx -t
nginx >/tmp/arena-nginx-validation.log 2>&1
trap 'nginx -s quit >/dev/null 2>&1' EXIT
wget -q -O - http://127.0.0.1/ | grep -q 'src="arena-config.js"'
config="$(wget -q -O - http://127.0.0.1/arena-config.js)"
headers="$(wget -S -O /dev/null http://127.0.0.1/arena-config.js 2>&1)"
echo "$headers" | grep -qi 'Cache-Control: no-store'
case "$ARENA_ENV" in sit) echo "$config" | grep -q 'keycloakRealm: "arena-sit"' ;; prod) echo "$config" | grep -q 'keycloakRealm: "arena"' ;; esac
for path in /auth/admin/ /auth/realms/master/ /api /api/test; do
  status="$(wget -S -O /dev/null "http://127.0.0.1$path" 2>&1 | sed -n 's/.*HTTP\/1.1 \([0-9]*\).*/\1/p' | head -n 1)"
  [ "$status" = 404 ] || { echo "Unexpected Keycloak proxy exposure: $path" >&2; exit 1; }
done
echo "$ARENA_ENV UI config and private auth routing validated"
TEST
done
