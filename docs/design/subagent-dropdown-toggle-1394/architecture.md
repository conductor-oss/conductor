# Architecture — Fix: sub-agent drill-down arrow disappears when "Hide sub-agent executions" toggle is on (#1394)

> Single source of truth for this change. The supporting docs
> ([`behavior.md`](./behavior.md), [`testing.md`](./testing.md)) reuse the names,
> props, and file paths defined here verbatim.

## 1. Overview

### Problem

On the **Agents → Executions** results table, each execution row has an
expandable drill-down — the caret/arrow that opens an inline sub-table listing
that agent's nested sub-agents. A `Switch` labelled **"Hide sub-agent
executions"** controls whether sub-agent executions also appear as their own
top-level rows in the flat list.

The bug (issue #1394): when the toggle is **on**, the per-row drill-down arrow
disappears, so a user can no longer expand a row to view its nested sub-agents.
When the toggle is **off**, the arrow is present.

This is especially visible because the toggle **defaults to on**
(`useState(true)` in the parent search pages), so on first load the arrow is
missing entirely — matching the second ("toggle on") screenshot in the issue.

### Root cause

In `ui-next/src/pages/agent/executions/ResultsTable.tsx`, the `DataTable`'s
row-expansion capability is bound to the *negation* of the toggle state:

```tsx
<DataTable
  expandableRows={!hideSubWorkflows}
  expandOnRowClicked={!hideSubWorkflows}
  ...
/>
```

`hideSubWorkflows` is the toggle's boolean. When the toggle is on
(`hideSubWorkflows === true`), both props evaluate to `false`, which removes the
expander column (the arrow) entirely.

The two features are conceptually independent and were incorrectly coupled:

| Feature | Purpose |
|---|---|
| **Toggle** (`hideSubWorkflows`) | Whether sub-agent executions appear as their **own rows** in the flat list. Drives a server-side filter (`topLevelOnly`) owned by the parent search page. |
| **Row expansion** (`expandableRows`) | Whether a row can be expanded inline to reveal its nested sub-agents, fetched lazily via `fetchSubAgents`. |

Hiding sub-agent rows from the flat list is precisely the case where per-row
drill-down is *most* useful — it is the only remaining way to reach the nested
sub-agents. Coupling the two defeats the toggle's purpose.

### Fix (minimal, focused)

Decouple row expansion from the toggle: row expansion is **always available**.
The toggle continues to govern only the flat-list filtering (`topLevelOnly`,
server-side) and the `_isSubAgent` visual marking that is already handled
elsewhere in the component.

```tsx
<DataTable
  expandableRows
  expandOnRowClicked
  ...
/>
```

This is strictly additive relative to today: the toggle-**off** case (arrow
already present) is unchanged, and the toggle-**on** case gains back the arrow.
Nothing beyond restoring the arrow changes — exactly what the issue asks.

### Tech stack

- **UI**: React + TypeScript (`ui-next/`), Material UI (`@mui/material`) —
  `Switch`, `FormControlLabel`, `Box`, `Table`.
- **Table**: the internal `DataTable`
  (`ui-next/src/components/ui/DataTable`), a wrapper whose `DataTableProps
  extends TableProps<any>` and spreads `...rest` onto `react-data-table-component`,
  so `expandableRows`, `expandOnRowClicked`, and `expandableRowsComponent` pass
  straight through. This is why the fix needs no component-level plumbing.
- No server, SDK, or REST API changes. Client-only rendering fix.

## 2. Module / file layout

Exactly one source file changes. No new source files are created.

| File | Responsibility | Change |
|---|---|---|
| `ui-next/src/pages/agent/executions/ResultsTable.tsx` | Renders the agent-executions results table, the "Hide sub-agent executions" toggle, and the lazy expandable sub-agent sub-table. | `expandableRows={!hideSubWorkflows}` → `expandableRows`; `expandOnRowClicked={!hideSubWorkflows}` → `expandOnRowClicked`. Nothing else. |

Recommended test file (see [`testing.md`](./testing.md)):

| File | Responsibility |
|---|---|
| `ui-next/src/pages/agent/executions/__tests__/ResultsTable.test.tsx` | New test asserting the expander arrow renders regardless of `hideSubWorkflows`, and that toggling never removes it. |

### Related files (context only — NOT modified)

| File | Why it matters |
|---|---|
| `ui-next/src/components/ui/DataTable/DataTable.tsx` | Forwards `expandableRows` / `expandOnRowClicked` / `expandableRowsComponent` to `react-data-table-component` via `...rest`. Unaffected. |
| `ui-next/src/pages/agent/executions/workflowSearchComponents/BasicSearch.tsx` | Owns `const [hideSubWorkflows, setHideSubWorkflows] = useState(true)` and sets `topLevelOnly: hideSubWorkflows` on the search request; passes both to `ResultsTable`. Unaffected. |
| `ui-next/src/pages/agent/executions/workflowSearchComponents/AdvancedSearch.tsx` | Same ownership pattern as `BasicSearch.tsx`. Unaffected. |
| `ui-next/src/pages/agent/executions/BulkActionModule.tsx` | Passed as `contextComponent` for bulk actions. Unaffected. |

## 3. Shared contracts

These names/types are used verbatim by `behavior.md` and `testing.md`.

### 3.1 Component props (`ResultsTableProps`) — existing, unchanged

Defined in `ResultsTable.tsx`. Only the two fields relevant to this fix are
shown; all other props are unchanged.

```ts
export interface ResultsTableProps {
  // ...existing fields unchanged...
  /** Toggle state: when true, sub-agent executions are excluded from the flat
   *  list (server-side topLevelOnly). Does NOT affect whether a row can be
   *  expanded to reveal nested sub-agents. */
  hideSubWorkflows: boolean;
  /** Setter wired to the "Hide sub-agent executions" Switch. Lifted to the
   *  parent search page (default value there is `true`). */
  setHideSubWorkflows: (value: boolean) => void;
}
```

### 3.2 Toggle control — existing markup, unchanged

The user-visible toggle. Its label and binding are the contract referenced by
`behavior.md` and `testing.md`:

```tsx
<FormControlLabel
  control={
    <Switch
      checked={hideSubWorkflows}
      onChange={(e) => setHideSubWorkflows(e.target.checked)}
      size="small"
    />
  }
  label="Hide sub-agent executions"
  sx={{ ml: 1, mb: 1, mt: 3 }}
/>
```

### 3.3 Expansion contract — the fix

The `DataTable` expansion props. Post-fix invariant:

```tsx
// INVARIANT: row expansion is independent of the toggle.
expandableRows          // always enabled -> expander arrow column always rendered
expandOnRowClicked      // always enabled -> clicking a row also expands it
```

`expandableRowsComponent` is unchanged: it lazily calls `fetchSubAgents(wfId)`,
renders **"Loading sub-agents..."**, then either **"No sub-agents"** or the
inline nested-sub-agent `Table` keyed by `sub.subWorkflowId`.

### 3.4 Sub-agent lazy-fetch shape — unchanged

`fetchSubAgents(workflowId)` calls `agentFetch("/api/workflow/{workflowId}")`,
keeps tasks with a `subWorkflowId`, and stores the result in
`subAgentCache: Record<string, any[]>`, where each element is:

```ts
{
  referenceTaskName: string;
  subWorkflowId: string;
  status: string;
}
```

### 3.5 Naming conventions

- `hideSubWorkflows` — the toggle boolean (kept as-is; existing public prop name,
  even though the label reads "sub-agent").
- `_isSubAgent` — row-level marker added by the `filteredResults` memo when the
  toggle is off; used only for visual distinction (`conditionalRowStyles` and the
  `workflowType` renderer's `↳ … sub-agent` treatment). Independent of expansion.
- `subAgentCache` — memoized per-`workflowId` cache of nested sub-agents.

## 4. Non-goals

- No change to server-side `topLevelOnly` filtering or the parent search pages.
- No renaming of `hideSubWorkflows` (would ripple to the parent).
- No change to the toggle's default value (`true`) or its label.
- No change to `_isSubAgent` marking, `conditionalRowStyles`, or row styling.
- No change to `expandableRowsComponent`, `fetchSubAgents`, or `subAgentCache`.
- No change to `DataTable` or `react-data-table-component`.
