# Fix Plan — #1394

Reuses names and the exact edit from [`architecture.md`](./architecture.md). Read
that first; this document is the step-by-step implementation.

## Scope

One file: `ui-next/src/pages/agent/executions/ResultsTable.tsx`.

Two lines changed. Goal: satisfy **Invariant EXP-1** — the Agent Executions
`DataTable` is always expandable, independent of `hideSubWorkflows`.

## Step 1 — Decouple the expander from the toggle

Locate the `<DataTable ...>` element (currently around line 340). The first two
props are:

```tsx
expandableRows={!hideSubWorkflows}
expandOnRowClicked={!hideSubWorkflows}
```

Replace them with the constant-`true` form:

```tsx
expandableRows
expandOnRowClicked
```

Leave every other prop on the `DataTable` untouched — in particular
`expandableRowsComponent`, `data={filteredResults}`, `columns`, `selectableRows`,
`conditionalRowStyles`, and pagination props.

## Step 2 — Confirm nothing else references the removed binding

- `hideSubWorkflows` must still be used by:
  - the `Switch` (`checked={hideSubWorkflows}`),
  - `filteredResults` (adds `_isSubAgent` only when the toggle is off).
- Do **not** remove `hideSubWorkflows` / `setHideSubWorkflows` from
  `ResultsTableProps` — they remain in use.

No other file reads these two `DataTable` props, so the change is self-contained.

## Step 3 — Rationale recorded in-place

The `!hideSubWorkflows` binding conflated two orthogonal concerns (see
`architecture.md` §1.2). The corrected code makes expansion always available; the
existing `expandableRowsComponent` already fetches a parent's sub-agents on demand
via `fetchSubAgents`, so the arrow is meaningful whether or not sub-agents are also
shown as flat rows.

## Step 4 — Format and verify

Follow repository conventions for `ui-next`:

- Run the project's formatter/linter for the UI package (e.g. `prettier` / `eslint`
  as configured in `ui-next`) so the two-line change matches surrounding style
  (JSX boolean shorthand is the prevailing idiom for `true` props).
- Do not run Java `spotlessApply` — no Java files change.

## Expected diff (illustrative)

```diff
       <DataTable
-        expandableRows={!hideSubWorkflows}
-        expandOnRowClicked={!hideSubWorkflows}
+        expandableRows
+        expandOnRowClicked
         expandableRowsComponent={({ data: row }: { data: any }) => {
```

## Risk assessment

- **Low.** The expander column was already the default-rendered state whenever the
  toggle was off; this change simply keeps it rendered when the toggle is on.
- No data model, network, or prop-shape changes; `expandableRowsComponent` is
  unchanged and already handles the "top-level only" dataset.
- Rollback is the inverse two-line edit.
