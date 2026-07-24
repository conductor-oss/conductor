# Architecture — Fix #1394: Sub-agent dropdown disappears when the view toggle is on

> **Single source of truth.** Every supporting design doc in this directory
> (`root-cause-and-fix.md`, `testing.md`) reuses the names, types, file paths, and
> conventions defined here **verbatim**. If something is ambiguous, this file wins.

## 1. Overview

Issue #1394 reports that in the Agent Execution debugger, the "dropdown" arrow used
to reveal a running agent's **nested sub-agents** is present in one state of the
view toggle and **disappears** in the other. The reporter's evidence:

- Toggle **off** → the expand/drill arrow is shown next to each sub-agent row.
- Toggle **on** → the arrow is gone.

This is a **UI-only bug** in the `ui-next` React application. There is **no
server, DAO, persistence, REST, or SDK change**. The fix is confined to the Agent
Execution feature module and its tests. Do **not** touch Java modules, Gradle
files, or the legacy `ui/` application.

### The toggle in question

The toggle is the **view-mode toggle** rendered in `AgentRunView.tsx` — the pair of
icon buttons in the top bar that switches between:

| Icon (`@phosphor-icons/react`) | `ViewMode` value | Renders |
| --- | --- | --- |
| `ListBullets` | `"timeline"` | `TurnDetail` → `SubAgentTree` (a row list **with** the nested-expansion caret + drill-in arrow) |
| `Graph` | `"diagram"` | `AgentExecutionDiagram` (a graph; sub-agents are flat nodes) |

The nested-sub-agent **dropdown/expand affordance** — the `CaretRight` / `CaretDown`
"chevron" plus the per-row drill-in `ArrowRight` — lives **only** in
`SubAgentTree.tsx`, which is reachable **only** through the `"timeline"` branch.
When the user switches the toggle to `"diagram"`, `SubAgentTree` is unmounted and
the affordance vanishes. That is the disappearance the issue describes.

The fix makes the nested-sub-agent expansion affordance **available in both view
modes**, so toggling the view no longer removes the ability to reveal nested
sub-agents. See `root-cause-and-fix.md` for the exact, minimal edit.

## 2. Tech stack

| Concern | Choice |
| --- | --- |
| App | `ui-next` (React + TypeScript) |
| UI kit | MUI (`@mui/material`) — `Box`, `Collapse`, `IconButton`, `Typography`, `Tooltip` |
| Icons | `@phosphor-icons/react` — `CaretRight`, `CaretDown`, `ArrowRight`, `ListBullets`, `Graph` |
| Diagram | `reaflow` (existing; not modified by this fix) |
| Tests | Vitest + React Testing Library (existing `ui-next` test setup) |
| Formatting | Prettier / project lint (run the repo's `ui-next` lint before finishing) |

## 3. Module / file layout

All paths are relative to `ui-next/src/pages/execution/AgentExecution/`. **No new
runtime files are created.** The bug is fixed by editing existing files; one test
file is added.

| File | Role | Changed by this fix? |
| --- | --- | --- |
| `AgentRunView.tsx` | Owns `viewMode` state (`ViewMode`), renders the view-mode toggle, and switches between the diagram and timeline renderers. **Primary fix site.** | **Yes** |
| `SubAgentTree.tsx` | Renders the sub-agent row list. Owns the nested-expansion caret (`CaretRight`/`CaretDown`) and the per-row drill-in `ArrowRight`. **Owner of the "dropdown".** | Yes, only if the caret gating is corrected (see fix doc) |
| `TurnDetail.tsx` | Renders one turn's events and, when present, its `SubAgentTree`. Consumed by the timeline branch. | No (reference only) |
| `AgentExecutionDiagram.tsx` | Diagram renderer; draws sub-agents as nodes with a `View execution` drill-in arrow. Reference for parity only. | No |
| `AgentExecutionTab.tsx` | Top-level tab; builds `rootRun` and passes `onDrillIn` down. | No |
| `types.ts` | Shared domain types (`AgentRunData`, `AgentTurn`, `AgentStatus`, `AgentStrategy`). | No |
| `agentExecutionUtils.ts` | Helpers (`timelineItemId`, `formatDuration`, `formatTokens`, `agentValuePreview`). | No |
| `SubAgentTree.test.tsx` | **New** unit test asserting the nested-expansion affordance renders and works. | **New file** |

### Component/data flow (unchanged by the fix)

```
AgentExecutionTab
  └─ AgentRunView                (holds viewMode + selectedTurn state)
       ├─ TurnBar                (turn selector)
       ├─ view toggle            (ListBullets = "timeline" | Graph = "diagram")
       ├─ viewMode === "diagram"  → AgentExecutionDiagram   ── onDrillIn ─┐
       └─ viewMode === "timeline" → TurnDetail → SubAgentTree ── onDrillIn ┤
                                                                          │
                                          onDrillIn(sub: AgentRunData) ───┘
```

`onDrillIn` is threaded unchanged from `AgentExecutionTab` down to both branches.

## 4. Shared contracts (reuse these names verbatim)

These are the **existing** types and identifiers the fix and its tests must reuse
exactly. Do not rename, re-export, or duplicate them.

### 4.1 `ViewMode` (defined in `AgentRunView.tsx`)

```ts
type ViewMode = "diagram" | "timeline";
```

- State: `const [viewMode, setViewMode] = useState<ViewMode>("diagram");`
- `"diagram"` is the **default** (initial) value — do not change the default.

### 4.2 Domain types (defined in `types.ts`)

```ts
interface AgentRunData {
  id: string;
  agentName: string;
  turns: AgentTurn[];
  status: AgentStatus;
  totalTokens: { promptTokens: number; completionTokens: number; totalTokens: number };
  totalDurationMs: number;
  model?: string;
  output?: unknown;
  failureReason?: string;
  strategy?: AgentStrategy;
  subWorkflowId?: string;
  // …other existing fields
}

interface AgentTurn {
  status: AgentStatus;
  events: AgentEvent[];
  subAgents: AgentRunData[];
  strategy?: AgentStrategy;
  durationMs: number;
  tokens: { promptTokens: number; completionTokens: number; totalTokens: number };
}

enum AgentStatus { COMPLETED, FAILED, RUNNING, WAITING }
enum AgentStrategy { HANDOFF, PARALLEL, SEQUENTIAL, ROUTER, SINGLE }
```

### 4.3 Component props (defined in their respective files — unchanged)

```ts
// SubAgentTree.tsx
interface SubAgentTreeProps {
  subAgents: AgentRunData[];
  strategy?: AgentStrategy;
  onDrillIn: (agentRun: AgentRunData) => void;
  depth?: number;
}

// TurnDetail.tsx
interface TurnDetailProps {
  turn: AgentTurn;
  onDrillIn: (agentRun: AgentRunData) => void;
}
```

### 4.4 The "dropdown" affordance (inside `SubAgentTree.tsx` → `SubAgentNode`)

The affordance the issue calls the "dropdown" is composed of two controls in the
sub-agent row, in this order:

1. **Nested-expansion caret** — an `IconButton` rendered **only when
   `hasChildren`**, toggling local `childrenExpanded` state and swapping
   `CaretDown` (expanded) / `CaretRight` (collapsed). Expanding renders a nested
   `<SubAgentTree depth={depth + 1}>` inside a `<Collapse>`.
2. **Drill-in arrow** — an `IconButton` with `ArrowRight`, always rendered, calls
   `onDrillIn(agent)`.

`hasChildren` is defined verbatim as:

```ts
const allSubAgents = agent.turns.flatMap((t) => t.subAgents);
const hasChildren = allSubAgents.length > 0;
```

## 5. Naming & style conventions

- **View-mode values** are the string literals `"diagram"` and `"timeline"` — no
  enums, no booleans. Compare with `viewMode === "timeline"`.
- **Icons** come from `@phosphor-icons/react`; reuse the imports already present in
  each file (`CaretRight`, `CaretDown`, `ArrowRight`).
- **Layout** uses MUI `Box` with the `sx` prop; follow the existing spacing scale
  (`px`, `py`, `gap`) already used in `SubAgentTree.tsx`.
- **State** is component-local `useState`; do not introduce global state, context,
  or new props for this fix beyond what `root-cause-and-fix.md` specifies.
- **No behavior change to `onDrillIn`** — it must keep pointing at the same
  `(agentRun: AgentRunData) => void` callback in every branch.
- Keep the change **minimal and focused** (AGENTS.md): fix the disappearing
  affordance and nothing else. No refactors, no restyling, no diagram redesign.

## 6. Out of scope

- Server, DAO, REST controllers, SDKs, persistence, and Gradle — untouched.
- The legacy `ui/` app — untouched.
- Redesigning the diagram, changing the default view mode, or altering `onDrillIn`
  navigation semantics.
