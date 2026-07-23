#!/usr/bin/env bash
# Check internal and external links in the public documentation locally.
# This is intentionally not part of CI: external services can be transient.

set -euo pipefail

if ! command -v lychee >/dev/null 2>&1; then
  echo "lychee is required for documentation link checks."
  echo "Install it with: brew install lychee"
  exit 127
fi

lychee --cache=false --config docs/linkcheck.toml --scheme http --scheme https docs
