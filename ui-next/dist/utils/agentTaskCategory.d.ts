/**
 * Shared utility to classify agent tasks by their role in the workflow.
 * Used by the Agent Execution debugger's detail panel to group an agent
 * definition's `tools` list into tool / agent / guardrail / http / mcp / rag.
 */
export type AgentTaskCategory = "tool" | "agent_tool" | "guardrail" | "http" | "mcp" | "rag" | "handoff" | "system" | "passthrough" | "unknown";
/**
 * Categorise a single tool entry by its toolType field.
 */
export declare function toolCategory(toolType: string | undefined): AgentTaskCategory;
/**
 * Map AgentTaskCategory back to the narrower ToolCategory used by
 * AgentDetailPanel (which only cares about tool-level classification).
 */
export type ToolCategory = "agent" | "tool" | "guardrail" | "http" | "mcp" | "rag";
export declare function toolCategoryForPanel(t: Record<string, unknown>): ToolCategory;
