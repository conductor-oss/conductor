#!/usr/bin/env bash
set -euo pipefail

# ── Run the python SDK's e2e suite in place, from a checkout of its repo ──────
#
# Invoked exactly the way conductor-oss/python-sdk's own agent-e2e.yml invokes it, against
# that repo's own build files. Nothing about the suite's dependencies is restated here —
# no generated requirements.txt — so an upstream dep change or version bump can't leave
# us testing something stale. If upstream breaks its own entrypoint, its CI goes red
# before ours does.
#
# The SDK is therefore built from the checked-out source, not resolved from PyPI. That
# means the ref can be a tag, a branch or a SHA with no "is it published yet" constraint —
# and it means this lane tests the SDK at that ref rather than its packaged artifact.
# Packaging is the SDK repo's own release CI to cover; this lane exists to catch
# conductor-server incompatibilities.
#
# Services must already be up. CONDUCTOR_SERVER_URL, CONDUCTOR_AGENT_LLM_MODEL,
# MCP_TESTKIT_URL and SCHEDULER_CONDUCTOR_URL are read from the environment by the suite
# itself, so they just need to be exported.
#
# JUnit XML lands at <checkout>/results/junit-e2e.xml.
#
# Usage: run-suite.sh <sdk-checkout-dir> [pytest args...]
#
# Trailing args go straight to pytest, which is how the known-failure xfails are applied
# without touching the upstream suite (-p known_failures_plugin).

# GitHub annotation under Actions, plain text when run by hand.
fail() { if [[ -n "${GITHUB_ACTIONS:-}" ]]; then echo "::error::$*"; else echo "ERROR: $*" >&2; fi; exit 1; }

[[ $# -ge 1 ]] || { echo "usage: $0 <sdk-checkout-dir> [pytest args...]" >&2; exit 2; }

SRC="$(cd "$1" 2>/dev/null && pwd)" || fail "checkout dir '$1' does not exist"
shift

# Fail loudly when upstream's entrypoint isn't where we expect. This is the coupling that
# remains after dropping the generated manifest, so it gets an explicit check rather than
# a confusing error from pip or pytest.
require_path() {
  [[ -e "$SRC/$1" ]] || fail "expected $2 at '$1' in the SDK checkout, but it is missing. The upstream layout moved — update run-suite.sh."
}

cd "$SRC"

require_path e2e "the pytest suite"
require_path setup.py "the SDK package"

# Bare `python`, not python3: CI gets it from actions/setup-python, and a local run needs
# an activated venv. Checked here so the failure names the cause.
command -v python >/dev/null 2>&1 || fail \
  "python is not on PATH — this lane calls bare \`python\`; activate a 3.10-3.13 venv"

# The test-runner packages are the one dep set still named here: python-sdk keeps them
# inline in its workflow rather than in a requirements file, so there is nothing to point at.
# The pytest ones are generic runners with nothing to drift, but mcp-testkit is pinned: it
# declares `mcp[cli]>=1.0.0` with no upper bound, and mcp 2.0.0 dropped mcp.server.fastmcp,
# which the testkit imports at start-up — so an unpinned install yields a testkit that exits
# immediately and every MCP suite reports the server unreachable and skips. Same pin as #1408.
python -m pip install --quiet --upgrade pip
python -m pip install --quiet -e '.[agents]'
python -m pip install --quiet pytest pytest-asyncio pytest-xdist pytest-rerunfailures \
  'mcp-testkit==1.0.3' 'mcp<2'

mkdir -p results
python -m pytest e2e/ -v --tb=short -n 3 --dist=loadgroup \
  --junitxml=results/junit-e2e.xml "$@"
