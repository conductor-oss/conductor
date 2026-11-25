/**
 * Shared utility to classify agent tasks by their role in the workflow.
 * Used by the Agent Execution debugger's detail panel to group an agent
 * definition's `tools` list into tool / agent / guardrail / http / mcp / rag.
 */

export type AgentTaskCategory =
  | "tool" // User-defined @tool
  | "agent_tool" // Sub-agent as tool
  | "guardrail" // Guardrail task
  | "http" // HTTP tool
  | "mcp" // MCP integration
  | "rag" // RAG tool
  | "handoff" // Handoff check / transfer
  | "system" // Internal system task (termination, check_transfer, etc.)
  | "passthrough" // Framework passthrough
  | "unknown";

/**
 * Categorise a single tool entry by its toolType field.
 */
export function toolCategory(toolType: string | undefined): AgentTaskCategory {
  const tt = (toolType ?? "").toLowerCase();
  if (tt === "agent_tool" || tt === "agent") return "agent_tool";
  if (tt === "guardrail") return "guardrail";
  if (tt === "http") return "http";
  if (tt === "mcp") return "mcp";
  if (tt === "rag") return "rag";
  return "tool"; // worker, tool, simple, or unknown
}

/**
 * Map AgentTaskCategory back to the narrower ToolCategory used by
 * AgentDetailPanel (which only cares about tool-level classification).
 */
export type ToolCategory =
  | "agent"
  | "tool"
  | "guardrail"
  | "http"
  | "mcp"
  | "rag";
export function toolCategoryForPanel(t: Record<string, unknown>): ToolCategory {
  const cat = toolCategory(t.toolType as string | undefined);
  if (cat === "agent_tool") return "agent";
  if (cat === "guardrail" || cat === "http" || cat === "mcp" || cat === "rag")
    return cat;
  return "tool";
}
