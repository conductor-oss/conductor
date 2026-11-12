#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
    echo "Usage: bash $0 <Dockerfile|Dockerfile.next> <linux/amd64|linux/arm64> [runtime|jdk]" >&2
    exit 1
fi

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
dockerfile="$1"
platform="$2"
stage="${3:-runtime}"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT

javac --release 25 -d "$test_dir" "$repo_dir/docker/server/tests/RuntimeTrustStoreCheck.java"
image="conductor-runtime-security:${dockerfile//./-}-${platform//\//-}-${stage}"

# Test the actual Java stages without rebuilding the server JAR or UI for each architecture.
docker build --platform "$platform" --target "$stage" \
    --file "$repo_dir/docker/server/$dockerfile" --tag "$image" "$repo_dir"
docker run --rm --platform "$platform" --volume "$test_dir:/tests:ro" \
    "$image" java -cp /tests RuntimeTrustStoreCheck
