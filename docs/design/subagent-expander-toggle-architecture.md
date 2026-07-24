# Architecture — Fix #1394: Sub-agent expander arrow disappears when the "Hide sub-agent executions" toggle is on

Source of truth for this change. The supporting docs
(`subagent-expander-toggle-behavior.md`, `subagent-expander-toggle-testing.md`)
reuse the names, props, and file paths defined here verbatim.

## 1. Overview

The Agent Executions results table lets an operator:

1. **Hide sub-agent executions** from the top-level list via a `Switch` toggle,
   so only registered (top-level) agents appear as their own rows.
2. **Expand a row** via a per-row caret/arrow (the `react-data-table-component`
   "expander" column) to see that agent's nested sub-agents inline.

These are two independent capabilities. Today they are incorrectly coupled: the
expander arrow is rendered only when the toggle is **off**. When an operator turns
the toggle **on**, the arrow vanishes, so nested sub-agents can no longer be viewed
inline (Issue #1394).

The fix decouples the expander from the toggle. The expander arrow must always be
available regardless of the toggle state. The toggle governs only whether sub-agents
appear as their own top-level rows; it must not govern whether a row can be expanded.

This is a minimal, single-file UI fix. No API, data-model, or SDK changes are
required.

## 2. Tech stack

| Concern | Technology |
|---|---|
| UI framework | React 18 + TypeScript (`ui-next`) |
| Component library | MUI (`@mui/material`) |
| Data table | `react-data-table-component` (wrapped by `components/ui/DataTable`) |
| Icons | `@phosphor-icons/react` |
| Test runner | Vitest + React Testing Library (`*.test.tsx` co-located) |

## 3. Affected module / file layout

Only one production file changes. One test file is added.

| File | Status | Responsibility |
|---|---|---|
| `ui-next/src/pages/agent/executions/ResultsTable.tsx` | **edit** | Renders the Agent Executions `DataTable`, the "Hide sub-agent executions" `Switch`, and the expandable-row component. Holds the coupling bug to fix. |
| `ui-next/src/pages/agent/executions/ResultsTable.test.tsx` | **add** | Verifies the expander arrow is present in both toggle states and that toggling does not remove it. |

### Reference (unchanged) files

Read for context only; do not modify.

| File | Role |
|---|---|
| `ui-next/src/components/ui/DataTable/DataTable.tsx` | Wraps `react-data-table-component`; `DataTableProps extends TableProps<any>`, so `expandableRows`, `expandOnRowClicked`, and `expandableRowsComponent` pass straight through. |
| `ui-next/src/components/ui/DataTable/types.ts` | `LegacyColumn`, `ColumnCustomType` used by `executionFields`. |

## 4. Root cause (exact)

In `ResultsTable.tsx`, inside the returned `<DataTable ...>`:

```tsx
<DataTable
  expandableRows={!hideSubWorkflows}
  expandOnRowClicked={!hideSubWorkflows}
  expandableRowsComponent={/* renders nested sub-agents for a row */}
  ...
/>
```

`react-data-table-component` renders the per-row expander arrow **only when
`expandableRows` is truthy**. Binding it to `!hideSubWorkflows` means:

- toggle **off** → `hideSubWorkflows === false` → `expandableRows === true` → arrow shown.
- toggle **on**  → `hideSubWorkflows === true`  → `expandableRows === false` → arrow gone.

The `hideSubWorkflows` state itself is correctly plumbed (it drives server-side
`topLevelOnly` filtering and the `_isSubAgent` visual marking in `filteredResults`);
it simply must not also gate the expander.

## 5. The fix (shared contract)

Decouple the expander from the toggle. The expander is always enabled.

```tsx
<DataTable
  expandableRows
  expandOnRowClicked
  expandableRowsComponent={/* unchanged */}
  ...
/>
```

Equivalently, `expandableRows={true}` and `expandOnRowClicked={true}`. The value
`true` is chosen (not `hideSubWorkflows` or any derived expression) because
expandability is unconditional for this table.

### Invariants every component/test must uphold

- **INV-1** — The expander arrow (`react-data-table-component`'s expander column,
  `button[aria-label="Expand Row"]` / `"Collapse Row"`) is present for data rows
  regardless of `hideSubWorkflows`.
- **INV-2** — Toggling `hideSubWorkflows` on then off (or vice versa) never removes
  the expander column.
- **INV-3** — `hideSubWorkflows` continues to control only: (a) the server-side
  `topLevelOnly` request behavior owned by the parent, and (b) the `_isSubAgent`
  marking in `filteredResults`. The fix touches neither.
- **INV-4** — `expandableRowsComponent` behavior is unchanged: on first expand it
  calls `fetchSubAgents(row.workflowId)` and shows "Loading sub-agents…", then
  renders the nested sub-agent rows (or "No sub-agents").

## 6. Naming conventions (reuse verbatim)

| Symbol | Meaning |
|---|---|
| `hideSubWorkflows: boolean` | Prop on `ResultsTableProps`; drives the toggle's `checked`. |
| `setHideSubWorkflows(value: boolean)` | Prop setter on `ResultsTableProps`. |
| `filteredResults` | `useMemo` list passed to `DataTable data`; marks `_isSubAgent`. |
| `fetchSubAgents(workflowId: string)` | Lazy loader for a row's nested sub-agents. |
| `subAgentCache: Record<string, any[]>` | Cache keyed by `workflowId`. |
| "Hide sub-agent executions" | Exact `FormControlLabel` label text of the toggle. |

## 7. Out of scope

- Renaming, restyling, or relocating the toggle.
- Changing server-side `topLevelOnly` filtering.
- Any change to `SubAgentTree.tsx`, `AgentRunView.tsx`, or the timeline/diagram
  view toggle — those are unrelated to this issue despite also using the word
  "toggle".
