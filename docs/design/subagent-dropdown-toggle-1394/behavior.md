# Behavior specification — #1394

Reuses names, props, and file paths from [`architecture.md`](./architecture.md).

Scope: `ui-next/src/pages/agent/executions/ResultsTable.tsx`.

## Behavior matrix

`hideSubWorkflows` is the "Hide sub-agent executions" toggle (default `true`).
Two independent concerns are governed:

| Concern | Toggle OFF (`hideSubWorkflows === false`) | Toggle ON (`hideSubWorkflows === true`, default) |
|---|---|---|
| Flat rows | All executions shown; sub-agent executions appear as their own rows, marked `_isSubAgent` (styled via `conditionalRowStyles`, `↳ … sub-agent` in the `workflowType` renderer). | Only top-level agent executions shown (server-side `topLevelOnly`); sub-agent rows excluded from the flat list. |
| Per-row drill-down arrow (`expandableRows`) | **Present** (unchanged) | **Present** — this is the fix. Was previously **absent**. |
| Expand-on-row-click (`expandOnRowClicked`) | Enabled (unchanged) | Enabled — previously **disabled**. |
| Expanded content (`expandableRowsComponent`) | Lazy `fetchSubAgents` → "Loading sub-agents..." → "No sub-agents" or inline nested sub-agent `Table`. Unchanged. | Identical. Unchanged. |

The only cells that change from current behavior are the arrow / expand rows in
the **Toggle ON** column: they move from absent/disabled to present/enabled.
Because the toggle defaults to on, this is also what the user sees on first load.

## Before vs. after (the exact diff)

Before (buggy):

```tsx
<DataTable
  expandableRows={!hideSubWorkflows}
  expandOnRowClicked={!hideSubWorkflows}
  ...
/>
```

After (fixed):

```tsx
<DataTable
  expandableRows
  expandOnRowClicked
  ...
/>
```

## Rationale

- The toggle's purpose is to declutter the flat list by removing sub-agent rows.
- When those rows are hidden, the inline drill-down is the *only* remaining path
  to a row's nested sub-agents, so the arrow must stay.
- Expansion and flat-list filtering are orthogonal; the original code conflated
  them by deriving `expandableRows` from `!hideSubWorkflows`.
- The fix is additive: toggle-off behavior is unchanged; toggle-on regains the
  arrow.

## Invariants preserved

1. The toggle still drives `filteredResults`, `_isSubAgent` marking, and
   `conditionalRowStyles`.
2. `expandableRowsComponent`, `fetchSubAgents`, and `subAgentCache` are untouched;
   lazy loading and the "Loading sub-agents..." / "No sub-agents" states behave
   as before.
3. Row selection (`selectableRows`), pagination, sorting, and the
   `BulkActionModule` context menu are unaffected.
4. The parent search pages' `topLevelOnly` request and the toggle default
   (`true`) are unchanged.

## User-visible outcome

- Toggle OFF: unchanged from today.
- Toggle ON (default): the flat list shows only top-level agents **and** each row
  keeps its expand arrow, so nested sub-agents remain reachable inline —
  resolving #1394.
