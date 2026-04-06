# CLAUDE.md — conductor (server repo)

Instructions for Claude Code working in this repository.

## Documentation Changes: Verify, Don't Reason

**Before writing or editing any code example, command, or API reference in docs, verify it against the source.**

This rule exists because a hallucinated endpoint (`POST /api/workflow/{name}/run`) was committed to the quickstart and caught only in PR review. The correct endpoint (`POST /api/workflow/execute/{name}/{version}`) is in `rest/src/main/java/com/netflix/conductor/rest/controllers/WorkflowResource.java`. The pattern repeats: plausible-sounding docs can be silently wrong.

### Verification checklist for any doc edit

- **REST endpoint** → grep `@PostMapping`, `@GetMapping`, `@PutMapping`, `@DeleteMapping` in `rest/src/main/java/` to confirm the path exists.
- **Query params / request body** → read the method signature in the matching controller.
- **CLI command** → check `conductor-cli/cmd/*.go` (separate repo under this workspace).
- **SDK example** → trace against the SDK source, not the doc you're editing.
- **Expected output** → match real log output from tests or CI; do not invent.

If you cannot verify locally (no running server, no CLI binary), **say so in the PR description** and mark the block with a `<!-- TODO: verify against live server -->` comment rather than committing an unverified example.

### Key source locations

| Topic | Where to look |
|---|---|
| REST API routes | `rest/src/main/java/com/netflix/conductor/rest/controllers/` |
| Workflow execution (sync) | `WorkflowResource.java` → `executeWorkflow()` at `/workflow/execute/{name}/{version}` |
| Task routes | `TaskResource.java` |
| CLI `workflow start --sync` | `conductor-cli/cmd/workflow.go` → `ExecuteWorkflow()` call |

## Other Guidelines

See [AGENTS.md](AGENTS.md) for full project conventions (code style, testing, dependency pinning, PR guidelines).

## GitHub Account

Always use `nthmost-orkes` for all `gh` commands in this repo. Verify with `gh auth status` before filing issues or creating PRs.
