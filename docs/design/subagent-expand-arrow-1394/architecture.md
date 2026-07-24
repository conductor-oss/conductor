# Architecture — Fix #1394: Sub-agent expand dropdown disappears when the toggle is on

## 1. Overview

Issue [#1394](https://github.com/conductor-oss/conductor/issues/1394) reports that on the
**Agent Executions** results table, the row-expansion **dropdown arrow** — the control used to
view an execution's nested sub-agents — **disappears when the "Hide sub-agent executions" toggle
is turned on**.

This document is the single source of truth for the fix. It describes the affected component, the
exact contract of the toggle and the expand control, the root cause (an inverted boolean binding),
and the minimal change required. The supporting documents in this directory
(`root-cause.md`, `testing.md`) reuse the names, props, and file paths defined here verbatim.

### Scope

This is a **one-line UI logic fix** in a single React component. No API, data-model, SDK, or
server change is required. No new files are created in the application. The only files created by
this task are the design documents under `docs/design/subagent-expand-arrow-1394/`.

### Tech stack (affected surface only)

| Concern | Technology |
|---|---|
| UI framework | React 18 + TypeScript (`ui-next`) |
| Component library | MUI (`@mui/material`) — `Switch`, `FormControlLabel` |
| Table | `DataTable` wrapper over `react-data-table-component` |
| Data fetching | `agentFetch` helper against the Conductor REST API |

## 2. Module / file layout

The change is confined to one existing file. No files are added or removed in the application.

| File | Role | Change |
|---|---|---|
| `ui-next/src/pages/agent/executions/ResultsTable.tsx` | Renders the Agent Executions table, the "Hide sub-agent executions" toggle, and the expandable-row sub-agent viewer. | **EDIT** — correct the boolean bound to the `DataTable` expand props. |
| `ui-next/src/pages/agent/executions/*` (siblings: `BulkActionModule`, `executionsStyles`, page container) | Unchanged callers/helpers. | none |

### Design docs produced by this task

| File | Role |
|---|---|
| `docs/design/subagent-expand-arrow-1394/architecture.md` | This file — source of truth. |
| `docs/design/subagent-expand-arrow-1394/root-cause.md` | Detailed root-cause analysis and the exact before/after diff. |
| `docs/design/subagent-expand-arrow-1394/testing.md` | Manual verification steps and automated test guidance. |

## 3. Shared contracts

Every supporting doc references the identifiers below exactly as written.

### 3.1 The toggle state

Defined in the parent page and threaded into `ResultsTable` via props. The prop names are fixed:

```ts
// ResultsTableProps (ui-next/src/pages/agent/executions/ResultsTable.tsx)
hideSubWorkflows: boolean;                 // true  => "Hide sub-agent executions" toggle is ON
setHideSubWorkflows: (value: boolean) => void;
```

Semantics of `hideSubWorkflows`:

- `true`  (toggle **ON**): the flat table lists **top-level agent executions only**. Sub-agent
  executions are filtered out server-side (`topLevelOnly` → `parentWorkflowId = ""`). The **only**
  way to see an execution's sub-agents is to expand its row.
- `false` (toggle **OFF**): sub-agent executions appear as their own rows in the flat list, marked
  with `_isSubAgent: true` for visual distinction. Row expansion would therefore be redundant with
  what is already shown inline.

The toggle control itself is unchanged:

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

### 3.2 The expand control contract (`DataTable`)

The dropdown arrow is provided by `react-data-table-component` (surfaced through the shared
`DataTable`). Three props govern it, and all three must be **consistent with each other**:

| Prop | Type | Meaning |
|---|---|---|
| `expandableRows` | `boolean` | Renders the per-row expand **arrow**. When `false`, no arrow is shown. |
| `expandOnRowClicked` | `boolean` | Allows expanding a row by clicking anywhere on it. |
| `expandableRowsComponent` | `React.FC<{ data: row }>` | Renders the expanded content — the nested sub-agent list. Unchanged. |

**Contract (the invariant this fix restores):** the arrow (`expandableRows`) must be present in the
state where sub-agents are *not* already visible as flat rows — i.e. when the toggle is **ON**.
Therefore both boolean expand props bind to `hideSubWorkflows` (not its negation):

```tsx
expandableRows={hideSubWorkflows}
expandOnRowClicked={hideSubWorkflows}
```

### 3.3 Behavior matrix (authoritative)

| `hideSubWorkflows` | Toggle | Flat rows shown | Expand arrow (`expandableRows`) | How you see sub-agents |
|---|---|---|---|---|
| `true` | ON | Top-level agents only | **Visible** | Expand a row |
| `false` | OFF | Top-level + sub-agents (inline) | Hidden | Already inline as rows |

### 3.4 Expanded-row content (unchanged)

`expandableRowsComponent` fetches sub-agents on demand and caches them:

```ts
// key: workflowId ; value: array of { referenceTaskName, subWorkflowId, status }
const [subAgentCache, setSubAgentCache] = useState<Record<string, any[]>>({});
fetchSubAgents(workflowId) // GET /api/workflow/{workflowId}, keep tasks with subWorkflowId
```

This logic is correct and is **not** modified by the fix.

## 4. Naming conventions

- Boolean props that gate the toggle are named `hideSubWorkflows` / `setHideSubWorkflows`
  (existing names — do not rename).
- The internal marker for a sub-agent row is `_isSubAgent` (existing — do not rename).
- Do not introduce new state; the fix reuses the existing `hideSubWorkflows` prop only.

## 5. Non-goals

- No change to server-side `topLevelOnly` filtering.
- No change to `fetchSubAgents`, `subAgentCache`, or `expandableRowsComponent`.
- No change to the `AgentExecution/` diagram/timeline views (`SubAgentTree`, `TurnBar`, etc.);
  those render a different sub-agent surface and are unaffected by this issue.
- No visual restyle of the toggle or arrow.
