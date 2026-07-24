# Testing — #1394

Reuses names and **Invariant EXP-1** from [`architecture.md`](./architecture.md).

The acceptance criterion is simple: the expander arrow on the Agent Executions
results grid is present in **both** toggle states.

## 1. Manual verification (reproduces the issue report)

Pre-req: an agent execution that spawned sub-agents.

1. Open **Agents → Executions** and run a search that returns a parent agent with
   sub-agents.
2. Toggle **`Hide sub-agent executions`** **OFF**.
   - **Expect:** each row shows the expand arrow; clicking a row (or the arrow)
     reveals the nested sub-agent `Table`, or `Loading sub-agents...` then the
     fetched rows / `No sub-agents`.
3. Toggle **`Hide sub-agent executions`** **ON**.
   - **Before fix:** the expand arrow is gone (bug).
   - **After fix:** the expand arrow is **still present**; expanding a parent row
     still reveals its sub-agents inline. Sub-agent executions no longer appear as
     their own top-level rows (unchanged behavior).

## 2. Regression test (optional, recommended)

File: `ui-next/src/pages/agent/executions/__tests__/ResultsTable.test.tsx`
(matches the module path in `architecture.md` §3).

Render `ResultsTable` twice with a `resultObj` containing at least one parent row,
using the existing `ResultsTableProps` contract. The distinguishing assertion is
the presence of the `react-data-table-component` expander button.

Test cases:

1. **Arrow present when toggle OFF** — render with `hideSubWorkflows={false}`;
   assert an expander control exists for a data row (e.g. an element with
   `aria-label="Expand Row"` / the RDT expander cell).
2. **Arrow present when toggle ON** — render with `hideSubWorkflows={true}`;
   assert the expander control **still** exists. This is the case that fails
   before the fix and passes after.
3. **Toggle still filters** — assert the `Switch` reflects `hideSubWorkflows` and
   that flipping it calls `setHideSubWorkflows` with the negated value. This guards
   against accidentally removing the toggle's real responsibility.

Notes for the test author:

- Mock `agentFetch` so `/api/agent/list` and `/api/workflow/{id}` resolve
  deterministically; the arrow's presence does not depend on those responses.
- Assert on the expander affordance itself, **not** on the fetched sub-agent
  content — the fix is about the arrow rendering, and `expandableRowsComponent`
  behavior is unchanged.

## 3. What must NOT regress

- Sub-agent rows still hide/show as top-level rows per the toggle (`filteredResults`
  / server-side `topLevelOnly`).
- `_isSubAgent` styling, the `↳ … sub-agent` label renderer, bulk actions, sorting,
  and pagination are unaffected.
