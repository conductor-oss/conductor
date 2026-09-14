# Check shared LLM playback

Use this action after an SDK has registered and run the shared examples. It verifies that every recording bundled under `llm-recordings/` was loaded unchanged and used by the server's mock provider. Every SDK uses the same files; no execution-ID list or SDK-specific manifest is needed.

```yaml
- name: Check shared playback
  uses: conductor-oss/conductor/.github/actions/check-playback@feature/llm_mock_impl
  with:
    server-url: http://localhost:8080/api
```

The action is a [composite action](https://docs.github.com/en/actions/reference/workflows-and-actions/metadata-syntax#runs-for-composite-actions) that invokes `check-playback.sh` with the recordings from the action's repository, regardless of the SDK checkout's working directory. Pin the action and server's recordings to the same Conductor revision.

Earlier CI steps must start a dedicated server with `conductor.ai.enable-llm-mocks=true`, set `conductor.ai.recordings-directory` to the shared `llm-recordings` directory, and run the examples with `mock/mockLLM`. Coverage starts empty when the server starts and is kept in memory. Run this check after all example calls finish, before stopping the server. A shared long-running server could retain coverage from an earlier run.

The action installs nothing. It requires a Linux runner with a POSIX shell, `curl` 7.76+, and standard utilities including `find`, `sha256sum`, and `mktemp`. It does not require Python, Node, `jq`, or any SDK package.

For an authenticated server, pass the complete header from a secret:

```yaml
  with:
    server-url: ${{ vars.CONDUCTOR_API_URL }}
    auth-header: ${{ secrets.CONDUCTOR_AUTH_HEADER }}
```

The shell script can also run directly:

```sh
sh .github/actions/check-playback/check-playback.sh http://localhost:8080/api
```

An optional second argument selects a recordings directory for local checks. The action itself always uses the bundled shared recordings.

## What is verified

The script posts a SHA-256 inventory to `POST /api/llm/playback/verify`. The endpoint compares it with the bytes loaded at server startup and the successful mock responses served since startup.

- HTTP 200: every expected recording was played back, with no unmatched requests.
- HTTP 409: a recording is missing, has different content, was never played, or a request had no recorded match. The response lists affected files.
- HTTP 400: the inventory is empty, malformed, or contains duplicate paths.
- Playback disabled: the endpoint is unavailable and the action fails.

Repeated calls do not cover a different unplayed recording. Identical duplicate recordings are aliases of the same request and share coverage. Verification is read-only and does not reset coverage.

This checks recorded model-response coverage. SDK steps remain responsible for workflow outcomes and tool behavior; the action does not register or run examples, inspect workflow statuses, or prevent unrelated live-provider calls. An intentional guardrail rejection can pass this check when all its model calls played back correctly.

Verified output after running the 19 shared examples:

```text
PASS: 93/93 recordings played back; 0 unmatched requests
```
