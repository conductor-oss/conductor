#!/bin/sh
# Validate playback outcomes from persisted workflow state, for every SDK.
set -eu
if [ "$#" -ne 1 ]; then
    printf '%s\n' "Usage: $0 <server-api-url>" >&2
    exit 2
fi
server_url=${1%/}
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
start=0
failures=0
while :; do
    result=$(curl --silent --show-error --fail-with-body --get \
        --data-urlencode 'query=status IN (COMPLETED,RUNNING,PAUSED,FAILED,TERMINATED,TIMED_OUT)' \
        --data-urlencode 'size=100' --data-urlencode "start=$start" \
        "$server_url/workflow/search")
    total=$(printf '%s' "$result" | jq -er '.totalHits')
    rows=$(printf '%s' "$result" | jq '.results | length')
    if [ "$rows" -eq 0 ]; then
        if [ "$start" -lt "$total" ]; then
            printf '%s\n' 'FAIL: workflow search returned an incomplete page'
            exit 1
        fi
        break
    fi
    for id in $(printf '%s' "$result" | jq -r '.results[].workflowId | @uri'); do
        workflow=$(curl --silent --show-error --fail-with-body "$server_url/workflow/$id?includeTasks=true")
        if printf '%s' "$workflow" | jq -e '.status == "COMPLETED"' > /dev/null; then
            continue
        fi
        if printf '%s' "$workflow" | jq -e -f "$script_dir/guardrail-rejection.jq" > /dev/null; then
            printf 'PASS: %s rejected by its guardrail\n' "$id"
        else
            printf '%s' "$workflow" | jq -r '"\(.workflowType) \(.workflowId): \(.status) \(.reasonForIncompletion // "")"'
            failures=$((failures + 1))
        fi
    done
    start=$((start + rows))
    [ "$start" -lt "$total" ] || break
done
if [ "$start" -eq 0 ]; then
    printf '%s\n' 'FAIL: no workflows found; the SDK examples never ran'
    exit 1
fi
if [ "$failures" -ne 0 ]; then
    printf 'FAIL: %s unexpected workflow outcomes\n' "$failures"
    exit 1
fi
printf '%s\n' 'PASS: every workflow completed or was rejected by its guardrail'
