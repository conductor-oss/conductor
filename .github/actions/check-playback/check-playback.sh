#!/bin/sh
# Check recordings bundled with this action against an already-running playback server.
set -eu

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
    printf '%s\n' "Usage: $0 <server-api-url> [recordings-directory]" >&2
    exit 2
fi
server_url=${1%/}
case "$server_url" in
    http://*|https://*) ;;
    *) printf '%s\n' 'Server URL must start with http:// or https://' >&2; exit 2 ;;
esac
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
recordings=${2:-"$script_dir/../../../llm-recordings"}
for command in curl find sha256sum mktemp; do
    command -v "$command" >/dev/null 2>&1 || {
        printf 'Required system command is missing: %s\n' "$command" >&2
        exit 2
    }
done
inventory=$(mktemp)
report=$(mktemp)
trap 'rm -f "$inventory" "$report"' 0
trap 'exit 1' 1 2 15

# Hash the action's own fixtures, independent of the SDK checkout and its working directory.
(cd "$recordings" && find . -type f -name '*.json' -exec sha256sum {} +) > "$inventory"
if [ ! -s "$inventory" ]; then
    printf '%s\n' 'No recording JSON files found; refusing an empty verification.' >&2
    exit 2
fi
set -- --silent --show-error --fail-with-body --connect-timeout 10 --max-time 60 \
    --request POST --header 'Content-Type: text/plain' --data-binary "@$inventory"
if [ -n "${CONDUCTOR_AUTH_HEADER:-}" ]; then
    set -- "$@" --header "$CONDUCTOR_AUTH_HEADER"
fi
# HTTP 409 reports missing, changed, or unplayed recordings and unmatched requests.
# curl propagates a nonzero exit status directly to CI; no JSON parser is needed.
if http_status=$(curl "$@" --output "$report" --write-out '%{http_code}' "$server_url/llm/playback/verify"); then
    cat "$report"
    if [ "$http_status" != 200 ]; then
        printf 'Expected HTTP 200 from playback verification; received %s\n' "$http_status" >&2
        exit 1
    fi
else
    result=$?
    cat "$report"
    exit "$result"
fi
