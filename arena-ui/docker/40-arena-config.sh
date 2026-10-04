#!/bin/sh
set -eu
case "${ARENA_ENV:-}" in
  sit) realm=arena-sit ;;
  prod) realm=arena ;;
  *) echo 'ARENA_ENV must be sit or prod for the deployed UI' >&2; exit 1 ;;
esac
# Only non-secret configuration is written. One image works in either environment.
config_file="${ARENAOPS_UI_CONFIG_FILE:-/usr/share/nginx/html/arena-config.js}"
printf 'window.ARENAOPS_CONFIG = {arenaEnv: "%s", keycloakRealm: "%s", keycloakBaseUrl: window.location.origin + "/auth"};\n' \
  "$ARENA_ENV" "$realm" > "$config_file"
