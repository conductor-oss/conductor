#!/usr/bin/env bash
# Run integration Playwright inside Linux Chromium Docker so screenshot
# baselines match CI. Forwards extra args to playwright (e.g. a spec path).
#
# Usage:
#   ./scripts/run-integration-e2e.sh
#   ./scripts/run-integration-e2e.sh e2e/integration/workflows.spec.ts
#   PLAYWRIGHT_FLAGS=--update-snapshots ./scripts/run-integration-e2e.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Export keys from .env / .env.local so compose can interpolate OPENAI_API_KEY
# into conductor-server and playwright (CI/shell exports still win).
load_env_file() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    [[ "$line" != *=* ]] && continue
    local key="${line%%=*}"
    local value="${line#*=}"
    key="${key%"${key##*[![:space:]]}"}"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    if [[ -z "${!key+x}" ]]; then
      export "$key=$value"
    fi
  done <"$file"
}
load_env_file .env
load_env_file .env.local

COMPOSE_PROJECT="${COMPOSE_PROJECT:-conductor-ui-e2e-integration}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.integration.yml}"
COMPOSE=(docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE")

cleanup() {
  "${COMPOSE[@]}" down --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

ensure_server_image() {
  if docker image inspect conductor:server >/dev/null 2>&1; then
    return 0
  fi
  echo "conductor:server image not found — building (first run ~5–10 min) ..."
  # Build via the integration compose definition (context = repo root).
  "${COMPOSE[@]}" build conductor-server
}

ensure_dist() {
  if [[ "${SKIP_WEBSERVER_BUILD:-}" == "true" && -d dist ]]; then
    echo "SKIP_WEBSERVER_BUILD=true — reusing existing dist/"
    return 0
  fi
  if [[ -d dist && -z "${E2E_COVERAGE:-}" && "${FORCE_UI_BUILD:-}" != "true" ]]; then
    # Reuse dist when coverage is not requested and build was not forced.
    # CI always sets E2E_COVERAGE and pre-builds; local iteration can skip rebuild.
    if [[ -f dist/index.html ]]; then
      echo "Reusing existing dist/ (set FORCE_UI_BUILD=true to rebuild)"
      return 0
    fi
  fi
  echo "Building UI (dist/) ..."
  if [[ -n "${E2E_COVERAGE:-}" ]]; then
    E2E_COVERAGE=true NODE_OPTIONS="${NODE_OPTIONS:---max-old-space-size=8192}" pnpm build
  else
    NODE_OPTIONS="${NODE_OPTIONS:---max-old-space-size=8192}" pnpm build
  fi
}

ensure_server_image
ensure_dist

# Forward remaining CLI args into PLAYWRIGHT_FLAGS so `pnpm test:e2e:integration -- grep`
# / spec paths work. Existing PLAYWRIGHT_FLAGS (e.g. --update-snapshots) are preserved.
EXTRA_FLAGS=("$@")
if [[ ${#EXTRA_FLAGS[@]} -gt 0 ]]; then
  export PLAYWRIGHT_FLAGS="${PLAYWRIGHT_FLAGS:-} ${EXTRA_FLAGS[*]}"
fi

echo "Starting integration stack (project: $COMPOSE_PROJECT) ..."
# `run --rm` starts depends_on services, runs playwright, then removes the
# one-off container. trap cleans up the rest of the stack.
"${COMPOSE[@]}" run --rm playwright
