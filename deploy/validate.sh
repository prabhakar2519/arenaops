#!/usr/bin/env bash
set -euo pipefail
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"
for script in deploy/*.sh arena-ui/docker/*.sh; do bash -n "$script"; done
python3 -m unittest discover -s deploy/tests -v
