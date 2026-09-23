#!/bin/sh
# Did every workflow on the playback server complete? PASS if yes, FAIL if not.
set -eu

if [ "$#" -ne 1 ]; then
    printf '%s\n' "Usage: $0 <server-api-url>" >&2
    exit 2
fi
server_url=${1%/}

# Anything that is not COMPLETED means playback did not work for that workflow.
result=$(curl --silent --show-error --fail-with-body --get \
    --data-urlencode 'query=status IN (RUNNING,PAUSED,FAILED,TERMINATED,TIMED_OUT)' \
    --data-urlencode 'size=100' \
    "$server_url/workflow/search")

count=$(printf '%s' "$result" | jq -r '.totalHits')
if [ "$count" -eq 0 ]; then
    printf '%s\n' 'PASS: every workflow completed'
    exit 0
fi

printf '%s' "$result" | jq -r '.results[] | "\(.workflowType) \(.workflowId): \(.status) \(.reasonForIncompletion // "")"'
printf 'FAIL: %s workflows did not complete\n' "$count"
exit 1
