#!/usr/bin/env bash
# Mock MVC only: no service listeners, external infrastructure or credentials.
set -euo pipefail
api_repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$api_repo_root"
api_export_mode="${1:---check}"
if [[ $# -gt 0 ]]; then shift; fi
api_update_args=()
case "$api_export_mode" in
  --check) ;;
  --update) api_update_args=(-Darena.openapi.update=true) ;;
  *) echo 'Usage: scripts/export-openapi.sh [--check|--update] [Maven options]' >&2; exit 2 ;;
esac
for api_module in arena-core arena-login; do
  mvn --batch-mode -f "$api_module/pom.xml" -Dtest=OpenApiContractTest,DocumentationSecurityTest "${api_update_args[@]}" "$@" test
done
if [[ "$api_export_mode" == --update ]]; then
  python3 scripts/validate-openapi.py --update
else
  python3 scripts/validate-openapi.py
fi
