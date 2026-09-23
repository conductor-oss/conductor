#!/bin/sh
# Require completed workflows, except exact FAILED execution IDs validated by the tests.
set -eu

if [ "$#" -ne 1 ]; then
    printf '%s\n' "Usage: $0 <server-api-url>" >&2
    exit 2
fi
server_url=${1%/}

expected='[]'
if [ -n "${CONDUCTOR_PLAYBACK_EXPECTED_FAILURES:-}" ]; then
    jq -e 'type == "array" and all(.[]; type == "string" and length > 0)' \
        "$CONDUCTOR_PLAYBACK_EXPECTED_FAILURES" > /dev/null
    expected=$(cat "$CONDUCTOR_PLAYBACK_EXPECTED_FAILURES")
fi

# Only FAILED rows with an explicitly expected execution ID are excluded.
result=$(curl --silent --show-error --fail-with-body --get \
    --data-urlencode 'query=status IN (RUNNING,PAUSED,FAILED,TERMINATED,TIMED_OUT)' \
    --data-urlencode 'size=100' \
    "$server_url/workflow/search")

unexpected=$(printf '%s' "$result" | jq --argjson expected "$expected" '
    [.results[] | select(.status == "FAILED" and (.workflowId as $id | $expected | index($id) != null))] as $allowed
    | .totalHits -= ($allowed | length)
    | .results -= $allowed')
count=$(printf '%s' "$unexpected" | jq -r '.totalHits')
if [ "$count" -eq 0 ]; then
    printf '%s\n' 'PASS: every workflow completed or matched an expected failure'
    exit 0
fi

printf '%s' "$unexpected" | jq -r '.results[] | "\(.workflowType) \(.workflowId): \(.status) \(.reasonForIncompletion // "")"'
printf 'FAIL: %s workflows did not complete\n' "$count"
exit 1
