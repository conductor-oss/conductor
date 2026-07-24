# Behavior — Expander vs. "Hide sub-agent executions" toggle (#1394)

Companion to `subagent-expander-toggle-architecture.md`. Reuses its names and
invariants (INV-1…INV-4) verbatim. Scope: the Agent Executions table in
`ui-next/src/pages/agent/executions/ResultsTable.tsx`.

## 1. The two independent controls

| Control | State/prop | What it does |
|---|---|---|
| "Hide sub-agent executions" `Switch` | `hideSubWorkflows` | Filters whether sub-agents appear as their **own top-level rows**. Server-side `topLevelOnly` + `_isSubAgent` marking. |
| Per-row expander arrow | `expandableRows` / `expandOnRowClicked` | Expands a **single row** to reveal that agent's nested sub-agents inline via `expandableRowsComponent`. |

These answer different questions ("which rows are listed?" vs. "show this row's
children?") and must operate independently.

## 2. Behavior matrix

| `hideSubWorkflows` | Expander arrow shown? | Rows listed | Expanding a row |
|---|---|---|---|
| `false` (toggle off) | **Yes** (unchanged) | Top-level agents + sub-agents (marked `_isSubAgent`) | Shows nested sub-agents |
| `true` (toggle on) | **Yes** (fixed by #1394) | Top-level agents only | Shows nested sub-agents |

Before the fix, the "toggle on" row read "Expander arrow shown? → No" — the bug.

## 3. Interaction sequence (expand while toggle on)

1. Operator turns "Hide sub-agent executions" on → list shows top-level agents only.
2. Operator clicks a row's expander arrow (still present per INV-1/INV-2).
3. `expandableRowsComponent` mounts for that row:
   - If `subAgentCache[workflowId]` is empty → call `fetchSubAgents(workflowId)`,
     render "Loading sub-agents…".
   - On resolve → render the nested sub-agent rows, or "No sub-agents" when none.
4. Toggling the switch back and forth never removes the arrow (INV-2).

## 4. Why not gate the expander at all

Gating expandability on `hideSubWorkflows` conflates list composition with
row drill-in. An operator who hides sub-agent *rows* to declutter the list still
legitimately wants to drill into any one agent to inspect its children. Hence the
expander is unconditional (`expandableRows`, `expandOnRowClicked` set to `true`).

## 5. Accessibility note

The expander control rendered by `react-data-table-component` exposes
`aria-label="Expand Row"` (and `"Collapse Row"` when open). Tests key off these
labels (see `subagent-expander-toggle-testing.md`) so they remain robust to
icon/styling changes.
