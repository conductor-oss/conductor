# CLAUDE.md — conductor (server repo)

Instructions for Claude Code working in this repository.

## Writing Documentation

Documentation in this project is **derived from source**, not composed from memory or intuition. The workflow is: open the source → read what's there → write the doc from what you find. The source is the spec; the doc is a rendering of it.

Concrete reason this matters: a curl equivalent for `conductor workflow start --sync` was once written as `POST /api/workflow/{name}/run` — an endpoint that does not exist. Opening `WorkflowResource.java` first would have given the correct path (`POST /api/workflow/execute/{name}/{version}`) immediately.

### For each content type, start here

**REST endpoint or curl example**
1. Open the controller: `rest/src/main/java/com/netflix/conductor/rest/controllers/`
2. Find the method by its `@PostMapping`/`@GetMapping` annotation — copy the path literally.
3. Read the method signature for query params, path variables, and request body.
4. Write the curl from what you just read.

**CLI command or flag**
1. Open `conductor-cli/cmd/*.go` (separate repo under this workspace).
2. Find the `cobra.Command` for the subcommand and read its `Flags()` declarations.
3. Write the example from what you just read — flag names, types, and defaults.

**SDK code example (Python, JS, Java, Go)**
1. Open the SDK source file for the method you're documenting.
2. Read the method signature and required parameters.
3. If a working test exists for that method, use it as the starting point.
4. Write from the signature — do not infer from the method name alone.

**Expected output block**
1. Get real output: run the command, or find it in test fixtures or CI logs.
2. Paste verbatim. Do not construct output that "looks right."
3. For variable fields (IDs, timestamps), use annotated placeholders like `<workflow-id>`.

**Editing an existing section**
- Before changing anything, read every code block and command in the section.
- Verify each one using the steps above, not just the block you plan to change.
- Fix anything you find while you're there.

### When you can't verify

If a running server or CLI is unavailable:
- Add `<!-- TODO: verify against live server -->` in the file.
- Note it explicitly in the PR description.
- Do not write an unverified example and leave it unmarked.

### Key source locations

| Content | Where to look |
|---|---|
| REST API routes | `rest/src/main/java/com/netflix/conductor/rest/controllers/` |
| Workflow sync execution | `WorkflowResource.java` → `executeWorkflow()` at `@PostMapping("execute/{name}/{version}")` |
| Task routes | `TaskResource.java` |
| CLI subcommands and flags | `conductor-cli/cmd/workflow.go`, `cmd/task.go`, etc. |

## Other Guidelines

See [AGENTS.md](AGENTS.md) for full project conventions: code style, testing, dependency pinning, PR guidelines.
