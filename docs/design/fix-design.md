# Fix design — #1394

Reuses names, types, and paths from [architecture.md](./architecture.md).
Read that first.

## The change (single file)

File: `ui-next/src/pages/agent/executions/ResultsTable.tsx`.

Locate the `DataTable` element and replace the two toggle-coupled expander
props:

```tsx
// before
expandableRows={!hideSubWorkflows}
expandOnRowClicked={!hideSubWorkflows}
```

```tsx
// after
expandableRows={true}
expandOnRowClicked={false}
```

Nothing else in the file changes. In particular:

- The `hideSubWorkflows` / `setHideSubWorkflows` props and the
  `FormControlLabel` + `Switch` toggle are untouched (see architecture §6.1–6.2).
- `expandableRowsComponent`, `fetchSubAgents`, and `subAgentCache` are untouched.
- The server-side `topLevelOnly` filtering the toggle drives (owned by the
  parent page) is untouched.

## Behaviour

### Before

| Toggle `hideSubWorkflows` | `expandableRows` | Arrow present? | Can view nested sub-agents? |
|---|---|---|---|
| off (`false`) | `true` | yes | yes, via arrow |
| on (`true`) | `false` | **no** | **no** — the bug |

### After

| Toggle `hideSubWorkflows` | `expandableRows` | Arrow present? | Can view nested sub-agents? |
|---|---|---|---|
| off (`false`) | `true` | yes | yes, via arrow |
| on (`true`) | `true` | yes | yes, via arrow |

The toggle now controls only whether sub-agent executions appear as their own
rows in the flat list (its label's literal meaning). The expander arrow is
always available, so a user who hides sub-agents from the list can still expand
any top-level agent to inspect its nested sub-agents.

## Why `expandOnRowClicked` becomes `false`

Previously `expandOnRowClicked` tracked `!hideSubWorkflows`, so in the toggle-off
state a click anywhere on the row expanded it. Now that `expandableRows` is a
constant `true`, leaving `expandOnRowClicked` coupled to the toggle would make
row-click behaviour differ between the two states for no functional reason, and
whole-row expansion competes with:

- the `selectableRows` checkbox column, and
- the `NavLink` cells (Execution Id, Agent Name) that navigate on click.

Setting it to the constant `false` makes expansion happen only through the
dedicated arrow, giving identical, predictable row-click behaviour in both
toggle states. This matches the issue, which is strictly about the *arrow*
being present.

## Alternatives considered

1. **`expandableRows={hideSubWorkflows}`** (invert the coupling) — would show
   the arrow only when the toggle is on. Rejected: it would remove the arrow in
   the currently-working toggle-off state, trading one regression for another.
2. **Custom `expandableRowDisabled` predicate** to hide the arrow on
   `_isSubAgent` rows — unnecessary scope. Sub-agent rows only exist in the
   toggle-off list; a redundant expander on them is harmless and expanding one
   simply shows "No sub-agents". Keeping the change to two constant props is the
   minimal fix the issue asks for.

## Risk

Low. Single-file, two-line change confined to presentation props of an existing
component. No API, data-model, or state-machine change. The expander mechanism
(`expandableRowsComponent` + `fetchSubAgents`) already exists and is exercised
today in the toggle-off path.
