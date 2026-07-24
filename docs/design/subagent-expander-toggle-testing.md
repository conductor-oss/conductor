# Testing — Fix #1394

Companion to `subagent-expander-toggle-architecture.md`. Verifies invariants
INV-1…INV-4. Reuses the file paths and names defined there verbatim.

## 1. New test file

`ui-next/src/pages/agent/executions/ResultsTable.test.tsx` (Vitest + React
Testing Library), co-located with the component per repo convention.

## 2. Test setup

Render `ResultsTable` with a minimal `resultObj` containing at least one top-level
result row, controlled `hideSubWorkflows` state, and no-op handlers for the other
required `ResultsTableProps` (`setPage`, `setSort`, `refetchExecution`,
`handleReset`, `setHideSubWorkflows`). Because `expandableRowsComponent` lazily
calls `fetchSubAgents` (which hits `/api/workflow/:id`), stub `agentFetch` /
`global.fetch` so tests do not perform real network calls.

Minimal row shape:

```ts
const resultObj = {
  totalHits: 1,
  results: [
    { workflowId: "wf-1", workflowType: "top_level_agent", status: "COMPLETED" },
  ],
};
```

The expander control is located by accessible name:

```ts
screen.getAllByLabelText(/Expand Row/i)
```

## 3. Test cases

| ID | Name | Asserts |
|---|---|---|
| T-1 | expander present with toggle **off** | With `hideSubWorkflows={false}`, at least one `Expand Row` control exists (INV-1). |
| T-2 | expander present with toggle **on** | With `hideSubWorkflows={true}`, at least one `Expand Row` control exists — the regression guard for #1394 (INV-1). |
| T-3 | toggling does not remove expander | Re-render from `false` → `true` (and back); `Expand Row` control remains in all states (INV-2). |
| T-4 | expand triggers lazy load | Click the `Expand Row` control; `fetchSubAgents` is invoked once for that `workflowId` and "Loading sub-agents…" is shown before results resolve (INV-4). |

T-2 is the primary regression test: it must fail against the buggy
`expandableRows={!hideSubWorkflows}` and pass against the fixed `expandableRows`.

## 4. Manual verification

Per `CLAUDE.md`/`AGENTS.md`, UI behavior is verified against the running app when
possible. If a live server is unavailable, note it in the PR and mark any
unverifiable step with `<!-- TODO: verify against live server -->`.

Steps:

1. Open **Agents → Executions**.
2. Run/observe an agent that has sub-agents.
3. Toggle **Hide sub-agent executions** on.
4. Confirm the per-row dropdown/expander arrow is still visible and expands to
   show nested sub-agents.
5. Toggle off; confirm the arrow remains and behavior is unchanged.

## 5. Commands

```bash
# Component test (from ui-next/)
npm run test -- ResultsTable

# Lint/format for the touched files follows existing ui-next tooling
```

These are the verification steps for the implementation PR, not for writing the
design docs.
