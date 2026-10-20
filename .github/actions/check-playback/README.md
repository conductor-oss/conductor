# Check LLM playback

Validate SDK examples against a dedicated playback server:

```yaml
- uses: conductor-oss/conductor/.github/actions/check-playback@main
  with:
    server-url: http://localhost:8080/api
```

Completed workflows pass. Failed workflows pass only when persisted tasks show
that a completed guardrail decision rejected the response and directly caused
the failed termination. Failed LLM tasks, unrelated failures, and unfinished
workflows fail. No SDK exception lists or example names are used.

Requires `curl` and `jq`. Run locally with:

```sh
sh .github/actions/check-playback/check-playback.sh http://localhost:8080/api
```
