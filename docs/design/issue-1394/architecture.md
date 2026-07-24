# Architecture — Fix #1394: Sub-agent expand arrow disappears when the "Hide sub-agent executions" toggle is on

> Single source of truth for this change. `fix-plan.md` and `testing.md` in this
> directory reuse the names, props, and the exact edit defined here, verbatim.

## 1. Overview

**Issue #1394** — On the **Agent Executions** results list, the per-row expand
arrow (the "dropdown" that reveals a parent agent's nested sub-agents) is present
when the toggle is **off** but **disappears when the toggle is on**.

The "toggle" named in the issue is the **`Hide sub-agent executions`** MUI `Switch`
rendered above the results grid. Its checked state is the boolean
`hideSubWorkflows`.

### 1.1 Root cause

In `ui-next/src/pages/agent/executions/ResultsTable.tsx`, the `DataTable`'s two
expand-related props are bound to the **negation of the toggle state**:

```tsx
// current — ResultsTable.tsx, lines 341-342
expandableRows={!hideSubWorkflows}
expandOnRowClicked={!hideSubWorkflows}
```

`expandableRows` controls whether the underlying `react-data-table-component`
renders the expander column (the arrow) at all. When `hideSubWorkflows === true`,
`expandableRows` evaluates to `false`, the expander column is removed, and the
arrow vanishes. This is precisely the reported symptom:

- toggle **off** → `expandableRows === true` → arrow shown.
- toggle **on**  → `expandableRows === false` → arrow gone.

### 1.2 Why the coupling is wrong

The two features are **orthogonal**:

- **`hideSubWorkflows`** decides whether sub-agent executions appear as their own
  **top-level rows** in the flat list. It is enforced server-side (the query sets
  `topLevelOnly` → `parentWorkflowId = ""`). In `ResultsTable.tsx`, `filteredResults`
  only adds the `_isSubAgent` visual marker when the toggle is off; it never gates
  expansion.
- **The expand arrow** lets a user drill into a *single* parent row and view that
  row's nested sub-agents inline, fetched on demand by `fetchSubAgents` and rendered
  by `expandableRowsComponent`.

Hiding sub-agents from the flat list should, if anything, make per-row expansion
**more** useful — it becomes the only inline way to see a parent's children — so it
must not remove the arrow.

### 1.3 The fix (minimal, focused)

Decouple the expander from the toggle: the results table is **always** expandable.
`expandableRowsComponent` already lazily fetches and renders each row's sub-agents
regardless of `hideSubWorkflows`, so no other change is required.

```tsx
// fixed
expandableRows
expandOnRowClicked
```

(Equivalent to `expandableRows={true}` / `expandOnRowClicked={true}`.)

No new components, props, types, state, DAOs, endpoints, styles, or dependencies
are introduced. This is a single-file, two-line behavioral correction in the UI.

## 2. Tech stack (touched surface only)

| Concern | Technology |
|---|---|
| UI framework | React + TypeScript (`ui-next`) |
| Component library | MUI (`@mui/material`: `Switch`, `FormControlLabel`, `Box`, `Table`) |
| Data grid | `react-data-table-component`, wrapped by the local `DataTable` component |
| Data fetching | `agentFetch` (thin `fetch` wrapper) against `/api/workflow/{id}` and `/api/agent/list` |
| Routing | `NavLink` + `AGENT_EXECUTIONS_URL` |

No backend, module, or Java/persistence changes.

## 3. Complete file layout

Exactly **one** production file changes. Every other entry is context the change
depends on and must stay consistent with — it is **not** modified.

| File | Responsibility | Change |
|---|---|---|
| `ui-next/src/pages/agent/executions/ResultsTable.tsx` | Renders the Agent Executions results grid, the `Hide sub-agent executions` toggle, and the expandable-row sub-agent viewer. | **EDIT** — set `expandableRows` and `expandOnRowClicked` to constant `true`, removing the `!hideSubWorkflows` binding (currently lines 341-342). |
| `ui-next/src/pages/agent/executions/BulkActionModule.tsx`, `executionsStyles.ts` | Peripheral pieces of the same page. | none |
| `ui-next/src/components/ui/DataTable/*` | Wrapper exposing `expandableRows`, `expandOnRowClicked`, `expandableRowsComponent`. | none — the fix reuses the existing prop surface unchanged. |

If the optional regression test is added (see `testing.md`):

| File | Responsibility |
|---|---|
| `ui-next/src/pages/agent/executions/__tests__/ResultsTable.test.tsx` | Assert the expander arrow renders in **both** toggle states. |

## 4. Shared contracts (reused verbatim by the other docs)

### 4.1 Component and props

`ResultsTable` is the default export of
`ui-next/src/pages/agent/executions/ResultsTable.tsx`. The props central to this
issue, from the existing `ResultsTableProps` interface:

```ts
export interface ResultsTableProps {
  // ...existing fields unchanged...
  /** Checked state of the "Hide sub-agent executions" Switch. */
  hideSubWorkflows: boolean;
  /** Setter for the toggle. */
  setHideSubWorkflows: (value: boolean) => void;
}
```

The toggle control (unchanged):

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

### 4.2 The exact edit

The invariant this fix establishes, stated once and referenced everywhere:

> **Invariant EXP-1:** The Agent Executions `DataTable` is always expandable. The
> expander arrow's presence is independent of `hideSubWorkflows`.

Before:

```tsx
<DataTable
  expandableRows={!hideSubWorkflows}
  expandOnRowClicked={!hideSubWorkflows}
  expandableRowsComponent={/* unchanged */}
  ...
```

After:

```tsx
<DataTable
  expandableRows
  expandOnRowClicked
  expandableRowsComponent={/* unchanged */}
  ...
```

### 4.3 Behaviors that MUST remain unchanged

- `hideSubWorkflows` still controls whether sub-agent executions appear as
  top-level rows (via `filteredResults` and the server-side `topLevelOnly` query).
- `_isSubAgent` row marking, `conditionalRowStyles`, and the `↳ … sub-agent`
  renderer on the `workflowType` column stay exactly as-is.
- `expandableRowsComponent` continues to lazily call `fetchSubAgents(wfId)` and
  render the nested sub-agent `Table` (or the `Loading sub-agents...` / `No
  sub-agents` states). It already works for both toggle states.

## 5. Non-goals

- No redesign of the sub-agent viewer, the inline nested `Table`, or the
  `AgentExecution` debugger (`SubAgentTree`, `AgentRunView`, etc.).
- No server/API changes.
- No change to the meaning or label of the toggle.
