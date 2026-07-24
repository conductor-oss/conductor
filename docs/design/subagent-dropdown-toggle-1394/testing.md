# Testing plan — #1394

Reuses names and file paths from [`architecture.md`](./architecture.md) and the
behavior matrix in [`behavior.md`](./behavior.md).

## Goal

Prove that the per-row drill-down arrow (row expander) is rendered **regardless**
of the "Hide sub-agent executions" toggle state, and that toggling the switch
never removes it.

## Location

`ui-next/src/pages/agent/executions/ResultsTable.test.tsx` (new file, or extend
if one exists). Uses the project's existing React Testing Library +
Vitest/Jest setup already used across `ui-next/`.

## Fixtures

Render `ResultsTable` with a minimal `resultObj` containing at least one
top-level agent row so the expander column has data to attach to:

```ts
const resultObj = {
  totalHits: 1,
  results: [
    {
      workflowId: "wf-1",
      workflowType: "top_level_agent",
      status: "COMPLETED",
      startTime: 0,
      endTime: 1000,
    },
  ],
};
```

Provide required props (`page`, `rowsPerPage`, `setPage`, `setSort`,
`refetchExecution`, `filterOn`, `handleReset`) as no-op stubs, and the two props
central to this fix: `hideSubWorkflows` and `setHideSubWorkflows`.

## Test cases

### 1. Arrow present when toggle OFF (regression guard)

- Render with `hideSubWorkflows={false}`.
- Assert an expander control exists for the data row (query the
  `DataTable` expander button / cell rendered by `react-data-table`).

### 2. Arrow present when toggle ON (the fix)

- Render with `hideSubWorkflows={true}`.
- Assert the expander control is **still** present for the data row.
- This case fails against the pre-fix code (where
  `expandableRows={!hideSubWorkflows}` removed the expander).

### 3. Toggling does not remove the arrow

- Render with `hideSubWorkflows={false}` and a controlled wrapper that flips the
  prop when the `Switch` (label "Hide sub-agent executions") is clicked.
- Click the toggle to turn it ON.
- Assert the expander control remains in the DOM after the toggle change.

### 4. Expansion still loads nested sub-agents (unchanged behavior)

- With any toggle state, click the expander for `wf-1`.
- Mock `agentFetch("/api/workflow/wf-1")` to return one task with a
  `subWorkflowId`.
- Assert "Loading sub-agents…" appears, then the nested sub-agent `NavLink`
  (keyed by `subWorkflowId`) renders. Confirms `fetchSubAgents` / `subAgentCache`
  are untouched.

## Manual verification

Per `CLAUDE.md`/`AGENTS.md`, if a running UI is unavailable, mark manual steps
explicitly in the PR. Manual steps:

1. Run an agent with sub-agents; open **Agents → Executions**.
2. With the toggle **off**, confirm the drill-down arrow is present (matches the
   first screenshot in the issue).
3. Turn the toggle **on**; confirm the arrow is **still present** (the second
   screenshot in the issue previously showed it missing).
4. Expand a top-level agent row and confirm nested sub-agents load inline.

## Commands

```bash
# From ui-next/
npm test -- ResultsTable
```

No backend, Gradle, or SDK tests are required — this is a client-only change.
