# Check LLM playback

Did every workflow on the playback server complete? PASS if yes, FAIL if not. Every SDK repository uses this same action after running its examples against a server that plays back the shared LLM recordings.

```yaml
- name: Check LLM playback
  uses: conductor-oss/conductor/.github/actions/check-playback@main
  with:
    server-url: http://localhost:8080/api
```

The CI job starts a fresh server with `conductor.ai.enable-llm-mocks=true` and `conductor.ai.recordings-directory` pointing at the shared `llm-recordings` directory, runs the SDK examples with the model `mock/mockLLM`, then runs this action. The server must be dedicated to the job, because every workflow on it is checked.

The script calls the standard `GET /api/workflow/search` endpoint for workflows whose status is anything other than `COMPLETED`. Zero hits is a pass. Otherwise it lists each workflow with its status and reason and exits 1. A missing recording shows up as a failed workflow whose reason reads "No recorded response matches the LLM request".

Requires `curl` and `jq`, both preinstalled on GitHub-hosted Linux runners. Run it locally with:

```sh
sh .github/actions/check-playback/check-playback.sh http://localhost:8080/api
```
