/**
 * Pure helpers that turn an agentDef into reaflow nodes/edges.
 * Kept out of AgentDefinitionView.tsx so Fast Refresh can treat that file as
 * components-only.
 */
import { NodeData, EdgeData } from "reaflow";

const W = 264;
const H = 90; // slightly taller to fit strategy row
const H_GATE = 80; // gate/decision nodes
const MAX_INDIVIDUAL = 8; // show individual nodes up to this count, group beyond

export interface DefNodeData {
  kind: "agent" | "subagent" | "tool" | "guardrail" | "group" | "gate";
  label: string;
  sublabel?: string; // model or instructions snippet
  badge: string; // type label: AGENT / TOOL / GUARDRAIL / HTTP / MCP / RAG / AGENTS / TOOLS
  badgeColor: string;
  badgeBg: string;
  borderColor: string;
  modelName?: string;
  strategy?: string; // routing strategy (raw lowercase)
  maxTurns?: number;
  subAgentCount?: number; // number of nested sub-agents this agent orchestrates
  count?: number; // for group nodes
  items?: string[];
  // Gate-specific
  gateType?: string; // e.g. "text_contains"
  gateText?: string; // the condition value
}

function getItemName(t: unknown, fallback = "[item]"): string {
  if (typeof t === "string") return t;
  if (t && typeof t === "object") {
    const o = t as Record<string, unknown>;
    const n = o.name ?? o._worker_ref ?? (o.function as any)?.name;
    if (typeof n === "string" && n) return n;
  }
  return fallback;
}

function getItemDescription(t: unknown): string | undefined {
  if (!t || typeof t !== "object") return undefined;
  const o = t as Record<string, unknown>;
  const d = o.description ?? (o.function as any)?.description;
  if (typeof d === "string" && d) return d;
  return undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function truncateText(text: string, maxLength: number): string {
  return text.length > maxLength ? `${text.slice(0, maxLength)}…` : text;
}

/**
 * Produces a safe diagram label for either inline instructions or a prompt-template reference.
 * Agent definitions accept both shapes, so only strings are sliced directly.
 */
function instructionSnippet(
  instructions: unknown,
  maxLength = 55,
): string | undefined {
  if (typeof instructions === "string") {
    return instructions ? truncateText(instructions, maxLength) : undefined;
  }

  if (!isRecord(instructions)) return undefined;

  const templateName = instructions.name;
  if (typeof templateName !== "string" || !templateName) return undefined;

  const version = instructions.version;
  const templateLabel = `Prompt template: ${templateName}${
    typeof version === "number" ? ` (v${version})` : ""
  }`;
  return truncateText(templateLabel, maxLength);
}

function toolCat(
  t: Record<string, unknown>,
): "agent" | "tool" | "guardrail" | "http" | "mcp" | "rag" {
  const tt = (t.toolType as string | undefined)?.toLowerCase() ?? "";
  if (tt === "agent_tool" || tt === "agent") return "agent";
  if (tt === "guardrail") return "guardrail";
  if (tt === "http") return "http";
  if (tt === "mcp") return "mcp";
  if (tt === "rag") return "rag";
  return "tool";
}

/** Flatten agents[] + agent-typed tools into a uniform list for diagram nodes. */
function collectDirectSubAgents(
  agentDef: Record<string, unknown>,
): Array<Record<string, unknown>> {
  const agentsList =
    (agentDef.agents as Array<Record<string, unknown>> | undefined) ?? [];
  const allTools =
    (agentDef.tools as Array<Record<string, unknown>> | undefined) ?? [];
  const agentToolList = allTools.filter((t) => toolCat(t) === "agent");

  return [
    ...agentsList,
    ...agentToolList.map((t) => {
      const agentConfig =
        isRecord(t.config) && isRecord(t.config.agentConfig)
          ? t.config.agentConfig
          : undefined;
      return {
        name: getItemName(t),
        model: (agentConfig?.model ?? t.model) as string | undefined,
        instructions: agentConfig?.instructions,
        strategy: t.strategy as string | undefined,
        // Prefer nested agents declared on the tool's agentConfig when present.
        agents:
          (agentConfig?.agents as Array<Record<string, unknown>> | undefined) ??
          [],
      };
    }),
  ];
}

/**
 * Recursively add sub-agent nodes under `parentId`.
 *
 * Edges always follow the definition tree (parent → each direct child). Strategy
 * badges describe how a coordinator runs its children; we do not chain sequential
 * siblings through each other — that made the next step look like a child of a
 * nested PARALLEL/ROUTER in the layout.
 */
function addSubAgentTree(
  nodes: NodeData<DefNodeData>[],
  edges: EdgeData[],
  parentId: string,
  parentStrategy: string | undefined,
  children: Array<Record<string, unknown>>,
  idPrefix: string,
): void {
  if (children.length === 0) return;

  if (children.length > MAX_INDIVIDUAL) {
    const names = children.map((a) => getItemName(a));
    const groupId = `${idPrefix}-group`;
    nodes.push({
      id: groupId,
      width: W,
      height: H,
      data: {
        kind: "group",
        label: names.slice(0, 2).join(", ") + ", …",
        count: children.length,
        badge: "AGENTS",
        badgeColor: "#3d5fc0",
        badgeBg: "#e8eeff",
        borderColor: "#93c5fd",
        items: names,
      },
    });
    edges.push({
      id: `${parentId}→${groupId}`,
      from: parentId,
      to: groupId,
    });
    return;
  }

  const isSequential = parentStrategy?.toLowerCase() === "sequential";

  for (let i = 0; i < children.length; i++) {
    const sa = children[i];
    const id = `${idPrefix}-${i}`;
    const nested =
      (sa.agents as Array<Record<string, unknown>> | undefined) ?? [];
    const subAgentCount = nested.length;
    const instSub = instructionSnippet(sa.instructions);
    const childStrategy = sa.strategy as string | undefined;
    const childMaxTurns = sa.maxTurns as number | undefined;
    const childModel = sa.model as string | undefined;
    const childGate = sa.gate as Record<string, unknown> | undefined;

    nodes.push({
      id,
      width: W,
      height: H,
      data: {
        kind: "subagent",
        label: getItemName(sa),
        sublabel: instSub ?? childModel,
        badge: "AGENT",
        badgeColor: "#3d5fc0",
        badgeBg: "#e8eeff",
        borderColor: "#93c5fd",
        modelName: childModel,
        // Suppress strategy/maxTurns for leaf sub-agents — the server may echo
        // SDK defaults (HANDOFF/25) even when the agent has no sub-agents.
        strategy: subAgentCount > 0 ? childStrategy : undefined,
        maxTurns: subAgentCount > 0 ? childMaxTurns : undefined,
        subAgentCount: subAgentCount || undefined,
      },
    });

    edges.push({ id: `${parentId}→${id}`, from: parentId, to: id });

    // Sequential gates hang off the step (not between siblings) so a later
    // sequential sibling stays a child of the parent, not of this subtree.
    if (isSequential && childGate) {
      const gateId = `${idPrefix}-gate-${i}`;
      nodes.push({
        id: gateId,
        width: W,
        height: H_GATE,
        data: {
          kind: "gate",
          label: "Gate",
          badge: "GATE",
          badgeColor: "#b45309",
          badgeBg: "#fef3c7",
          borderColor: "#f59e0b",
          gateType: childGate.type as string | undefined,
          gateText: childGate.text as string | undefined,
        },
      });
      edges.push({ id: `${id}→${gateId}`, from: id, to: gateId });
    }

    if (nested.length > 0) {
      addSubAgentTree(nodes, edges, id, childStrategy, nested, id);
    }
  }
}

/** Builds reaflow nodes/edges from an agentDef. */
export function buildDefDiagram(agentDef: Record<string, unknown>) {
  const nodes: NodeData<DefNodeData>[] = [];
  const edges: EdgeData[] = [];

  const defModel = agentDef.model as string | undefined;
  const agentName = (agentDef.name as string | undefined) ?? "Agent";
  const strategy = agentDef.strategy as string | undefined;
  const maxTurns = agentDef.maxTurns as number | undefined;
  const instructions = agentDef.instructions ?? agentDef.description;

  const allTools =
    (agentDef.tools as Array<Record<string, unknown>> | undefined) ?? [];
  const regularTools = allTools.filter((t) => toolCat(t) === "tool");
  const httpTools = allTools.filter((t) => toolCat(t) === "http");
  const mcpTools = allTools.filter((t) => toolCat(t) === "mcp");
  const ragTools = allTools.filter((t) => toolCat(t) === "rag");
  const guardrailTools = allTools.filter((t) => toolCat(t) === "guardrail");
  const guardrailsDef =
    (agentDef.guardrails as Array<unknown> | undefined) ?? [];
  const allGuardrails = [
    ...guardrailTools.map((g) => getItemName(g)),
    ...(guardrailsDef as unknown[]).map((g) => getItemName(g)),
  ];

  const allSubAgents = collectDirectSubAgents(agentDef);
  const instSnippet = instructionSnippet(instructions);

  // ── Root agent node ──────────────────────────────────────────────────────────
  nodes.push({
    id: "agent",
    width: W,
    height: H,
    data: {
      kind: "agent",
      label: agentName,
      sublabel: defModel ?? instSnippet,
      badge: "AGENT",
      badgeColor: "#3d5fc0",
      badgeBg: "#e8eeff",
      borderColor: "#93c5fd",
      modelName: defModel,
      // Only show strategy/maxTurns when the root actually coordinates sub-agents;
      // a lone agent carries SDK defaults (e.g. HANDOFF/25) that are meaningless here.
      strategy: allSubAgents.length > 0 ? strategy : undefined,
      maxTurns: allSubAgents.length > 0 ? maxTurns : undefined,
    },
  });

  // Helper: add a node branching directly from root
  const addFromRoot = (id: string, data: DefNodeData) => {
    nodes.push({ id, width: W, height: H, data });
    edges.push({ id: `agent→${id}`, from: "agent", to: id });
  };

  // ── Sub-agents (recursive containment tree) ──────────────────────────────
  addSubAgentTree(nodes, edges, "agent", strategy, allSubAgents, "subagent");

  // Helper: add tool nodes from root, passing descriptions when available
  const addToolCategory = (
    tools: Array<Record<string, unknown> | string>,
    id: string,
    badge: string,
    badgeColor: string,
    badgeBg: string,
    borderColor: string,
  ) => {
    if (tools.length === 0) return;
    if (tools.length <= MAX_INDIVIDUAL) {
      tools.forEach((t, i) => {
        const desc = getItemDescription(t);
        const descSnippet = desc
          ? desc.slice(0, 60) + (desc.length > 60 ? "…" : "")
          : undefined;
        addFromRoot(`${id}-${i}`, {
          kind: "tool",
          label: getItemName(t),
          sublabel: descSnippet,
          badge,
          badgeColor,
          badgeBg,
          borderColor,
        });
      });
    } else {
      const names = tools.map((t) => getItemName(t));
      addFromRoot(id, {
        kind: "group",
        label: names.slice(0, 2).join(", ") + (names.length > 2 ? ", …" : ""),
        count: tools.length,
        badge,
        badgeColor,
        badgeBg,
        borderColor,
        items: names,
      });
    }
  };

  // ── Tools / HTTP / MCP / RAG ─────────────────────────────────────────────
  addToolCategory(
    regularTools,
    "tools",
    "@TOOL",
    "#0369a1",
    "#e0f2fe",
    "#7dd3fc",
  );
  addToolCategory(httpTools, "http", "HTTP", "#6b7280", "#f3f4f6", "#d1d5db");
  addToolCategory(mcpTools, "mcp", "MCP", "#7c3aed", "#ede9fe", "#c4b5fd");
  addToolCategory(ragTools, "rag", "RAG", "#0f766e", "#ccfbf1", "#99f6e4");
  addToolCategory(
    allGuardrails,
    "guardrails",
    "GUARDRAILS",
    "#b45309",
    "#fef3c7",
    "#fde68a",
  );

  return { nodes, edges };
}
