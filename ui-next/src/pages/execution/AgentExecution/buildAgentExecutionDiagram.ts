/**
 * Pure layout helpers that turn AgentRunData into reaflow nodes/edges.
 * Kept separate from AgentExecutionDiagram.tsx so Fast Refresh can treat
 * that file as components-only.
 */
import { NodeData, EdgeData, PortData, PortSide } from "reaflow";
import { TaskStatus, TaskType } from "types";
import {
  AgentEvent,
  AgentRunData,
  AgentStatus,
  AgentStrategy,
  AgentTurn,
  EventType,
} from "./types";
import {
  formatTokens,
  formatDuration,
  agentValuePreview,
  timelineItemId,
  timelineItemLabel,
} from "./agentExecutionUtils";

export type Kind =
  | "start"
  | "llm"
  | "tool"
  | "handoff"
  | "subagent"
  | "output"
  | "error"
  | "next"
  | "back"
  | "group"
  | "junction"
  | "ellipsis";

export const KIND_TYPE: Record<Kind, TaskType> = {
  start: TaskType.SUB_WORKFLOW,
  subagent: TaskType.SUB_WORKFLOW,
  handoff: TaskType.SET_VARIABLE,
  llm: TaskType.LLM_CHAT_COMPLETE,
  tool: TaskType.SIMPLE,
  output: TaskType.SIMPLE,
  error: TaskType.TERMINATE,
  next: TaskType.SIMPLE,
  back: TaskType.SIMPLE,
  group: TaskType.SIMPLE,
  junction: TaskType.SIMPLE,
  ellipsis: TaskType.SIMPLE,
};

export const KIND_LABEL: Record<Kind, string> = {
  start: "AGENT",
  subagent: "AGENT",
  handoff: "HANDOFF",
  llm: "LLM CALL",
  tool: "TOOL",
  output: "OUTPUT",
  error: "ERROR",
  next: "",
  back: "",
  group: "",
  junction: "",
  ellipsis: "",
};

export function toTS(s?: AgentStatus): TaskStatus {
  if (s === AgentStatus.FAILED) return TaskStatus.FAILED;
  if (s === AgentStatus.RUNNING) return TaskStatus.IN_PROGRESS;
  if (s === AgentStatus.WAITING) return TaskStatus.SCHEDULED;
  return TaskStatus.COMPLETED;
}

export const STRATEGY_BADGE: Record<AgentStrategy, string> = {
  [AgentStrategy.HANDOFF]: "HANDOFF",
  [AgentStrategy.PARALLEL]: "PARALLEL",
  [AgentStrategy.SEQUENTIAL]: "SEQUENTIAL",
  [AgentStrategy.ROUTER]: "ROUTER",
  [AgentStrategy.SINGLE]: "AGENT",
};

export interface DiagramNodeData {
  kind: Kind;
  label: string;
  sublabel?: string;
  meta?: string;
  /** Overrides KIND_LABEL for the TypeBadge (e.g. "GUARDRAIL" on an output/error node) */
  typeLabel?: string;
  /** Strategy used to spawn this node's sub-agent(s) */
  strategy?: AgentStrategy;
  /** Model name for provider icon (LLM and agent nodes) */
  modelName?: string;
  ts: TaskStatus;
  event?: AgentEvent;
  subAgentRun?: AgentRunData;
  nextTurn?: string;
  /** For "subagent" kind: own sub-agent count/expansion state (issue #1452) */
  subAgentCount?: number;
  expanded?: boolean;
  expanding?: boolean;
  expandError?: boolean;
  /** For group nodes */
  groupType?: "agents" | "tools";
  groupAgents?: AgentRunData[];
  groupEvents?: AgentEvent[];
  groupCompleted?: number;
  groupFailed?: number;
  groupRunning?: number;
}

// ─── Build diagram nodes/edges ────────────────────────────────────────────────
const W = 264,
  H = 80,
  H_HANDOFF = 48;
// Parallel tool-call batches with fewer than this many calls are shown individually (not collapsed)
export const COLLAPSE_THRESHOLD = 10;
// Maximum individual nodes to render when a collapsed group is expanded.
// When the total exceeds this, the first EXPAND_HEAD and last EXPAND_TAIL items
// are shown with an ellipsis node in between.
export const MAX_EXPANDED = 20;
const EXPAND_HEAD = 10;
const EXPAND_TAIL = 10;

function buildTurnNodes(
  turn: AgentTurn,
  nodes: NodeData<DiagramNodeData>[],
  edges: EdgeData[],
  done: Set<string>,
  prevRef: { id: string },
  expandedGroups: Set<string>,
  idPrefix = "",
) {
  const push = (id: string, data: DiagramNodeData, h = H) => {
    nodes.push({ id, width: W, height: h, data });
    // Use join junction's south port for clean outgoing routing
    const fromPort = prevRef.id.endsWith("-join")
      ? `${prevRef.id}-south-port`
      : undefined;
    edges.push({
      id: `${prevRef.id}→${id}`,
      from: prevRef.id,
      to: id,
      ...(fromPort ? { fromPort } : {}),
    });
    if (data.ts === TaskStatus.COMPLETED) done.add(id);
    prevRef.id = id;
  };

  /**
   * Fan-out: create a fork node → N parallel branch nodes → join node.
   * Each branch node is displayed side-by-side horizontally.
   */
  const pushParallel = (
    forkId: string,
    branches: { id: string; data: DiagramNodeData; h?: number }[],
    joinId: string,
  ) => {
    if (branches.length === 0) return;
    const n = branches.length;

    // Fork junction — indexed SOUTH ports, same pattern as debug view's FORK_JOIN.
    // Width matches regular nodes so ELK keeps layers aligned.
    const forkPorts: PortData[] = branches.map((_, i) => ({
      id: `${forkId}_[key=${i}]-south-port`,
      width: 2,
      height: 2,
      side: "SOUTH" as PortSide,
      disabled: true,
      hidden: true,
      index: i,
    }));
    nodes.push({
      id: forkId,
      width: W,
      height: 16,
      data: { kind: "junction" as Kind, label: "", ts: TaskStatus.COMPLETED },
      ports: forkPorts,
    });
    edges.push({ id: `${prevRef.id}→${forkId}`, from: prevRef.id, to: forkId });
    done.add(forkId);

    // Join junction — INVERTED indexed NORTH ports (key anti-crossing trick
    // from the debug view: branch 0 → highest port index, branch N-1 → index 0).
    // Plus a standard SOUTH port for the outgoing edge.
    const joinPorts: PortData[] = [
      {
        id: `${joinId}-south-port`,
        width: 2,
        height: 2,
        side: "SOUTH" as PortSide,
        disabled: true,
        hidden: true,
      },
      ...branches.map((_, i) => {
        const inv = n - 1 - i;
        return {
          id: `${joinId}-n${inv}-north-port`,
          width: 2,
          height: 2,
          side: "NORTH" as PortSide,
          disabled: true,
          hidden: true,
          index: inv,
        };
      }),
    ];
    nodes.push({
      id: joinId,
      width: W,
      height: 16,
      data: { kind: "junction" as Kind, label: "", ts: TaskStatus.COMPLETED },
      ports: joinPorts,
    });
    done.add(joinId);

    // Branch nodes — each gets a south port for the branch→join edge.
    // Fully port-bound edges on both ends (fromPort + toPort).
    for (let i = 0; i < n; i++) {
      const b = branches[i];
      const inv = n - 1 - i;
      nodes.push({
        id: b.id,
        width: W,
        height: b.h ?? H,
        data: b.data,
        ports: [
          {
            id: `${b.id}-south-port`,
            width: 2,
            height: 2,
            side: "SOUTH" as PortSide,
            disabled: true,
            hidden: true,
          },
        ],
      });
      edges.push({
        id: `${forkId}→${b.id}`,
        from: forkId,
        fromPort: `${forkId}_[key=${i}]-south-port`,
        to: b.id,
      });
      edges.push({
        id: `${b.id}→${joinId}`,
        from: b.id,
        fromPort: `${b.id}-south-port`,
        to: joinId,
        toPort: `${joinId}-n${inv}-north-port`,
      });
      if (b.data.ts === TaskStatus.COMPLETED) done.add(b.id);
    }

    prevRef.id = joinId;
  };

  // Sequential chain turns: sub-agent FIRST, then gate event — entirely separate flow
  if (
    turn.strategy === AgentStrategy.SEQUENTIAL &&
    turn.subAgents.length === 1
  ) {
    const sub = turn.subAgents[0];
    push(`sub-${sub.id}`, {
      kind: "subagent",
      label: sub.agentName,
      meta: sub.model,
      modelName: sub.model,
      sublabel:
        agentValuePreview(sub.output, 55) ?? sub.failureReason?.slice(0, 55),
      strategy: sub.strategy,
      ts: toTS(sub.status),
      subAgentRun: sub,
      subAgentCount: sub.subAgentCount,
      expanded: sub.expanded,
      expanding: sub.expanding,
      expandError: sub.expandError,
    });
    if (sub.expanded && sub.turns.length > 0) {
      appendAgentRunTurns(
        sub,
        nodes,
        edges,
        done,
        prevRef,
        expandedGroups,
        `${idPrefix}${sub.id}-`,
      );
    }
    for (const ev of turn.events) {
      if (
        ev.type === EventType.GUARDRAIL_PASS ||
        ev.type === EventType.GUARDRAIL_FAIL
      ) {
        push(ev.id, {
          kind: ev.type === EventType.GUARDRAIL_FAIL ? "error" : "output",
          label: "Gate",
          typeLabel: "GATE",
          sublabel: ev.summary,
          ts:
            ev.type === EventType.GUARDRAIL_FAIL
              ? TaskStatus.FAILED
              : TaskStatus.COMPLETED,
          event: ev,
        });
      }
    }
    return; // Skip normal event + sub-agent processing below
  }

  // Only explicit FORK/FORK_JOIN groups render as parallel. Adjacent discovery
  // calls are sequential even when they are both TOOL_CALL events.
  type Grp =
    | AgentEvent
    | { type: "__toolGroup"; id: string; events: AgentEvent[] };
  const groups: Grp[] = [];
  let toolBatch: AgentEvent[] = [];
  let toolBatchGroup: string | undefined;
  const flushBatch = () => {
    if (toolBatch.length === 0) return;
    groups.push({
      type: "__toolGroup",
      id: toolBatchGroup ?? `tool-${toolBatch[0].id}`,
      events: [...toolBatch],
    });
    toolBatch = [];
    toolBatchGroup = undefined;
  };
  for (const ev of turn.events) {
    if (ev.type === EventType.TOOL_CALL && ev.parallelGroup) {
      if (toolBatchGroup && toolBatchGroup !== ev.parallelGroup) flushBatch();
      toolBatchGroup = ev.parallelGroup;
      toolBatch.push(ev);
    } else if (ev.type === EventType.TOOL_CALL) {
      flushBatch();
      groups.push({ type: "__toolGroup", id: `tool-${ev.id}`, events: [ev] });
    } else {
      flushBatch();
      groups.push(ev);
    }
  }
  flushBatch();

  for (const grp of groups) {
    if ("type" in grp && grp.type === "__toolGroup") {
      const batch = (grp as any).events as AgentEvent[];
      const groupId = (grp as any).id as string;
      const isExpanded = expandedGroups.has(groupId);

      if (batch.length < COLLAPSE_THRESHOLD || isExpanded) {
        // Build visible list: when expanded and over MAX_EXPANDED, show head + ellipsis + tail
        let visible: AgentEvent[];
        let ellipsisCount = 0;
        if (isExpanded && batch.length > MAX_EXPANDED) {
          const head = batch.slice(0, EXPAND_HEAD);
          ellipsisCount = batch.length - EXPAND_HEAD - EXPAND_TAIL;
          visible = [...head]; // tail is handled separately below
        } else {
          visible = batch;
        }

        const makeBranch = (ev: AgentEvent) => {
          const out = ev.result
            ? (() => {
                try {
                  return JSON.stringify(ev.result)
                    .replace(/[{}"]/g, "")
                    .slice(0, 55);
                } catch {
                  return undefined;
                }
              })()
            : undefined;
          return {
            id: ev.id,
            data: {
              kind: "tool" as Kind,
              label: ev.toolName ?? "tool",
              sublabel: out,
              meta: ev.durationMs ? formatDuration(ev.durationMs) : undefined,
              ts:
                ev.success === false
                  ? TaskStatus.FAILED
                  : ev.success === undefined
                    ? TaskStatus.IN_PROGRESS
                    : TaskStatus.COMPLETED,
              event: ev,
            },
          };
        };

        if (ellipsisCount > 0) {
          // Head + ellipsis + tail in fan-out
          const headBranches = visible.map(makeBranch);
          const ellipsisBranch = {
            id: `${groupId}-ellipsis`,
            data: {
              kind: "ellipsis" as Kind,
              label: `… ${ellipsisCount} more …`,
              ts: TaskStatus.COMPLETED,
            },
            h: 56,
          };
          const tailBranches = batch
            .slice(batch.length - EXPAND_TAIL)
            .map(makeBranch);
          pushParallel(
            `${groupId}-fork`,
            [...headBranches, ellipsisBranch, ...tailBranches],
            `${groupId}-join`,
          );
        } else if (visible.length === 1) {
          const ev = visible[0];
          const out = ev.result
            ? (() => {
                try {
                  return JSON.stringify(ev.result)
                    .replace(/[{}"]/g, "")
                    .slice(0, 55);
                } catch {
                  return undefined;
                }
              })()
            : undefined;
          push(ev.id, {
            kind: "tool",
            label: ev.toolName ?? "tool",
            sublabel: out,
            meta: ev.durationMs ? formatDuration(ev.durationMs) : undefined,
            ts:
              ev.success === false
                ? TaskStatus.FAILED
                : ev.success === undefined
                  ? TaskStatus.IN_PROGRESS
                  : TaskStatus.COMPLETED,
            event: ev,
          });
        } else {
          pushParallel(
            `${groupId}-fork`,
            visible.map(makeBranch),
            `${groupId}-join`,
          );
        }
      } else {
        const completed = batch.filter((e) => e.success === true).length;
        const failed = batch.filter((e) => e.success === false).length;
        const running = batch.filter((e) => e.success === undefined).length;
        const ts =
          failed > 0
            ? TaskStatus.FAILED
            : running > 0
              ? TaskStatus.IN_PROGRESS
              : TaskStatus.COMPLETED;
        push(groupId, {
          kind: "group",
          label: batch[0].toolName ?? "tool calls",
          groupType: "tools",
          groupEvents: batch,
          groupCompleted: completed,
          groupFailed: failed,
          groupRunning: running,
          ts,
        });
      }
    } else {
      const ev = grp as AgentEvent;
      switch (ev.type) {
        case EventType.THINKING: {
          const tok = ev.tokens;
          push(ev.id, {
            kind: "llm",
            label: "LLM",
            sublabel: ev.toolName,
            modelName: ev.toolName,
            meta: tok
              ? `${formatTokens(tok.promptTokens)}↑  ${formatTokens(tok.completionTokens)}↓`
              : undefined,
            ts:
              ev.success === false
                ? TaskStatus.FAILED
                : ev.success === undefined
                  ? TaskStatus.IN_PROGRESS
                  : TaskStatus.COMPLETED,
            event: ev,
          });
          break;
        }
        case EventType.HANDOFF: {
          const target =
            ev.targetAgent ?? ev.summary.replace(/^→\s*/, "") ?? "";
          push(
            ev.id,
            {
              kind: "handoff",
              label: target,
              ts: TaskStatus.COMPLETED,
              event: ev,
            },
            H_HANDOFF,
          );
          break;
        }
        case EventType.MESSAGE: {
          const txt = typeof ev.detail === "string" ? ev.detail : undefined;
          push(ev.id, {
            kind: "output",
            label: "response",
            sublabel: txt?.slice(0, 70) + (txt && txt.length > 70 ? "…" : ""),
            ts: TaskStatus.COMPLETED,
            event: ev,
          });
          break;
        }
        case EventType.DONE: {
          const txt = typeof ev.detail === "string" ? ev.detail : undefined;
          push(ev.id, {
            kind: "output",
            label: "output",
            sublabel: txt?.slice(0, 70) + (txt && txt.length > 70 ? "…" : ""),
            ts: TaskStatus.COMPLETED,
            event: ev,
          });
          break;
        }
        case EventType.ERROR:
          push(ev.id, {
            kind: "error",
            label: "error",
            sublabel: ev.summary,
            ts: TaskStatus.FAILED,
            event: ev,
          });
          break;
        case EventType.GUARDRAIL_PASS:
          push(ev.id, {
            kind: "output",
            label:
              ev.toolName === "gate" ? "Gate" : (ev.toolName ?? "Guardrail"),
            typeLabel: ev.toolName === "gate" ? "GATE" : "GUARDRAIL",
            sublabel: ev.toolName === "gate" ? ev.summary : "passed",
            ts: TaskStatus.COMPLETED,
            event: ev,
          });
          break;
        case EventType.GUARDRAIL_FAIL:
          push(ev.id, {
            kind: "error",
            label:
              ev.toolName === "gate" ? "Gate" : (ev.toolName ?? "Guardrail"),
            typeLabel: ev.toolName === "gate" ? "GATE" : "GUARDRAIL",
            sublabel: ev.summary,
            ts: TaskStatus.FAILED,
            event: ev,
          });
          break;
        case EventType.WAITING:
          push(ev.id, {
            kind: "output",
            label: "Waiting",
            typeLabel: "WAITING",
            sublabel: ev.summary,
            ts: TaskStatus.IN_PROGRESS,
            event: ev,
          });
          break;
        default:
          break;
      }
    }
  }

  // Sub-agents: single node if one; inline if < threshold; collapsed group if >= threshold
  if (turn.subAgents.length > 0) {
    const subGroupId = `${idPrefix}subgroup-${timelineItemId(turn)}`;
    const isSubExpanded = expandedGroups.has(subGroupId);

    if (turn.subAgents.length < COLLAPSE_THRESHOLD || isSubExpanded) {
      // NOTE: parallel siblings intentionally don't get subAgentCount/expand
      // here — expanding a branch inline would need pushParallel's fork/join
      // layout to support a multi-node chain per branch, which is out of
      // scope for this pass. Only single-sub-agent turns (below) expand
      // in place; a parallel sibling with its own children still needs
      // "View execution" (drill-in) to inspect them.
      const makeSubBranch = (sub: AgentRunData) => ({
        id: `sub-${sub.id}`,
        data: {
          kind: "subagent" as Kind,
          label: sub.agentName,
          meta: sub.model,
          modelName: sub.model,
          sublabel:
            agentValuePreview(sub.output, 55) ??
            sub.failureReason?.slice(0, 55),
          strategy: sub.strategy,
          ts: toTS(sub.status),
          subAgentRun: sub,
        },
      });

      if (isSubExpanded && turn.subAgents.length > MAX_EXPANDED) {
        // Head + ellipsis + tail
        const head = turn.subAgents.slice(0, EXPAND_HEAD).map(makeSubBranch);
        const tail = turn.subAgents
          .slice(turn.subAgents.length - EXPAND_TAIL)
          .map(makeSubBranch);
        const ellipsisCount = turn.subAgents.length - EXPAND_HEAD - EXPAND_TAIL;
        const ellipsisBranch = {
          id: `${subGroupId}-ellipsis`,
          data: {
            kind: "ellipsis" as Kind,
            label: `… ${ellipsisCount} more …`,
            ts: TaskStatus.COMPLETED,
          },
          h: 56,
        };
        pushParallel(
          `${subGroupId}-fork`,
          [...head, ellipsisBranch, ...tail],
          `${subGroupId}-join`,
        );
      } else if (turn.subAgents.length === 1) {
        const sub = turn.subAgents[0];
        push(`sub-${sub.id}`, {
          kind: "subagent",
          label: sub.agentName,
          meta: sub.model,
          modelName: sub.model,
          sublabel:
            agentValuePreview(sub.output, 55) ??
            sub.failureReason?.slice(0, 55),
          strategy: sub.strategy,
          ts: toTS(sub.status),
          subAgentRun: sub,
          subAgentCount: sub.subAgentCount,
          expanded: sub.expanded,
          expanding: sub.expanding,
          expandError: sub.expandError,
        });
        if (sub.expanded && sub.turns.length > 0) {
          appendAgentRunTurns(
            sub,
            nodes,
            edges,
            done,
            prevRef,
            expandedGroups,
            `${idPrefix}${sub.id}-`,
          );
        }
      } else {
        pushParallel(
          `${subGroupId}-fork`,
          turn.subAgents.map(makeSubBranch),
          `${subGroupId}-join`,
        );
      }
    } else {
      const completed = turn.subAgents.filter(
        (s) => s.status === AgentStatus.COMPLETED,
      ).length;
      const failed = turn.subAgents.filter(
        (s) => s.status === AgentStatus.FAILED,
      ).length;
      const running = turn.subAgents.length - completed - failed;
      const ts =
        failed > 0
          ? TaskStatus.FAILED
          : running > 0
            ? TaskStatus.IN_PROGRESS
            : TaskStatus.COMPLETED;
      push(subGroupId, {
        kind: "group",
        label: turn.subAgents[0].agentName,
        strategy: turn.strategy,
        groupType: "agents",
        groupAgents: turn.subAgents,
        groupCompleted: completed,
        groupFailed: failed,
        groupRunning: running,
        ts,
      });
    }
  }
}

/**
 * Lay out an agent run's own turn sequence, continuing the chain from
 * `prevRef`. Shared by the top-level diagram build and by expanding a
 * collapsed sub-agent node in place (issue #1452) — `idPrefix` keeps node
 * ids from a nested agent's own turns/groups from colliding with a sibling
 * or ancestor's turns of the same number (e.g. two different agents both
 * having a "Turn 1").
 */
function appendAgentRunTurns(
  agentRun: AgentRunData,
  nodes: NodeData<DiagramNodeData>[],
  edges: EdgeData[],
  done: Set<string>,
  prevRef: { id: string },
  expandedGroups: Set<string>,
  idPrefix: string,
) {
  const allTurns = agentRun.turns;
  for (let i = 0; i < allTurns.length; i++) {
    const turn = allTurns[i];

    // Insert orange "Turn N" separator before every turn after the first
    if (i > 0) {
      const turnId = timelineItemId(turn);
      const ntId = `${idPrefix}turn-sep-${turnId}`;
      nodes.push({
        id: ntId,
        width: 72,
        height: 72,
        data: {
          kind: "next",
          label: timelineItemLabel(turn),
          nextTurn: `${idPrefix}${turnId}`,
          ts: toTS(turn.status),
        },
      });
      const fromPort = prevRef.id.endsWith("-join")
        ? `${prevRef.id}-south-port`
        : undefined;
      edges.push({
        id: `${prevRef.id}→${ntId}`,
        from: prevRef.id,
        to: ntId,
        ...(fromPort ? { fromPort } : {}),
      });
      if (turn.status === AgentStatus.COMPLETED) done.add(ntId);
      prevRef.id = ntId;
    }

    buildTurnNodes(turn, nodes, edges, done, prevRef, expandedGroups, idPrefix);
  }
}

/** Pure layout helper — exported for unit tests (nesting + COMPLETED fixtures). */
export function buildAgentExecutionDiagram(
  agentRun: AgentRunData,
  _activeTurnId: string,
  hasBack: boolean,
  expandedGroups: Set<string>,
) {
  const nodes: NodeData<DiagramNodeData>[] = [];
  const edges: EdgeData[] = [];
  const done = new Set<string>();
  const prevRef = { id: "start" };

  // "Back to parent" node — first in the chain
  if (hasBack) {
    nodes.push({
      id: "back",
      width: 72,
      height: 72,
      data: { kind: "back", label: "", ts: TaskStatus.COMPLETED },
    });
    edges.push({ id: "back→start", from: "back", to: "start" });
    done.add("back");
  }

  nodes.push({
    id: "start",
    width: W,
    height: H,
    data: {
      kind: "start",
      label: agentRun.agentName,
      sublabel: agentValuePreview(agentRun.input, 55),
      meta: agentRun.model,
      modelName: agentRun.model,
      ts: toTS(agentRun.status),
    },
  });
  if (agentRun.status === AgentStatus.COMPLETED) done.add("start");

  appendAgentRunTurns(
    agentRun,
    nodes,
    edges,
    done,
    prevRef,
    expandedGroups,
    "",
  );

  return { nodes, edges, done };
}
