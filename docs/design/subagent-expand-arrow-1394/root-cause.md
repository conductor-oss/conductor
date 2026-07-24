# Root-cause analysis — #1394

> Reuses names, props, and file paths from `architecture.md` verbatim.

## 1. Symptom

On the Agent Executions results table:

- Toggle **OFF** ("Hide sub-agent executions" unchecked): each top-level row shows an expand
  **dropdown arrow**.
- Toggle **ON**: the arrow is **gone**, so there is no way to view an execution's nested
  sub-agents — which is precisely the state in which the user needs it, because sub-agent rows are
  no longer shown inline.

## 2. Location

`ui-next/src/pages/agent/executions/ResultsTable.tsx`, in the `DataTable` element.

## 3. The defect: inverted boolean binding

The expand props are bound to the **negation** of the toggle state:

```tsx
// BEFORE (buggy)
<DataTable
  expandableRows={!hideSubWorkflows}
  expandOnRowClicked={!hideSubWorkflows}
  expandableRowsComponent={/* nested sub-agent list — correct */}
  ...
/>
```

Evaluating against the behavior matrix in `architecture.md` §3.3:

| `hideSubWorkflows` | `!hideSubWorkflows` (current) | Arrow rendered | Sub-agents visible any other way? |
|---|---|---|---|
| `true` (ON) | `false` | **no** | no — they are filtered out ⇒ **broken** |
| `false` (OFF) | `true` | yes | yes — already shown as inline rows ⇒ redundant |

So the negation makes the arrow appear in the exact state where it is redundant (toggle OFF,
sub-agents already inline) and vanish in the exact state where it is needed (toggle ON, sub-agents
hidden). This is the inversion reported in the issue.

## 4. Why binding to `hideSubWorkflows` (not its negation) is correct

The purpose of the expand arrow is to reveal sub-agents that are **not otherwise on screen**. That
condition holds only when the toggle is ON. Binding both expand props directly to
`hideSubWorkflows` aligns the control with the invariant stated in `architecture.md` §3.2 and with
the authoritative behavior matrix §3.3.

## 5. The fix

Remove the negation from both expand props:

```tsx
// AFTER (fixed)
<DataTable
  expandableRows={hideSubWorkflows}
  expandOnRowClicked={hideSubWorkflows}
  expandableRowsComponent={/* unchanged */}
  ...
/>
```

That is the complete change: two `!` characters removed. No other lines change.

## 6. Blast radius

- `expandableRowsComponent`, `fetchSubAgents`, and `subAgentCache` are untouched and already work
  for top-level `workflowId` rows — the state in which the arrow now appears.
- The `_isSubAgent` marker and `conditionalRowStyles` only apply when the toggle is OFF; with the
  arrow now hidden in that state, no expansion/inline duplication can occur.
- No prop signature changes; the parent page and `ResultsTableProps` are unaffected.
