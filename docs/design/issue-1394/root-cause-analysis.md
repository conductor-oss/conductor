# Root Cause Analysis — Fix #1394

> Reuses the names, files, and contract in `architecture.md` verbatim.

## 1. Symptom

- **Reported:** "Subagent DropDown disappears when toggle is on."
- **Observed:** On the Agent Executions results table, the per-row expand arrow
  (caret / dropdown used to reveal a parent execution's nested sub-agents) is
  visible when the **"Hide sub-agent executions"** switch is **off** and gone
  when it is **on**.

## 2. Trace: from toggle to missing arrow

All references are in `ui-next/src/pages/agent/executions/`.

### 2.1 State origin

`workflowSearchComponents/BasicSearch.tsx` and
`workflowSearchComponents/AdvancedSearch.tsx` both declare:

```tsx
const [hideSubWorkflows, setHideSubWorkflows] = useState(true); // default: ON
```

So on first render the toggle is **on** — which is precisely the broken state.

### 2.2 Legitimate use — server-side filtering

Each search component maps the flag into the request payload:

```tsx
topLevelOnly: hideSubWorkflows,
```

The backend interprets `topLevelOnly` as "return only executions with
`parentWorkflowId = ""`". This is the toggle's intended, correct job and is
**not** part of the fix.

### 2.3 The defect — expand affordance bound to the negation

`ResultsTable.tsx` renders the switch and the grid:

```tsx
<Switch
  checked={hideSubWorkflows}
  onChange={(e) => setHideSubWorkflows(e.target.checked)}
  size="small"
/>
// label="Hide sub-agent executions"

<DataTable
  expandableRows={!hideSubWorkflows}      // BUG
  expandOnRowClicked={!hideSubWorkflows}  // BUG
  expandableRowsComponent={/* lazily fetches + renders sub-agents */}
  ...
/>
```

With `hideSubWorkflows === true`, both expand props evaluate to `false`, and
`react-data-table-component` renders no expander column — the arrow vanishes.

### 2.4 Why the negation is wrong

The two behaviors the toggle is caught between:

| `hideSubWorkflows` | Flat list contents | Should the expand arrow exist? |
|---|---|---|
| `true` (on) | Top-level agents only (`topLevelOnly`) | **Yes** — the only way to see a parent's sub-agents inline. |
| `false` (off) | All executions; sub-agents shown as their own rows tagged `_isSubAgent` | No — inline expansion would list the same sub-agents a second time. |

The desired arrow-visibility column is identical to `hideSubWorkflows`, so the
correct binding is `hideSubWorkflows`, not `!hideSubWorkflows`. The original code
inverted it.

## 3. Fix

In `ResultsTable.tsx`, remove the negation on both DataTable props:

```tsx
expandableRows={hideSubWorkflows}
expandOnRowClicked={hideSubWorkflows}
```

`expandableRowsComponent` and its lazy `fetchSubAgents` / `subAgentCache` logic
already work correctly and are unchanged.

## 4. Decision record

- **Chosen:** bind expand props to `hideSubWorkflows` (remove `!`). Fixes the
  reported bug and preserves the no-double-listing behavior when the toggle is
  off. Smallest possible change; satisfies the invariant in
  `architecture.md` §3.3.
- **Rejected — always `expandableRows={true}`:** would also fix the reported
  case but reintroduces duplicate sub-agent listings when the toggle is off
  (once as a top-level `_isSubAgent` row, once nested under its parent).
- **Rejected — add a third independent control:** out of scope; the issue asks
  for a minimal fix, and no new UX affordance is warranted.

## 5. Blast radius

- One production file changed (`ResultsTable.tsx`), two one-token expressions.
- `topLevelOnly` / server query behavior: unaffected.
- Switch label, defaults, and `filteredResults` tagging: unaffected.
- No API, DAO, or persistence changes (this is a UI-only fix).
