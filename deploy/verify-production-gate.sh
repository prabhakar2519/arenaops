#!/usr/bin/env bash
# Fail closed if production approval settings are missing or cannot be verified.
set -euo pipefail
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${GH_TOKEN:?GitHub API token is required}"
reviewer_count="$(gh api "repos/$GITHUB_REPOSITORY/environments/production" \
  --jq '[.protection_rules[]? | select(.type == "required_reviewers") | .reviewers[]?] | length')"
if [[ ! "$reviewer_count" =~ ^[1-9][0-9]*$ ]]; then
  echo 'Production requires a GitHub Environment with at least one required reviewer. Configure it before releasing.' >&2
  exit 1
fi
