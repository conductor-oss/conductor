# Testing — #1394

> Reuses names, props, and file paths from `architecture.md` verbatim.

## 1. Manual verification

Preconditions: run at least one agent that spawns sub-agents so the executions table has both
top-level and sub-agent rows.

Navigate to the Agent Executions page and open the results table
(`ui-next/src/pages/agent/executions/ResultsTable.tsx`).

| Step | Action | Expected result |
|---|---|---|
| 1 | Toggle **ON** ("Hide sub-agent executions" checked, `hideSubWorkflows = true`) | Flat list shows top-level agents only; **every top-level row shows the expand dropdown arrow**. |
| 2 | Click a row's arrow | Row expands; `GET /api/workflow/{workflowId}` runs once; nested sub-agents render (or "No sub-agents"). |
| 3 | Collapse, expand again | No re-fetch (served from `subAgentCache`). |
| 4 | Toggle **OFF** (`hideSubWorkflows = false`) | Sub-agent executions appear as inline rows marked `↳ … sub-agent`; **no expand arrow** (sub-agents already visible), matching `architecture.md` §3.3. |

Regression check: confirm search, sort, pagination, row selection, and `BulkActionModule` behave
identically in both toggle states.

## 2. Automated test guidance

If a component test is added, colocate it with the component
(`ui-next/src/pages/agent/executions/`) following existing `ui-next` test conventions (Vitest +
Testing Library). Cover the authoritative behavior matrix from `architecture.md` §3.3:

- **Arrow present when toggle ON**: render `ResultsTable` with `hideSubWorkflows={true}` and a
  result set of top-level rows; assert an expander control exists for a row.
- **Arrow absent when toggle OFF**: render with `hideSubWorkflows={false}`; assert no expander
  control is rendered.
- **Expand fetches once**: with `hideSubWorkflows={true}`, expanding a row calls the sub-agent
  fetch a single time; a second expand of the same row does not re-fetch (asserts `subAgentCache`).

Both assertions key off the same prop binding under test:
`expandableRows={hideSubWorkflows}` / `expandOnRowClicked={hideSubWorkflows}`.

## 3. Pre-submit checklist

Per `AGENTS.md`:

- `./gradlew spotlessApply` does not apply to `ui-next`; run the formatter/linter used by the
  `ui-next` package instead.
- Build and run the `ui-next` unit tests for the changed component.
- Confirm no TypeScript errors are introduced (the change removes two `!` operators; types are
  unchanged).
